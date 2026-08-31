package com.cactusds.backend.dto;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Facture;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FactureResponse(Long id, String numero, String clientEmail, String clientNom,
                              LocalDate periodeDebut, LocalDate periodeFin,
                              BigDecimal montantTotal, String statut, LocalDateTime dateEmission,
                              Boolean relanceEnvoyee, List<Ligne> lignes) {

    public record Ligne(Long commandeId, String offreNom, String duree, BigDecimal prixTotal) {}

    public static FactureResponse from(Facture f, List<Commande> commandes) {
        List<Ligne> lignes = commandes.stream()
                .map(c -> new Ligne(c.getId(), c.getOffre().getNom(), c.getDuree().name(), c.getPrixTotal()))
                .toList();
        return new FactureResponse(
                f.getId(), f.getNumero(), f.getUser().getEmail(), f.getUser().getFullName(),
                f.getPeriodeDebut(), f.getPeriodeFin(), f.getMontantTotal(), f.getStatut().name(),
                f.getDateEmission(), f.getRelanceEnvoyee(), lignes
        );
    }
}