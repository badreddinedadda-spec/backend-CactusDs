package com.cactusds.backend.dto;
import com.cactusds.backend.model.Commande;
import jakarta.validation.constraints.NotNull;
public record StatutUpdateRequest(@NotNull Commande.Statut statut) {
}
