package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.security.LoginAttemptService;
import com.cactusds.backend.comon.security.TwoFactorService;
import com.cactusds.backend.dto.ForgotPasswordRequest;
import com.cactusds.backend.dto.LoginRequest;
import com.cactusds.backend.dto.RegisterRequest;
import com.cactusds.backend.dto.ResendVerificationRequest;
import com.cactusds.backend.dto.ResetPasswordRequest;
import com.cactusds.backend.dto.Twofactorverifyrequest;
import com.cactusds.backend.dto.UserResponse;
import com.cactusds.backend.dto.VerifyEmailRequest;
import com.cactusds.backend.model.AuthProvider;
import com.cactusds.backend.model.EmailVerificationToken;
import com.cactusds.backend.model.PasswordResetToken;
import com.cactusds.backend.model.Role;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.EmailVerificationTokenRepository;
import com.cactusds.backend.repository.PasswordResetTokenRepository;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final NotificationService notificationService;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthController(UserRepository userRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                          EmailVerificationTokenRepository emailVerificationTokenRepository,
                          PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                          NotificationService notificationService, LoginAttemptService loginAttemptService,
                          TwoFactorService twoFactorService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.notificationService = notificationService;
        this.loginAttemptService = loginAttemptService;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }
        String email = extractEmail(authentication);
        return userRepository.findByEmail(email)
                .map(u -> ResponseEntity.ok(toResponse(u)))
                .orElse(ResponseEntity.status(401).build());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "email_taken"));
        }
        User user = User.builder()
                .email(req.email())
                .fullName(req.fullName())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(Role.CLIENT)
                .authProvider(AuthProvider.LOCAL)
                .build();
        userRepository.save(user);
        sendVerificationEmail(user);
        return ResponseEntity.status(201).body(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        if (loginAttemptService.isLocked(req.email())) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "account_locked"));
        }

        Authentication authRequest = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        Authentication authResult;
        try {
            authResult = authenticationManager.authenticate(authRequest);
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(req.email());
            return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
        }
        loginAttemptService.recordSuccess(req.email());

        User user = userRepository.findByEmail(req.email()).orElseThrow();
        if (twoFactorService.isEnabled(user)) {
            // Password is correct, but the session stays anonymous until the second factor is
            // verified (POST /api/auth/2fa/verify). Nothing authenticated is stored yet.
            twoFactorService.startPendingLogin(request, user);
            return ResponseEntity.ok(Map.of("twoFactorRequired", true));
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(toResponse(user));
    }

    /** Login step 2 (only for accounts with 2FA on): a 6-digit code, or one recovery code. */
    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody Twofactorverifyrequest req,
                                             HttpServletRequest request, HttpServletResponse response) {
        Long userId = twoFactorService.pendingUserId(request).orElse(null);
        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user == null || !twoFactorService.isEnabled(user)) {
            twoFactorService.clearPending(request);
            return ResponseEntity.status(401).body(Map.of("error", "no_pending_login"));
        }

        TwoFactorService.VerifyResult result =
                twoFactorService.verifyLoginChallenge(user, req.code(), req.recoveryCode());
        if (result == TwoFactorService.VerifyResult.LOCKED) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "account_locked"));
        }
        if (result != TwoFactorService.VerifyResult.OK) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_code"));
        }

        twoFactorService.clearPending(request);
        completeLogin(user, request, response);
        return ResponseEntity.ok(toResponse(user));
    }

    /**
     * Opens the authenticated session once every factor has been verified. The session id is
     * rotated at this exact moment (privilege change) to prevent session fixation.
     */
    private void completeLogin(User user, HttpServletRequest request, HttpServletResponse response) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        user.getEmail(), "", List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        userRepository.findByEmail(req.email()).ifPresent(user -> {
            if (user.getAuthProvider() != AuthProvider.LOCAL) {
                return;
            }
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();
            passwordResetTokenRepository.save(resetToken);
            String resetLink = frontendUrl + "/hosting/reset-password?token=" + token;
            notificationService.notifyPasswordReset(user, resetLink);
        });
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(req.token()).orElse(null);
        if (resetToken == null || Boolean.TRUE.equals(resetToken.getUsed()) || resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_or_expired_token"));
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest req) {
        EmailVerificationToken verifyToken = emailVerificationTokenRepository.findByToken(req.token()).orElse(null);
        if (verifyToken == null || Boolean.TRUE.equals(verifyToken.getUsed()) || verifyToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_or_expired_token"));
        }
        User user = verifyToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        verifyToken.setUsed(true);
        emailVerificationTokenRepository.save(verifyToken);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        userRepository.findByEmail(req.email())
                .filter(user -> !Boolean.TRUE.equals(user.getEmailVerified()))
                .ifPresent(this::sendVerificationEmail);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private void sendVerificationEmail(User user) {
        if (user.getAuthProvider() != AuthProvider.LOCAL || Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verifyToken = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        emailVerificationTokenRepository.save(verifyToken);
        String verifyLink = frontendUrl + "/hosting/verify-email?token=" + token;
        notificationService.notifyEmailVerification(user, verifyLink);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.getEmailVerified());
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
    @GetMapping("/csrf")
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }
}