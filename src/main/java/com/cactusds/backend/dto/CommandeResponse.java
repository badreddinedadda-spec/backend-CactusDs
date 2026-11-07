package com.cactusds.backend.dto;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record CommandeResponse(Long id, Long userId, String offreNom, String statut, String duree,
                               BigDecimal prixTotal, LocalDate dateDebut, LocalDate dateExpiration,
                               LocalDateTime createdAt, String clientEmail,
                               Long offreId, String categorieNom, String famille) {
    public static CommandeResponse from(Commande c) {
        Offre offre = c.getOffre();
        CategorieOffre categorie = offre.getCategorie();
        return new CommandeResponse(
                c.getId(), c.getUser().getId(), offre.getNom(), c.getStatut().name(), c.getDuree().name(),
                c.getPrixTotal(), c.getDateDebut(), c.getDateExpiration(),
                c.getCreatedAt(), c.getUser().getEmail(),
                offre.getId(),
                categorie == null ? null : categorie.getNom(),
                categorie == null || categorie.getFamille() == null ? null : categorie.getFamille().name()
        );
    }
}