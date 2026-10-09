package com.cactusds.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketMessageRequest(@NotBlank @Size(max = 5000) String message) {
}
