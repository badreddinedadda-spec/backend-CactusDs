package com.cactusds.backend.dto;
import com.cactusds.backend.model.Facture;
import jakarta.validation.constraints.NotNull;
public record FactureStatutUpdateRequest(@NotNull Facture.Statut statut) {}