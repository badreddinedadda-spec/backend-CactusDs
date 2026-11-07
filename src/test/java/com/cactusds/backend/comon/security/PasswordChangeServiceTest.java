package com.cactusds.backend.comon.security;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.comon.security.PasswordChangeService.Result;
import com.cactusds.backend.model.AuthProvider;
import com.cactusds.backend.model.User;
import com.cactusds.backend.model.UserSession;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeServiceTest {

    private static final String OLD_PW = "OldPassw0rd!";
    private static final String NEW_PW = "BrandNewPassw0rd!";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final List<SimpleMailMessage> mails = new ArrayList<>();
    private final List<UserSession> sessionRows = new ArrayList<>();

    private TotpService totp;
    private TwoFactorService twoFactor;
    private LoginAttemptService attempts;
    private UserSessionService sessions;
    private PasswordChangeService service;
    private HttpServletRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "save": return args[0];
                        case "findById": return Optional.empty();
                        case "toString": return "fake-user-repo";
                        case "hashCode": return 1;
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        UserSessionRepository sessionRepo = (UserSessionRepository) Proxy.newProxyInstance(
                UserSessionRepository.class.getClassLoader(), new Class[]{UserSessionRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findByUser_IdAndRevokedFalseOrderByLastSeenAtDesc":
                            return sessionRows.stream().filter(r -> !r.getRevoked()).toList();
                        case "saveAll": return args[0];
                        case "toString": return "fake-session-repo";
                        case "hashCode": return 2;
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        JavaMailSender mailSender = (JavaMailSender) Proxy.newProxyInstance(
                JavaMailSender.class.getClassLoader(), new Class[]{JavaMailSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("send") && args[0] instanceof SimpleMailMessage m) {
                        mails.add(m);
                        return null;
                    }
                    if (method.getName().equals("toString")) return "fake-mailer";
                    if (method.getName().equals("hashCode")) return 3;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        NotificationService notifications = new NotificationService(mailSender);
        ReflectionTestUtils.setField(notifications, "from", "no-reply@cactusds.ma");
        ReflectionTestUtils.setField(notifications, "companyEmail", "contact@cactusds.ma");
        ReflectionTestUtils.setField(notifications, "adminEmailOverride", "");

        totp = new TotpService();
        attempts = new LoginAttemptService();
        twoFactor = new TwoFactorService(userRepo, totp,
                new SecretCipher(Base64.getEncoder().encodeToString(new byte[32])), attempts);
        ReflectionTestUtils.setField(twoFactor, "issuer", "Cactus DS");
        sessions = new UserSessionService(sessionRepo, Duration.ofMinutes(30));
        service = new PasswordChangeService(userRepo, encoder, attempts, twoFactor, sessions, notifications);

        user = User.builder().id(7L).email("client@cactusds.ma").fullName("Client One")
                .passwordHash(encoder.encode(OLD_PW)).build();


        sessionRows.add(row(user, "sess-current"));
        sessionRows.add(row(user, "sess-phone"));
        sessionRows.add(row(user, "sess-laptop"));
        request = requestOnSession("sess-current");
    }

    private static UserSession row(User owner, String rawSessionId) {
        return UserSession.builder().user(owner).sessionIdHash(SessionFingerPrint.hash(rawSessionId)).build();
    }

    private static HttpServletRequest requestOnSession(String rawId) {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(
                HttpSession.class.getClassLoader(), new Class[]{HttpSession.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getId")) return rawId;
                    if (method.getName().equals("toString")) return "fake-session";
                    if (method.getName().equals("hashCode")) return 4;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSession")) return session;
                    if (method.getName().equals("toString")) return "fake-request";
                    if (method.getName().equals("hashCode")) return 5;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private boolean revoked(String rawSessionId) {
        String hash = SessionFingerPrint.hash(rawSessionId);
        return sessionRows.stream().filter(r -> r.getSessionIdHash().equals(hash)).findFirst().orElseThrow().getRevoked();
    }

    private boolean passwordIs(String plain) {
        return encoder.matches(plain, user.getPasswordHash());
    }

    private String currentCode(String base32Secret, long stepOffset) {
        long step = System.currentTimeMillis() / 30_000L + stepOffset;
        return totp.codeAt(TotpService.base32Decode(base32Secret), step);
    }

    private String wrongCode(String base32Secret) {
        java.util.Set<String> valid = new java.util.HashSet<>();
        for (long off = -2; off <= 2; off++) valid.add(currentCode(base32Secret, off));
        for (int n = 100000; ; n++) {
            if (!valid.contains(String.valueOf(n))) return String.valueOf(n);
        }
    }

    @Test
    void changesThePasswordRevokesOtherSessionsKeepsTheCurrentOneAndEmailsTheOwner() {
        assertEquals(Result.OK, service.change(user, OLD_PW, NEW_PW, null, null, request));

        assertTrue(passwordIs(NEW_PW));
        assertFalse(passwordIs(OLD_PW));
        assertFalse(revoked("sess-current"));
        assertTrue(revoked("sess-phone"));
        assertTrue(revoked("sess-laptop"));
        assertEquals(1, mails.size());
        assertEquals("client@cactusds.ma", mails.get(0).getTo()[0]);
    }

    @Test
    void theNewPasswordIsStoredHashedNeverInClear() {
        service.change(user, OLD_PW, NEW_PW, null, null, request);
        assertFalse(user.getPasswordHash().contains(NEW_PW));
        assertTrue(user.getPasswordHash().startsWith("$2"));
    }

    @Test
    void wrongCurrentPasswordIsRefusedAndNothingChanges() {
        assertEquals(Result.WRONG_PASSWORD, service.change(user, "not-it", NEW_PW, null, null, request));

        assertTrue(passwordIs(OLD_PW));
        assertFalse(revoked("sess-phone"));
        assertTrue(mails.isEmpty());
    }

    @Test
    void samePasswordIsRefused() {
        assertEquals(Result.SAME_PASSWORD, service.change(user, OLD_PW, OLD_PW, null, null, request));
        assertFalse(revoked("sess-phone"));
        assertTrue(mails.isEmpty());
    }

    @Test
    void googleOnlyAccountsHaveNoPasswordToChange() {
        User google = User.builder().id(8L).email("g@cactusds.ma").authProvider(AuthProvider.GOOGLE).build();
        assertEquals(Result.NO_PASSWORD_ACCOUNT, service.change(google, "x", NEW_PW, null, null, request));
    }

    @Test
    void fiveWrongCurrentPasswordsLockEvenTheRightOne() {
        for (int i = 0; i < 5; i++) {
            assertEquals(Result.WRONG_PASSWORD, service.change(user, "wrong" + i, NEW_PW, null, null, request));
        }
        assertEquals(Result.LOCKED, service.change(user, OLD_PW, NEW_PW, null, null, request));
        assertTrue(passwordIs(OLD_PW));
    }

    @Test
    void thePasswordChangeLockDoesNotLockTheRealOwnerOutOfLogin() {
        for (int i = 0; i < 5; i++) service.change(user, "wrong" + i, NEW_PW, null, null, request);
        assertFalse(attempts.isLocked(user.getEmail()));
    }

    private String enableTwoFactor() {
        return enableTwoFactor(new ArrayList<>());
    }

    private String enableTwoFactor(List<String> recoveryOut) {
        String secret = twoFactor.beginSetup(user).secret();
        TwoFactorService.EnableResult r = twoFactor.confirmSetup(user, currentCode(secret, 0));
        assertEquals(TwoFactorService.VerifyResult.OK, r.result());
        recoveryOut.addAll(r.recoveryCodes());
        return secret;
    }

    @Test
    void anAccountWithTwoFactorMustProvideACode() {
        enableTwoFactor();
        assertEquals(Result.SECOND_FACTOR_REQUIRED, service.change(user, OLD_PW, NEW_PW, null, null, request));
        assertEquals(Result.SECOND_FACTOR_REQUIRED, service.change(user, OLD_PW, NEW_PW, "  ", "", request));
        assertTrue(passwordIs(OLD_PW));
    }

    @Test
    void aWrongTwoFactorCodeIsRefused() {
        String secret = enableTwoFactor();
        assertEquals(Result.INVALID_CODE, service.change(user, OLD_PW, NEW_PW, wrongCode(secret), null, request));
        assertTrue(passwordIs(OLD_PW));
        assertFalse(revoked("sess-phone"));
    }

    @Test
    void aValidTwoFactorCodeLetsTheChangeThrough() {
        String secret = enableTwoFactor();
        // one step later than the code consumed during setup, so it is not seen as a replay
        assertEquals(Result.OK, service.change(user, OLD_PW, NEW_PW, currentCode(secret, 1), null, request));
        assertTrue(passwordIs(NEW_PW));
    }

    @Test
    void aWrongPasswordNeverBurnsARecoveryCode() {
        List<String> codes = new ArrayList<>();
        enableTwoFactor(codes);

        assertEquals(Result.WRONG_PASSWORD, service.change(user, "not-it", NEW_PW, null, codes.get(0), request));
        assertEquals(8, twoFactor.recoveryCodesRemaining(user));

        assertEquals(Result.OK, service.change(user, OLD_PW, NEW_PW, null, codes.get(0), request));
        assertEquals(7, twoFactor.recoveryCodesRemaining(user));
    }
}