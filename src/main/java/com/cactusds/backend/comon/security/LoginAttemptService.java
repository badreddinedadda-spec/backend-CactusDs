package com.cactusds.backend.comon.security;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private static class Attempt {
        int count = 0;
        LocalDateTime lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        String key = normalize(email);
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.lockedUntil == null) return false;
        if (attempt.lockedUntil.isBefore(LocalDateTime.now())) {
            attempts.remove(key);
            return false;
        }
        return true;
    }

    public void recordFailure(String email) {
        String key = normalize(email);
        Attempt attempt = attempts.computeIfAbsent(key, k -> new Attempt());
        attempt.count++;
        if (attempt.count >= MAX_ATTEMPTS) {
            attempt.lockedUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
        }
    }

    public void recordSuccess(String email) {
        attempts.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}