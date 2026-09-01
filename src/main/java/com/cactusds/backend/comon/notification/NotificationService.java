package com.cactusds.backend.comon.notification;

import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import com.cactusds.backend.model.User;
import java.time.format.DateTimeFormatter;

@Component
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void notifyTicketReply(Ticket ticket) {
        String clientName = ticket.getUser().getFullName() != null ? ticket.getUser().getFullName() : ticket.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre ticket \"" + ticket.getSujet() + "\" a reçu une réponse :\n\n"
                + ticket.getReponseAdmin() + "\n\n"
                + "Statut actuel : " + ticket.getStatut().name() + "\n\n"
                + "L'équipe CactusDS";
        send(ticket.getUser().getEmail(), "Réponse à votre ticket : " + ticket.getSujet(), body);
    }

    public void notifyFactureGenerated(Facture facture) {
        String clientName = facture.getUser().getFullName() != null ? facture.getUser().getFullName() : facture.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Une nouvelle facture " + facture.getNumero() + " d'un montant de " + facture.getMontantTotal() + " MAD "
                + "a été émise pour la période du " + facture.getPeriodeDebut().format(DATE_FMT)
                + " au " + facture.getPeriodeFin().format(DATE_FMT) + ".\n\n"
                + "Vous pouvez la télécharger depuis votre espace client.\n\n"
                + "L'équipe CactusDS";
        send(facture.getUser().getEmail(), "Nouvelle facture " + facture.getNumero(), body);
    }

    public void notifyPasswordReset(User user, String resetLink) {
        String clientName = user.getFullName() != null ? user.getFullName() : user.getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Vous avez demandé la réinitialisation de votre mot de passe CactusDS.\n\n"
                + "Cliquez sur le lien suivant pour choisir un nouveau mot de passe (valable 1 heure, usage unique) :\n"
                + resetLink + "\n\n"
                + "Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email : votre mot de passe ne sera pas modifié.\n\n"
                + "L'équipe CactusDS";
        send(user.getEmail(), "Réinitialisation de votre mot de passe", body);
    }

    public void notifyExpirationReminder(Commande commande) {
        String clientName = commande.getUser().getFullName() != null ? commande.getUser().getFullName() : commande.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre service \"" + commande.getOffre().getNom() + "\" expire le "
                + commande.getDateExpiration().format(DATE_FMT) + ".\n\n"
                + "Contactez-nous pour le renouveler avant cette date.\n\n"
                + "L'équipe CactusDS";
        send(commande.getUser().getEmail(), "Votre service expire bientôt", body);
    }

    public void notifyFactureOverdueReminder(Facture facture) {
        String clientName = facture.getUser().getFullName() != null ? facture.getUser().getFullName() : facture.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre facture " + facture.getNumero() + " d'un montant de " + facture.getMontantTotal() + " MAD "
                + "est toujours impayée depuis son émission le " + facture.getDateEmission().format(DATE_FMT) + ".\n\n"
                + "Merci de régulariser votre paiement dès que possible pour éviter une suspension de service.\n\n"
                + "L'équipe CactusDS";
        send(facture.getUser().getEmail(), "Rappel : facture " + facture.getNumero() + " impayée", body);
    }

    public void notifyCommandeSuspended(Commande commande) {
        String clientName = commande.getUser().getFullName() != null ? commande.getUser().getFullName() : commande.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre service \"" + commande.getOffre().getNom() + "\" a été suspendu suite à une facture restée impayée trop longtemps.\n\n"
                + "Contactez-nous pour régulariser la situation et réactiver votre service.\n\n"
                + "L'équipe CactusDS";
        send(commande.getUser().getEmail(), "Service suspendu : " + commande.getOffre().getNom(), body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}