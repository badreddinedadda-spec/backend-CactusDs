package com.cactusds.backend.dto;

import com.cactusds.backend.model.Ticket;
import java.time.LocalDateTime;

public record TicketResponse(
        Long id, String categorie, String sujet, String message, String reponseAdmin,
        String statut, String priorite, LocalDateTime createdAt, LocalDateTime updatedAt, String clientEmail
) {
    public static TicketResponse from(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getCategorie().name(), t.getSujet(), t.getMessage(), t.getReponseAdmin(),
                t.getStatut().name(), t.getPriorite().name(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getUser().getEmail()
        );
    }
}