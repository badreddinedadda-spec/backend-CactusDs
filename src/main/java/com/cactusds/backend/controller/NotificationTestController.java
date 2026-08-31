package com.cactusds.backend.controller;

import com.cactusds.backend.comon.scheduling.ExpirationReminderJob;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationTestController {

    private final ExpirationReminderJob expirationReminderJob;

    public NotificationTestController(ExpirationReminderJob expirationReminderJob) {
        this.expirationReminderJob = expirationReminderJob;
    }

    @PostMapping("/api/admin/notifications/run-expiration-check")
    public String runNow() {
        expirationReminderJob.sendReminders();
        return "Expiration check triggered";
    }
}