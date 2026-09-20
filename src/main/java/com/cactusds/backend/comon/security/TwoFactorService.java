package com.cactusds.backend.comon.security;

import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * All the 2FA (TOTP) business rules live here; controllers stay thin.
 *
 * Note: the PasswordEncoder is created locally instead of injected. The bean defined in
 * SecurityConfig would create a circular dependency (SecurityConfig -> OAuth2LoginSuccessHandler
 * -> TwoFactorService -> SecurityConfig). A BCryptPasswordEncoder is stateless and reads the
 * same hashes, so this is equivalent.
 */
@Service
public class TwoFactorService {

    public static final String SESSION_PENDING_USER_ID = "PENDING_2FA_USER_ID";
    public static final String SESSION_PENDING_AT = "PENDING_2FA_AT";
    /** After a correct password the user has this long to provide the second factor. */
    static final long PENDING_TTL_MS = 5 * 60 * 1000L;

    // No I, O, 0, 1: they are easy to confuse when a code is copied by hand.
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 10;

    public enum VerifyResult { OK, INVALID, LOCKED }

    public record SetupResult(String secret, String otpauthUri) {}

    public record EnableResult(VerifyResult result, List<String> recoveryCodes) {}

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final SecretCipher secretCipher;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    @Value("${app.totp.issuer:Cactus DS}")
    private String issuer;

    public TwoFactorService(UserRepository userRepository, TotpService totpService,
                            SecretCipher secretCipher, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.secretCipher = secretCipher;
        this.loginAttemptService = loginAttemptService;
    }

    //status

    public boolean isEnabled(User user) {
        return Boolean.TRUE.equals(user.getTotpEnabled());
    }

    public int recoveryCodesRemaining(User user) {
        return splitHashes(user.getTotpRecoveryCodes()).size();
    }

    //setup / enable / disable

    /** Generates a fresh secret and stores it (encrypted) but does NOT enable 2FA yet. */
    public SetupResult beginSetup(User user) {
        if (isEnabled(user)) {
            throw new IllegalStateException("2FA is already enabled");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecret(secretCipher.encrypt(secret));
        user.setTotpEnabled(false);
        user.setTotpLastStep(null);
        user.setTotpRecoveryCodes(null);
        userRepository.save(user);
        return new SetupResult(secret, totpService.buildOtpAuthUri(issuer, user.getEmail(), secret));
    }

    /**
     * Confirms the first code shown by the authenticator app. Only then is 2FA switched on,
     * so a user can never lock themselves out with a QR code they failed to scan.
     * The recovery codes are returned in clear text exactly once: only hashes are stored.
     */
    public EnableResult confirmSetup(User user, String code) {
        String lockKey = lockKey(user);
        if (loginAttemptService.isLocked(lockKey)) {
            return new EnableResult(VerifyResult.LOCKED, List.of());
        }
        if (!checkTotp(user, code)) {
            loginAttemptService.recordFailure(lockKey);
            return new EnableResult(VerifyResult.INVALID, List.of());
        }
        loginAttemptService.recordSuccess(lockKey);

        List<String> recoveryCodes = generateRecoveryCodes();
        user.setTotpEnabled(true);
        user.setTotpRecoveryCodes(hashAll(recoveryCodes));
        userRepository.save(user);
        return new EnableResult(VerifyResult.OK, recoveryCodes);
    }

    /** Turning 2FA off requires the password (if the account has one) AND a code or recovery code. */
    public VerifyResult disable(User user, String password, String code, String recoveryCode) {
        String lockKey = lockKey(user);
        if (loginAttemptService.isLocked(lockKey)) {
            return VerifyResult.LOCKED;
        }
        boolean passwordOk = user.getPasswordHash() == null // Google-only account: no password exists
                || (password != null && passwordEncoder.matches(password, user.getPasswordHash()));
        // Short-circuit on purpose: a wrong password must not burn a recovery code.
        boolean secondFactorOk = passwordOk && consumeSecondFactor(user, code, recoveryCode);
        if (!secondFactorOk) {
            loginAttemptService.recordFailure(lockKey);
            return VerifyResult.INVALID;
        }
        loginAttemptService.recordSuccess(lockKey);

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        user.setTotpRecoveryCodes(null);
        userRepository.save(user);
        return VerifyResult.OK;
    }

    //login second step

    /** Verifies the code (or a recovery code) of the second login step. */
    public VerifyResult verifyLoginChallenge(User user, String code, String recoveryCode) {
        String lockKey = lockKey(user);
        if (loginAttemptService.isLocked(lockKey)) {
            return VerifyResult.LOCKED;
        }
        if (!consumeSecondFactor(user, code, recoveryCode)) {
            loginAttemptService.recordFailure(lockKey);
            return VerifyResult.INVALID;
        }
        loginAttemptService.recordSuccess(lockKey);
        userRepository.save(user);
        return VerifyResult.OK;
    }

    //pending login (session state)

    /**
     * Called once the password (or Google) step succeeded for a user who has 2FA on.
     * The session is kept anonymous: it only remembers WHO is waiting to provide a code.
     */
    public void startPendingLogin(HttpServletRequest request, User user) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(true);
        session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        session.setAttribute(SESSION_PENDING_USER_ID, user.getId());
        session.setAttribute(SESSION_PENDING_AT, System.currentTimeMillis());
    }

    public Optional<Long> pendingUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object id = session.getAttribute(SESSION_PENDING_USER_ID);
        Object startedAt = session.getAttribute(SESSION_PENDING_AT);
        if (!(id instanceof Long userId) || !(startedAt instanceof Long started)) {
            return Optional.empty();
        }
        if (System.currentTimeMillis() - started > PENDING_TTL_MS) {
            clearPending(request);
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    public void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_PENDING_USER_ID);
            session.removeAttribute(SESSION_PENDING_AT);
        }
    }

    //Internals

    /** Accepts a TOTP code if given, otherwise a recovery code. Mutates the user; callers save. */
    private boolean consumeSecondFactor(User user, String code, String recoveryCode) {
        if (code != null && !code.isBlank()) {
            return checkTotp(user, code);
        }
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            return consumeRecoveryCode(user, recoveryCode);
        }
        return false;
    }

    /** Verifies a TOTP code and, on success, records its time-step to block replays. Callers save. */
    private boolean checkTotp(User user, String code) {
        if (user.getTotpSecret() == null) {
            return false;
        }
        String secret = secretCipher.decrypt(user.getTotpSecret());
        long lastStep = user.getTotpLastStep() == null ? -1 : user.getTotpLastStep();
        long step = totpService.verify(secret, code, System.currentTimeMillis(), lastStep);
        if (step < 0) {
            return false;
        }
        user.setTotpLastStep(step);
        return true;
    }

    private boolean consumeRecoveryCode(User user, String supplied) {
        String normalized = normalizeRecovery(supplied);
        if (normalized.length() != RECOVERY_CODE_LENGTH) {
            return false;
        }
        List<String> hashes = splitHashes(user.getTotpRecoveryCodes());
        for (Iterator<String> it = hashes.iterator(); it.hasNext(); ) {
            if (passwordEncoder.matches(normalized, it.next())) {
                it.remove(); // single use
                user.setTotpRecoveryCodes(String.join(",", hashes));
                return true;
            }
        }
        return false;
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder raw = new StringBuilder();
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                raw.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(raw.substring(0, 5) + "-" + raw.substring(5)); // shown as XXXXX-XXXXX
        }
        return codes;
    }

    private String hashAll(List<String> codes) {
        return codes.stream()
                .map(c -> passwordEncoder.encode(normalizeRecovery(c)))
                .collect(Collectors.joining(","));
    }

    private static String normalizeRecovery(String code) {
        return code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private static List<String> splitHashes(String stored) {
        if (stored == null || stored.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(stored.split(","))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Separate counter from the password lockout, so a correct password does not reset it. */
    private static String lockKey(User user) {
        return "2fa:" + user.getEmail();
    }
}