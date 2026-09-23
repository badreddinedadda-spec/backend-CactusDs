package com.cactusds.backend.comon.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SessionFingerPrint {

    private SessionFingerPrint() {
    }

    public static String hash(String rawSessionId) {
        if (rawSessionId == null) {
            throw new IllegalArgumentException("rawSessionId must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawSessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}