package com.cactusds.backend.dto;

import com.cactusds.backend.model.Ticket;
import jakarta.validation.constraints.NotBlank;

public record TicketRequest(Ticket.Categorie categorie, @NotBlank String sujet, @NotBlank String message) {}