package com.cactusds.backend.comon.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    // RFC 6238, appendix B (SHA-1, secret "12345678901234567890"). The RFC lists 8-digit codes;
    // the 6-digit code is their last 6 digits.
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesTheOfficialRfc6238TestVectors() {
        assertEquals("287082", totp.codeAt(RFC_KEY, 59L / 30));          // 94287082
        assertEquals("081804", totp.codeAt(RFC_KEY, 1111111109L / 30));  // 07081804
        assertEquals("050471", totp.codeAt(RFC_KEY, 1111111111L / 30));  // 14050471
        assertEquals("005924", totp.codeAt(RFC_KEY, 1234567890L / 30));  // 89005924
        assertEquals("279037", totp.codeAt(RFC_KEY, 2000000000L / 30));  // 69279037
        assertEquals("353130", totp.codeAt(RFC_KEY, 20000000000L / 30)); // 65353130
    }

    @Test
    void base32MatchesTheKnownEncodingAndRoundTrips() {
        assertEquals(RFC_SECRET_BASE32, TotpService.base32Encode(RFC_KEY));
        assertEquals("12345678901234567890",
                new String(TotpService.base32Decode(RFC_SECRET_BASE32), StandardCharsets.US_ASCII));
        String secret = totp.generateSecret();
        assertEquals(32, secret.length());
        assertEquals(20, TotpService.base32Decode(secret).length);
        assertEquals(secret, TotpService.base32Encode(TotpService.base32Decode(secret)));
    }

    @Test
    void acceptsTheCurrentStepAndOneStepOfClockDrift() {
        long now = 1_700_000_000_000L;
        long step = now / 30_000L;
        assertEquals(step, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step), now, -1));
        assertEquals(step - 1, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step - 1), now, -1));
        assertEquals(step + 1, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step + 1), now, -1));
    }

    @Test
    void rejectsCodesOutsideTheWindow() {
        long now = 1_700_000_000_000L;
        long step = now / 30_000L;
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step - 2), now, -1));
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step + 2), now, -1));
    }

    @Test
    void rejectsReplayOfAnAlreadyUsedStep() {
        long now = 1_700_000_000_000L;
        long step = now / 30_000L;
        String code = totp.codeAt(RFC_KEY, step);
        long first = totp.verify(RFC_SECRET_BASE32, code, now, -1);
        assertEquals(step, first);
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, code, now, first)); // same code again
        // an older step than the last used one is refused too
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, step - 1), now, first));
    }

    @Test
    void rejectsMalformedCodes() {
        long now = 1_700_000_000_000L;
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, null, now, -1));
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, "", now, -1));
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, "12345", now, -1));
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, "1234567", now, -1));
        assertEquals(-1, totp.verify(RFC_SECRET_BASE32, "abcdef", now, -1));
        assertEquals(-1, totp.verify(null, "123456", now, -1));
    }

    @Test
    void ignoresSpacesTypedInTheCode() {
        long now = 1_700_000_000_000L;
        long step = now / 30_000L;
        String code = totp.codeAt(RFC_KEY, step);
        String spaced = code.substring(0, 3) + " " + code.substring(3);
        assertEquals(step, totp.verify(RFC_SECRET_BASE32, spaced, now, -1));
    }

    @Test
    void buildsAnOtpauthUriAuthenticatorAppsUnderstand() {
        String uri = totp.buildOtpAuthUri("Cactus DS", "admin@cactusds.ma", RFC_SECRET_BASE32);
        assertTrue(uri.startsWith("otpauth://totp/Cactus%20DS:admin%40cactusds.ma?"));
        assertTrue(uri.contains("secret=" + RFC_SECRET_BASE32));
        assertTrue(uri.contains("issuer=Cactus%20DS"));
        assertTrue(uri.contains("algorithm=SHA1"));
        assertTrue(uri.contains("digits=6"));
        assertTrue(uri.contains("period=30"));
    }
}