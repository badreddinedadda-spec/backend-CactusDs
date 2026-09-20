package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    private String passwordHash;

    @Column(length = 150)
    private String fullName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CLIENT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    /** TOTP secret, AES-256-GCM encrypted (see SecretCipher). Null until 2FA setup starts. */
    @Column(name = "totp_secret", length = 255)
    private String totpSecret;

    /** True only once the user has confirmed a first code from their authenticator app. */
    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private Boolean totpEnabled = false;

    /** Last accepted 30 s time-step: refuses a code that was already used (replay protection). */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /** Comma-separated BCrypt hashes of the unused one-time recovery codes. */
    @Column(name = "totp_recovery_codes", length = 1000)
    private String totpRecoveryCodes;
}