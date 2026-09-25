package com.cactusds.backend.comon.notification;

import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import com.cactusds.backend.model.Ticket;
import com.cactusds.backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.notifications.admin-email:}")
    private String adminEmailOverride;

    @Value("${app.company.email}")
    private String companyEmail;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Where internal "something needs your attention" alerts go: a dedicated inbox if one was
     * configured, otherwise the company's own contact address. */
    private String adminEmail() {
        return (adminEmailOverride != null && !adminEmailOverride.isBlank()) ? adminEmailOverride : companyEmail;
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

    public void notifyExpirationReminder(Commande commande) {
        String clientName = commande.getUser().getFullName() != null ? commande.getUser().getFullName() : commande.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre service \"" + commande.getOffre().getNom() + "\" expire le "
                + commande.getDateExpiration().format(DATE_FMT) + ".\n\n"
                + "Contactez-nous pour le renouveler avant cette date.\n\n"
                + "L'équipe CactusDS";
        send(commande.getUser().getEmail(), "Votre service expire bientôt", body);
    }

    public void notifyEmailVerification(User user, String verifyLink) {
        String clientName = user.getFullName() != null ? user.getFullName() : user.getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Merci de votre inscription sur CactusDS. Confirmez votre adresse email en cliquant sur le lien suivant (valable 24 heures) :\n"
                + verifyLink + "\n\n"
                + "Si vous n'êtes pas à l'origine de cette inscription, vous pouvez ignorer cet email.\n\n"
                + "L'équipe CactusDS";
        send(user.getEmail(), "Confirmez votre adresse email", body);
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
    public void notifyServiceExpired(Commande commande) {
        String clientName = commande.getUser().getFullName() != null ? commande.getUser().getFullName() : commande.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre service \"" + commande.getOffre().getNom() + "\" a expiré le "
                + commande.getDateExpiration().format(DATE_FMT) + ".\n\n"
                + "Contactez-nous pour le renouveler et le réactiver.\n\n"
                + "L'équipe CactusDS";
        send(commande.getUser().getEmail(), "Votre service a expiré", body);
    }
    public void notifyFactureOverdueReminder(Facture facture) {
        String clientName = facture.getUser().getFullName() != null ? facture.getUser().getFullName() : facture.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre facture " + facture.getNumero() + " d'un montant de " + facture.getMontantTotal() + " MAD, "
                + "émise le " + facture.getDateEmission().format(DATE_FMT) + ", est toujours impayée à ce jour.\n\n"
                + "Merci de régulariser votre situation depuis votre espace client afin d'éviter une suspension de vos services.\n\n"
                + "L'équipe CactusDS";
        send(facture.getUser().getEmail(), "Rappel : facture " + facture.getNumero() + " impayée", body);
    }

    /**
     * The client only CLAIMED to have paid by bank transfer; this does not mean the money has
     * actually arrived. The admin must check the real bank statement before marking the invoice
     * PAYEE in the admin Facturation screen — this email is a prompt to do that, not proof.
     */
    public void notifyPaiementDeclare(Facture facture) {
        String clientName = facture.getUser().getFullName() != null ? facture.getUser().getFullName() : facture.getUser().getEmail();
        String body = "Le client " + clientName + " (" + facture.getUser().getEmail() + ") indique avoir réglé "
                + "la facture " + facture.getNumero() + " (" + facture.getMontantTotal() + " MAD) par virement bancaire.\n\n"
                + "Vérifiez la réception du virement sur le relevé bancaire avant de marquer cette facture "
                + "comme payée dans l'espace admin — cette déclaration client n'est pas une preuve de paiement.\n\n"
                + "CactusDS";
        send(adminEmail(), "Virement déclaré : facture " + facture.getNumero(), body);
    }

    public void notifyCommandeSuspended(Commande commande) {
        String clientName = commande.getUser().getFullName() != null ? commande.getUser().getFullName() : commande.getUser().getEmail();
        String body = "Bonjour " + clientName + ",\n\n"
                + "Votre service \"" + commande.getOffre().getNom() + "\" a été suspendu suite à une facture restée impayée.\n\n"
                + "Contactez-nous ou réglez votre facture depuis votre espace client pour le réactiver.\n\n"
                + "L'équipe CactusDS";
        send(commande.getUser().getEmail(), "Votre service a été suspendu", body);
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