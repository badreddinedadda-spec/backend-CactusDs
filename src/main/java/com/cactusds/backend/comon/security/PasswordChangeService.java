package com.cactusds.backend.comon.security;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.AuthProvider;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {

    public enum Result {
        OK, NO_PASSWORD_ACCOUNT, LOCKED, WRONG_PASSWORD, SECOND_FACTOR_REQUIRED, INVALID_CODE, SAME_PASSWORD
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final TwoFactorService twoFactorService;
    private final UserSessionService userSessionService;
    private final NotificationService notificationService;

    public PasswordChangeService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 LoginAttemptService loginAttemptService, TwoFactorService twoFactorService,
                                 UserSessionService userSessionService, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.twoFactorService = twoFactorService;
        this.userSessionService = userSessionService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Result change(User user, String currentPassword, String newPassword,
                         String code, String recoveryCode, HttpServletRequest request) {
        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            return Result.NO_PASSWORD_ACCOUNT;
        }
        String lockKey = lockKey(user);
        if (loginAttemptService.isLocked(lockKey)) {
            return Result.LOCKED;
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            loginAttemptService.recordFailure(lockKey);
            return Result.WRONG_PASSWORD;
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            return Result.SAME_PASSWORD;
        }
        if (twoFactorService.isEnabled(user)) {
            if (isBlank(code) && isBlank(recoveryCode)) {
                return Result.SECOND_FACTOR_REQUIRED;
            }
            TwoFactorService.VerifyResult second = twoFactorService.verifyLoginChallenge(user, code, recoveryCode);
            if (second == TwoFactorService.VerifyResult.LOCKED) {
                return Result.LOCKED;
            }
            if (second != TwoFactorService.VerifyResult.OK) {
                loginAttemptService.recordFailure(lockKey);
                return Result.INVALID_CODE;
            }
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        loginAttemptService.recordSuccess(lockKey);
        userSessionService.revokeOthers(user, request);
        notificationService.notifyPasswordChanged(user);
        return Result.OK;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String lockKey(User user) {
        return "pwd:" + user.getEmail();
    }
}