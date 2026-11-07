package com.cactusds.backend.controller;

import com.cactusds.backend.dto.UpdateProfileRequest;
import com.cactusds.backend.dto.UserResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cactusds.backend.comon.security.PasswordChangeService;
import com.cactusds.backend.dto.ChangePasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import java.util.Map;

@RestController
@RequestMapping("/api/client")
public class AccountController {

    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final PasswordChangeService passwordChangeService;

    public AccountController(CurrentUserResolver currentUserResolver, UserRepository userRepository,
                             PasswordChangeService passwordChangeService) {
        this.currentUserResolver = currentUserResolver;
        this.userRepository = userRepository;
        this.passwordChangeService = passwordChangeService;
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest req,
                                                      Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        user.setFullName(req.fullName().trim());
        userRepository.save(user);
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getEmailVerified()));
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            Authentication authentication, HttpServletRequest request) {
        User user = currentUserResolver.resolve(authentication);
        PasswordChangeService.Result result = passwordChangeService.change(
                user, req.currentPassword(), req.newPassword(), req.code(), req.recoveryCode(), request);
        return switch (result) {
            case OK -> ResponseEntity.ok(Map.of("status", "ok"));
            case LOCKED -> ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "account_locked"));
            case NO_PASSWORD_ACCOUNT -> ResponseEntity.badRequest().body(Map.of("error", "no_password_account"));
            case WRONG_PASSWORD -> ResponseEntity.badRequest().body(Map.of("error", "invalid_current_password"));
            case SECOND_FACTOR_REQUIRED -> ResponseEntity.badRequest().body(Map.of("error", "second_factor_required"));
            case INVALID_CODE -> ResponseEntity.badRequest().body(Map.of("error", "invalid_code"));
            case SAME_PASSWORD -> ResponseEntity.badRequest().body(Map.of("error", "same_password"));
        };
    }
}