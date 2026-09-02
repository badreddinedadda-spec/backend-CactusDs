package com.cactusds.backend.dto;

import com.cactusds.backend.model.Ticket;
import jakarta.validation.constraints.NotBlank;

public record TicketReplyRequest(@NotBlank String reponseAdmin, Ticket.Statut statut , Ticket.Priorite priorite) {}