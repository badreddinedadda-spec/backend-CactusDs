package com.cactusds.backend.comon.scheduling;

import com.cactusds.backend.comon.notification.NotificationService;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.FactureRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BillingAutomationJob {

    private static final int RELANCE_APRES_JOURS = 7;
    private static final int SUSPENSION_APRES_JOURS = 15;

    private final FactureRepository factureRepository;
    private final CommandeRepository commandeRepository;
    private final NotificationService notificationService;

    public BillingAutomationJob(FactureRepository factureRepository, CommandeRepository commandeRepository,
                                NotificationService notificationService) {
        this.factureRepository = factureRepository;
        this.commandeRepository = commandeRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 8 * * *") // every day at 08:30, right after ExpirationReminderJob
    public void relancerFacturesImpayees() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RELANCE_APRES_JOURS);
        List<Facture> unpaid = factureRepository
                .findByStatutAndDateEmissionBeforeAndRelanceEnvoyeeFalse(Facture.Statut.EMISE, cutoff);
        for (Facture facture : unpaid) {
            notificationService.notifyFactureOverdueReminder(facture);
            facture.setRelanceEnvoyee(true);
            factureRepository.save(facture);
        }
    }

    @Scheduled(cron = "0 45 8 * * *") // every day at 08:45
    public void suspendreServicesImpayes() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(SUSPENSION_APRES_JOURS);
        List<Facture> overdue = factureRepository.findByStatutAndDateEmissionBefore(Facture.Statut.EMISE, cutoff);
        for (Facture facture : overdue) {
            List<Commande> commandes = commandeRepository.findByFactureIdOrderByCreatedAtAsc(facture.getId());
            for (Commande commande : commandes) {
                if (commande.getStatut() == Commande.Statut.ACTIVE) {
                    commande.setStatut(Commande.Statut.SUSPENDUE);
                    commandeRepository.save(commande);
                    notificationService.notifyCommandeSuspended(commande);
                }
            }
        }
    }
}