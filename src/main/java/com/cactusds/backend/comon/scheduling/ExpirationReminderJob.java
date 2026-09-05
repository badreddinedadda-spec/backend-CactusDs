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

    @Scheduled(cron = "0 0 8 * * *")
    public void sendReminders() {
        LocalDate target = LocalDate.now().plusDays(REMINDER_DAYS_BEFORE);
        List<Commande> expiringSoon = commandeRepository.findByDateExpirationAndReminderSentFalse(target, Commande.Statut.ACTIVE);
        for (Commande commande : expiringSoon) {
            notificationService.notifyExpirationReminder(commande);
            commande.setReminderSent(true);
            commandeRepository.save(commande);
        }
    }

    @Scheduled(cron = "0 15 8 * * *")
    public void expireOverdueCommandes() {
        List<Commande> overdue = commandeRepository.findByDateExpirationAndReminderSentFalse(LocalDate.now(), Commande.Statut.ACTIVE);
        for (Commande commande : overdue) {
            commande.setStatut(Commande.Statut.EXPIREE);
            commandeRepository.save(commande);
            notificationService.notifyServiceExpired(commande);
        }
    }
}