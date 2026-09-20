package com.cactusds.backend.comon.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s step): the profile supported by every
 * authenticator app (Google Authenticator, Microsoft Authenticator, Authy, 1Password...).
 *
 * No third-party dependency on purpose: the algorithm is small and is covered by the
 * official RFC test vectors in TotpServiceTest.
 */
@Component
public class TotpService {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20; // 160 bits, as recommended by RFC 4226
    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    /** Also accept the previous and the next 30 s step, to tolerate small clock drift. */
    private static final int WINDOW = 1;

    private final SecureRandom random = new SecureRandom();

    /** New random secret, base32-encoded (32 characters), ready to be shown to the user. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** The otpauth:// URI that authenticator apps read from a QR code. */
    public String buildOtpAuthUri(String issuer, String account, String base32Secret) {
        return "otpauth://totp/" + urlEncode(issuer) + ":" + urlEncode(account)
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /**
     * Checks a code against the secret.
     *
     * @param lastUsedStep the last time-step already accepted for this user (-1 if none).
     *                     A code from a step <= lastUsedStep is refused, so a code that was
     *                     just used (or shoulder-surfed) cannot be replayed.
     * @return the matching time-step, or -1 when the code is invalid or already used.
     */
    public long verify(String base32Secret, String code, long nowMillis, long lastUsedStep) {
        if (base32Secret == null || code == null) {
            return -1;
        }
        String candidate = code.replace(" ", "").trim();
        if (candidate.length() != DIGITS) {
            return -1;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
        }

        byte[] key = base32Decode(base32Secret);
        byte[] given = candidate.getBytes(StandardCharsets.UTF_8);
        long currentStep = Math.floorDiv(nowMillis, STEP_SECONDS * 1000L);

        long matched = -1;
        for (long step = currentStep - WINDOW; step <= currentStep + WINDOW; step++) {
            byte[] expected = codeAt(key, step).getBytes(StandardCharsets.UTF_8);
            // Constant-time comparison; every step of the window is always checked.
            if (MessageDigest.isEqual(expected, given) && step > lastUsedStep) {
                matched = Math.max(matched, step);
            }
        }
        return matched;
    }

    /** The 6-digit code for a given time-step (package-private: used by tests). */
    String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());

            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
            buffer &= (1 << bitsLeft) - 1;
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return out.toString();
    }

    static byte[] base32Decode(String input) {
        String s = input.trim().replace(" ", "").replace("-", "").replace("=", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < s.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(s.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + s.charAt(i));
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
                buffer &= (1 << bitsLeft) - 1;
            }
        }
        return out.toByteArray();
    }

    private static String urlEncode(String value) {
        // otpauth labels need %20, not the '+' that URLEncoder uses for spaces.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}