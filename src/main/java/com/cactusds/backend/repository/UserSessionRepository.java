package com.cactusds.backend.repository;

import com.cactusds.backend.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findBySessionIdHash(String sessionIdHash);

    Optional<UserSession> findByIdAndUser_Id(Long id, Long userId);

    /**
     * Every non-revoked row for the user, newest activity first. Staleness (a session past the
     * configured timeout) is filtered afterwards in {@code UserSessionService}, in plain Java, so
     * that logic stays unit-testable without a database.
     */
    List<UserSession> findByUser_IdAndRevokedFalseOrderByLastSeenAtDesc(Long userId);

    void deleteBySessionIdHash(String sessionIdHash);

    /** Housekeeping: called by the daily cleanup job, not by request-handling code. */
    long deleteByRevokedTrue();

    long deleteByLastSeenAtBefore(LocalDateTime cutoff);
}