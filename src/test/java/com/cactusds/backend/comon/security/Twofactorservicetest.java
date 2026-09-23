package com.cactusds.backend.comon.security;

import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cactusds.backend.comon.security.TwoFactorService.VerifyResult;

/** Plain-JDK test doubles (java.lang.reflect.Proxy): no Mockito, no Spring context, no database. */
class TwoFactorServiceTest {

    private TotpService totp;
    private SecretCipher cipher;
    private TwoFactorService service;
    private User user;

    @BeforeEach
    void setUp() {
        totp = new TotpService();
        cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        UserRepository repo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) return args[0];
                    if (method.getName().equals("findById")) return Optional.empty();
                    if (method.getName().equals("toString")) return "fake-repo";
                    if (method.getName().equals("hashCode")) return 1;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new TwoFactorService(repo, totp, cipher, new LoginAttemptService());
        ReflectionTestUtils.setField(service, "issuer", "Cactus DS");

        user = new User();
        user.setId(42L);
        user.setEmail("admin@cactusds.ma");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("S3cret!pass"));
    }

    private String currentCode(String base32Secret, long stepOffset) {
        long step = System.currentTimeMillis() / 30_000L + stepOffset;
        return totp.codeAt(TotpService.base32Decode(base32Secret), step);
    }

    /** A 6-digit code guaranteed NOT to be valid right now (not in the +/-2 steps around now). */
    private String wrongCode(String base32Secret) {
        java.util.Set<String> valid = new java.util.HashSet<>();
        for (long off = -2; off <= 2; off++) valid.add(currentCode(base32Secret, off));
        for (int n = 100000; ; n++) {
            String candidate = String.valueOf(n);
            if (!valid.contains(candidate)) return candidate;
        }
    }

    private String enable() {
        String secret = service.beginSetup(user).secret();
        TwoFactorService.EnableResult r = service.confirmSetup(user, currentCode(secret, 0));
        assertEquals(VerifyResult.OK, r.result());
        return secret;
    }

    @Test
    void setupDoesNotEnableUntilACodeIsConfirmed() {
        TwoFactorService.SetupResult setup = service.beginSetup(user);
        assertFalse(service.isEnabled(user));
        assertTrue(setup.otpauthUri().startsWith("otpauth://totp/Cactus%20DS:"));
        assertEquals(VerifyResult.INVALID, service.confirmSetup(user, wrongCode(setup.secret())).result());
        assertFalse(service.isEnabled(user));
    }

    @Test
    void secretIsStoredEncrypted() {
        String secret = service.beginSetup(user).secret();
        assertNotEquals(secret, user.getTotpSecret());
        assertFalse(user.getTotpSecret().contains(secret));
        assertEquals(secret, cipher.decrypt(user.getTotpSecret()));
    }

    @Test
    void confirmingWithAValidCodeEnablesAndReturnsEightRecoveryCodesOnce() {
        String secret = service.beginSetup(user).secret();
        TwoFactorService.EnableResult r = service.confirmSetup(user, currentCode(secret, 0));
        assertEquals(VerifyResult.OK, r.result());
        assertTrue(service.isEnabled(user));
        assertEquals(8, r.recoveryCodes().size());
        assertTrue(r.recoveryCodes().get(0).matches("[A-Z2-9]{5}-[A-Z2-9]{5}"));
        assertEquals(8, service.recoveryCodesRemaining(user));
        // only hashes are stored, never the codes themselves
        for (String c : r.recoveryCodes()) {
            assertFalse(user.getTotpRecoveryCodes().contains(c.replace("-", "")));
        }
    }

    @Test
    void loginChallengeAcceptsACodeOnceAndRefusesItsReplay() {
        String secret = enable();
        String next = currentCode(secret, 1); // later than the step consumed during setup
        assertEquals(VerifyResult.OK, service.verifyLoginChallenge(user, next, null));
        assertEquals(VerifyResult.INVALID, service.verifyLoginChallenge(user, next, null));
    }

    @Test
    void loginChallengeLocksAfterFiveWrongCodesEvenForTheRightOne() {
        String secret = enable();
        for (int i = 0; i < 5; i++) {
            assertEquals(VerifyResult.INVALID, service.verifyLoginChallenge(user, wrongCode(secret), null));
        }
        assertEquals(VerifyResult.LOCKED, service.verifyLoginChallenge(user, currentCode(secret, 1), null));
    }

    @Test
    void recoveryCodeWorksOnceThenIsConsumed() {
        String secret = service.beginSetup(user).secret();
        List<String> codes = service.confirmSetup(user, currentCode(secret, 0)).recoveryCodes();
        String first = codes.get(0);

        assertEquals(VerifyResult.OK, service.verifyLoginChallenge(user, null, first));
        assertEquals(7, service.recoveryCodesRemaining(user));
        assertEquals(VerifyResult.INVALID, service.verifyLoginChallenge(user, null, first));
        // typed without the dash and in lower case still matches another code
        assertEquals(VerifyResult.OK,
                service.verifyLoginChallenge(user, null, codes.get(1).replace("-", "").toLowerCase()));
    }

    @Test
    void disableNeedsThePasswordAndDoesNotBurnARecoveryCodeOnAWrongPassword() {
        String secret = service.beginSetup(user).secret();
        List<String> codes = service.confirmSetup(user, currentCode(secret, 0)).recoveryCodes();

        assertEquals(VerifyResult.INVALID, service.disable(user, "wrong-password", null, codes.get(0)));
        assertTrue(service.isEnabled(user));
        assertEquals(8, service.recoveryCodesRemaining(user));

        assertEquals(VerifyResult.OK, service.disable(user, "S3cret!pass", null, codes.get(0)));
        assertFalse(service.isEnabled(user));
        assertNull(user.getTotpSecret());
        assertNull(user.getTotpRecoveryCodes());
    }

    @Test
    void googleOnlyAccountsCanDisableWithoutAPassword() {
        user.setPasswordHash(null);
        String secret = enable();
        assertEquals(VerifyResult.OK, service.disable(user, null, currentCode(secret, 1), null));
        assertFalse(service.isEnabled(user));
    }

    private static HttpServletRequest requestWithSession(Map<String, Object> attrs) {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(), new Class[]{HttpSession.class},
                (p, m, a) -> switch (m.getName()) {
                    case "setAttribute" -> { attrs.put((String) a[0], a[1]); yield null; }
                    case "getAttribute" -> attrs.get((String) a[0]);
                    case "removeAttribute" -> { attrs.remove((String) a[0]); yield null; }
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class[]{HttpServletRequest.class},
                (p, m, a) -> {
                    if (m.getName().equals("getSession")) return session;
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    @Test
    void pendingLoginRemembersTheUserAndClearsAnyAuthenticatedContext() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("SPRING_SECURITY_CONTEXT", "already-authenticated");
        HttpServletRequest request = requestWithSession(attrs);

        service.startPendingLogin(request, user);

        assertEquals(Optional.of(42L), service.pendingUserId(request));
        assertFalse(attrs.containsKey("SPRING_SECURITY_CONTEXT"));

        service.clearPending(request);
        assertEquals(Optional.empty(), service.pendingUserId(request));
    }

    @Test
    void pendingLoginExpiresAfterFiveMinutes() {
        Map<String, Object> attrs = new HashMap<>();
        HttpServletRequest request = requestWithSession(attrs);
        service.startPendingLogin(request, user);
        attrs.put(TwoFactorService.SESSION_PENDING_AT, System.currentTimeMillis() - 6 * 60 * 1000L);
        assertEquals(Optional.empty(), service.pendingUserId(request));
    }
}