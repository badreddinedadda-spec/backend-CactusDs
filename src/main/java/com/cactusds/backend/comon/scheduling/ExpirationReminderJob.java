package com.cactusds.backend.comon.scheduling;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.repository.CommandeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ExpirationReminderJob {

    private static final int REMINDER_DAYS_BEFORE = 7;

    private final CommandeRepository commandeRepository;
    private final NotificationService notificationService;

    public ExpirationReminderJob(CommandeRepository commandeRepository, NotificationService notificationService) {
        this.commandeRepository = commandeRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *") // every day at 08:00 server time
    public void sendReminders() {
        LocalDate target = LocalDate.now().plusDays(REMINDER_DAYS_BEFORE);
        List<Commande> expiringSoon = commandeRepository.findByDateExpirationAndReminderSentFalse(target);
        for (Commande commande : expiringSoon) {
            notificationService.notifyExpirationReminder(commande);
            commande.setReminderSent(true);
            commandeRepository.save(commande);
        }
    }
}