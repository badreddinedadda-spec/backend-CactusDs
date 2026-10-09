package com.cactusds.backend.dto;

import com.cactusds.backend.model.TicketMessage;
import java.time.LocalDateTime;
public record TicketMessageResponse(String auteur, String message, LocalDateTime createdAt) {

    public static TicketMessageResponse from(TicketMessage m) {
        return new TicketMessageResponse(m.getAuteur().name(), m.getMessage(), m.getCreatedAt());
    }
}