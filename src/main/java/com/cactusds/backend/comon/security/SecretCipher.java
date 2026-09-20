package com.cactusds.backend.comon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the TOTP secret before it is written to the database (AES-256-GCM).
 *
 * A TOTP secret cannot be hashed like a password: the server needs the original value to
 * compute the expected code. Encrypting it means a leaked database dump alone is not enough
 * to generate valid codes; the attacker would also need the key, which lives in an
 * environment variable and never in the database.
 */
@Component
public class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.totp.encryption-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY is not valid base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "TOTP_ENCRYPTION_KEY must decode to exactly 32 bytes (got " + raw.length + ")");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** Returns base64(iv || ciphertext+tag). A fresh random IV is used for every call. */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the TOTP secret", e);
        }
    }

    public String decrypt(String token) {
        try {
            byte[] all = Base64.getDecoder().decode(token);
            if (all.length <= IV_BYTES) {
                throw new IllegalStateException("Encrypted TOTP secret is malformed");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key, tampered data or corrupted value: never fall back silently.
            throw new IllegalStateException("Could not decrypt the TOTP secret (wrong TOTP_ENCRYPTION_KEY?)", e);
        }
    }
}