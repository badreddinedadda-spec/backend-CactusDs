package com.cactusds.backend.dto;
import com.cactusds.backend.comon.billing.BillingPolicy;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FactureResponse(Long id, String numero, String clientEmail, String clientNom,
                              LocalDate periodeDebut, LocalDate periodeFin,
                              BigDecimal montantTotal, String statut, LocalDateTime dateEmission,
                              Boolean relanceEnvoyee, LocalDateTime paiementDeclareAt, List<Ligne> lignes,
                              LocalDate dateEcheance, boolean enRetard, LocalDate dateSuspension) {

    public record Ligne(Long commandeId, String offreNom, String duree, BigDecimal prixTotal) {}

    public static FactureResponse from(Facture f, List<Commande> commandes) {
        return from(f, commandes, LocalDate.now());
    }

    public static FactureResponse from(Facture f, List<Commande> commandes, LocalDate today) {
        List<Ligne> lignes = commandes.stream()
                .map(c -> new Ligne(c.getId(), c.getOffre().getNom(), c.getDuree().name(), c.getPrixTotal()))
                .toList();
        LocalDate echeance = BillingPolicy.dueDateOf(f);
        boolean emise = f.getStatut() == Facture.Statut.EMISE;
        boolean enRetard = emise && echeance != null && today.isAfter(echeance);
        LocalDate suspension = emise && f.getDateEmission() != null ? BillingPolicy.suspensionDate(f.getDateEmission()) : null;
        return new FactureResponse(
                f.getId(), f.getNumero(), f.getUser().getEmail(), f.getUser().getFullName(),
                f.getPeriodeDebut(), f.getPeriodeFin(), f.getMontantTotal(), f.getStatut().name(),
                f.getDateEmission(), f.getRelanceEnvoyee(), f.getPaiementDeclareAt(), lignes,
                echeance, enRetard, suspension
        );
    }
}