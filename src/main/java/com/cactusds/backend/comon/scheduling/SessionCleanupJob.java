package com.cactusds.backend.comon.scheduling;

import com.cactusds.backend.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Without this, "user_sessions" would only ever shrink on an explicit logout: a row for a
 * revoked device, or one whose owner simply closed the tab and let it time out, would otherwise
 * sit in the table forever.
 */
@Component
public class SessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    /** Generous on purpose: well past any realistic session timeout, so a slightly late cleanup
     * run can never delete a row the revocation filter still needs to check. */
    private static final int STALE_AFTER_DAYS = 7;

    private final UserSessionRepository userSessionRepository;

    public SessionCleanupJob(UserSessionRepository userSessionRepository) {
        this.userSessionRepository = userSessionRepository;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        long revoked = userSessionRepository.deleteByRevokedTrue();
        long stale = userSessionRepository.deleteByLastSeenAtBefore(LocalDateTime.now().minusDays(STALE_AFTER_DAYS));
        if (revoked > 0 || stale > 0) {
            log.info("Session cleanup: {} revoked row(s), {} stale row(s) removed.", revoked, stale);
        }
    }
}