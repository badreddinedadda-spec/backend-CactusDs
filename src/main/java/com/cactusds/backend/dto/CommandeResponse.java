package com.cactusds.backend.dto;
import com.cactusds.backend.model.Commande;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
public record CommandeResponse(Long id, String offreNom, String statut, String duree,
                               BigDecimal prixTotal, LocalDate dateDebut, LocalDate dateExpiration,
                               LocalDateTime createdAt, String clientEmail) {
    public static CommandeResponse from(Commande c) {
        return new CommandeResponse(
                c.getId(), c.getOffre().getNom(), c.getStatut().name(), c.getDuree().name(),
                c.getPrixTotal(), c.getDateDebut(), c.getDateExpiration(),
                c.getCreatedAt(), c.getUser().getEmail()
        );
    }
}
