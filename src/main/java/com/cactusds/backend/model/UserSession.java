package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per HTTP session opened by a user, so the "Sessions" screen can show every device
 * currently signed in and let the user end one remotely.
 *
 * The raw servlet session id is never stored, only its SHA-256 fingerprint (see
 * {@link com.cactusds.backend.comon.security.SessionFingerPrint}) — a leaked table then cannot be
 * replayed as a valid session cookie.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_id_hash", nullable = false, unique = true, length = 64)
    private String sessionIdHash;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt = LocalDateTime.now();

    /**
     * Set by {@code DELETE /api/client/sessions/{id}} for a session that isn't the caller's own.
     * The row is kept (not deleted) so {@code SessionRevocationFilter} can still find it on that
     * device's next request and force it out. {@code @Builder.Default} is required here so the
     * Lombok-generated builder also defaults to false; without it, the builder would silently
     * ignore this field initializer and leave the value null.
     */
    @Builder.Default
    @Column(nullable = false)
    private Boolean revoked = false;
}