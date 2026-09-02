package com.cactusds.backend.dto;

import com.cactusds.backend.model.Ticket;
import jakarta.validation.constraints.NotNull;

public record TicketPrioriteUpdateRequest(@NotNull Ticket.Priorite priorite) {}