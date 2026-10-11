package com.cactusds.backend.comon.security;

import com.cactusds.backend.dto.SessionResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.model.UserSession;
import com.cactusds.backend.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserSessionService {
    static final Duration TOUCH_THROTTLE = Duration.ofSeconds(60);

    public enum RevokeResult { OK, NOT_FOUND, IS_CURRENT_SESSION }

    private final UserSessionRepository repository;
    private final Duration sessionTimeout;

    public UserSessionService(UserSessionRepository repository,
                              @Value("${server.servlet.session.timeout:30m}") Duration sessionTimeout) {
        this.repository = repository;
        this.sessionTimeout = sessionTimeout;
    }
    public void recordLogin(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        String hash = SessionFingerPrint.hash(session.getId());
        LocalDateTime now = LocalDateTime.now();
        UserSession row = repository.findBySessionIdHash(hash).orElseGet(UserSession::new);
        row.setUser(user);
        row.setSessionIdHash(hash);
        row.setIpAddress(clientIp(request));
        row.setUserAgent(truncate(request.getHeader("User-Agent"), 255));
        row.setCreatedAt(now);
        row.setLastSeenAt(now);
        row.setRevoked(false);
        repository.save(row);
    }

    public void touch(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        String hash = SessionFingerPrint.hash(session.getId());
        repository.findBySessionIdHash(hash).ifPresent(row -> {
            if (!Boolean.TRUE.equals(row.getRevoked()) && shouldTouch(row.getLastSeenAt(), LocalDateTime.now())) {
                row.setLastSeenAt(LocalDateTime.now());
                repository.save(row);
            }
        });
    }

    public boolean isRevoked(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String hash = SessionFingerPrint.hash(session.getId());
        return repository.findBySessionIdHash(hash).map(UserSession::getRevoked).orElse(false);
    }
    @Transactional
    public void endSession(String rawSessionId) {
        if (rawSessionId == null) {
            return;
        }
        repository.deleteBySessionIdHash(SessionFingerPrint.hash(rawSessionId));
    }

    public List<SessionResponse> listActive(User user, HttpServletRequest request) {
        String currentHash = currentSessionHash(request);
        LocalDateTime now = LocalDateTime.now();
        return repository.findByUser_IdAndRevokedFalseOrderByLastSeenAtDesc(user.getId()).stream()
                .filter(row -> !isStale(row.getLastSeenAt(), now, sessionTimeout))
                .map(row -> new SessionResponse(
                        row.getId(),
                        UserAgentParser.describe(row.getUserAgent()),
                        row.getIpAddress(),
                        row.getCreatedAt(),
                        row.getLastSeenAt(),
                        row.getSessionIdHash().equals(currentHash)))
                .toList();
    }

    public RevokeResult revoke(User user, Long sessionRowId, HttpServletRequest request) {
        Optional<UserSession> found = repository.findByIdAndUser_Id(sessionRowId, user.getId());
        if (found.isEmpty()) {
            return RevokeResult.NOT_FOUND;
        }
        UserSession row = found.get();
        String currentHash = currentSessionHash(request);
        if (currentHash != null && row.getSessionIdHash().equals(currentHash)) {
            return RevokeResult.IS_CURRENT_SESSION;
        }
        row.setRevoked(true);
        repository.save(row);
        return RevokeResult.OK;
    }

    static boolean shouldTouch(LocalDateTime lastSeenAt, LocalDateTime now) {
        return lastSeenAt == null || Duration.between(lastSeenAt, now).compareTo(TOUCH_THROTTLE) >= 0;
    }

    static boolean isStale(LocalDateTime lastSeenAt, LocalDateTime now, Duration timeout) {
        return lastSeenAt == null || Duration.between(lastSeenAt, now).compareTo(timeout) > 0;
    }

    private String currentSessionHash(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : SessionFingerPrint.hash(session.getId());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}