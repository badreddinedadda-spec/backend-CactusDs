package com.cactusds.backend.controller;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.dto.ForgotPasswordRequest;
import com.cactusds.backend.dto.ResetPasswordRequest;
import com.cactusds.backend.model.PasswordResetToken;
import com.cactusds.backend.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.UUID;
import com.cactusds.backend.dto.LoginRequest;
import com.cactusds.backend.dto.RegisterRequest;
import com.cactusds.backend.dto.UserResponse;
import com.cactusds.backend.model.AuthProvider;
import com.cactusds.backend.model.Role;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final NotificationService notificationService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthController(UserRepository userRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                          PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.notificationService = notificationService;
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
        return ResponseEntity.status(201).body(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest request, HttpServletResponse response) {
        Authentication authRequest = new UsernamePasswordAuthenticationToken(req.email(), req.password());
        Authentication authResult;
        try {
            authResult = authenticationManager.authenticate(authRequest);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_credentials"));
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        User user = userRepository.findByEmail(req.email()).orElseThrow();
        return ResponseEntity.ok(toResponse(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
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
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        userRepository.findByEmail(req.email()).ifPresent(user -> {
            if (user.getAuthProvider() != AuthProvider.LOCAL) return; // Google-only: nothing to reset
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token).user(user).expiresAt(LocalDateTime.now().plusHours(1)).build();
            passwordResetTokenRepository.save(resetToken);
            notificationService.notifyPasswordReset(user, frontendUrl + "/hosting/reset-password?token=" + token);
        });
        return ResponseEntity.ok(Map.of("status", "ok")); // always 200 — never reveals if the email exists
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
    @GetMapping("/csrf")
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

}
