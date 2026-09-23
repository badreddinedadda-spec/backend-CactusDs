package com.cactusds.backend.controller;

import com.cactusds.backend.comon.security.TwoFactorService;
import com.cactusds.backend.dto.TwoFactorCodeRequest;
import com.cactusds.backend.dto.TwoFactorDisableRequest;
import com.cactusds.backend.model.User;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Manage 2FA for the logged-in account. Lives under /api/client/** so SecurityConfig already
 * requires an authenticated CLIENT or ADMIN: the same endpoints serve the admin "Paramètres > 2FA"
 * screen and the client "Sécurité" screen.
 */
@RestController
@RequestMapping("/api/client/2fa")
public class TwoFactorController {
    private final CurrentUserResolver currentUserResolver;
    private final TwoFactorService twoFactorService;

    public TwoFactorController(CurrentUserResolver currentUserResolver, TwoFactorService twoFactorService) {
        this.currentUserResolver = currentUserResolver;
        this.twoFactorService = twoFactorService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(Map.of(
                "enabled", twoFactorService.isEnabled(user),
                "recoveryCodesRemaining", twoFactorService.recoveryCodesRemaining(user),
                // Google-only accounts have no password, so the UI must not ask for one.
                "passwordRequired", user.getPasswordHash() != null));
    }

    /** Step 1: returns the secret + otpauth URI (the frontend turns it into a QR code). */
    @PostMapping("/setup")
    public ResponseEntity<?> setup(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        if (twoFactorService.isEnabled(user)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "already_enabled"));
        }
        TwoFactorService.SetupResult setup = twoFactorService.beginSetup(user);
        return ResponseEntity.ok(Map.of("secret", setup.secret(), "otpauthUri", setup.otpauthUri()));
    }

    /** Step 2: the user types the first code; 2FA only turns on if it is correct. */
    @PostMapping("/enable")
    public ResponseEntity<?> enable(@Valid @RequestBody TwoFactorCodeRequest req, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        if (twoFactorService.isEnabled(user)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "already_enabled"));
        }
        if (user.getTotpSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "setup_not_started"));
        }
        TwoFactorService.EnableResult result = twoFactorService.confirmSetup(user, req.code());
        if (result.result() == TwoFactorService.VerifyResult.LOCKED) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "account_locked"));
        }
        if (result.result() != TwoFactorService.VerifyResult.OK) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_code"));
        }
        return ResponseEntity.ok(Map.of("recoveryCodes", result.recoveryCodes()));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestBody TwoFactorDisableRequest req, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        if (!twoFactorService.isEnabled(user)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "not_enabled"));
        }
        TwoFactorService.VerifyResult result =
                twoFactorService.disable(user, req.password(), req.code(), req.recoveryCode());
        if (result == TwoFactorService.VerifyResult.LOCKED) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of("error", "account_locked"));
        }
        if (result != TwoFactorService.VerifyResult.OK) {
            // Deliberately does not say whether the password or the code was wrong.
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_code_or_password"));
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}