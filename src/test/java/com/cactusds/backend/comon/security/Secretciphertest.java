package com.cactusds.backend.comon.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretCipherTest {

    private static String key(int fill) {
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void roundTrips() {
        SecretCipher cipher = new SecretCipher(key(7));
        assertEquals("JBSWY3DPEHPK3PXP", cipher.decrypt(cipher.encrypt("JBSWY3DPEHPK3PXP")));
    }

    @Test
    void usesAFreshIvSoTheSameSecretNeverEncryptsTheSameWay() {
        SecretCipher cipher = new SecretCipher(key(7));
        assertNotEquals(cipher.encrypt("SECRET"), cipher.encrypt("SECRET"));
    }

    @Test
    void refusesToDecryptWithAnotherKey() {
        String token = new SecretCipher(key(7)).encrypt("SECRET");
        SecretCipher other = new SecretCipher(key(8));
        assertThrows(IllegalStateException.class, () -> other.decrypt(token));
    }

    @Test
    void detectsTampering() {
        SecretCipher cipher = new SecretCipher(key(7));
        byte[] all = Base64.getDecoder().decode(cipher.encrypt("SECRET"));
        all[all.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(all);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void rejectsAKeyThatIsNot32Bytes() {
        assertThrows(IllegalStateException.class,
                () -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalStateException.class, () -> new SecretCipher("not base64 !!"));
    }
}