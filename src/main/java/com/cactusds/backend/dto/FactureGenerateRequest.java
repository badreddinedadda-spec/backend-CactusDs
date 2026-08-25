package com.cactusds.backend.dto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record FactureGenerateRequest(
        @NotNull Long userId,
        @NotNull LocalDate periodeDebut,
        @NotNull LocalDate periodeFin) {
}
