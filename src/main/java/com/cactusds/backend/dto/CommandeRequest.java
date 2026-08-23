package com.cactusds.backend.dto;
import com.cactusds.backend.model.Commande;
import jakarta.validation.constraints.NotNull;

public record CommandeRequest( @NotNull Long offreId,
                               @NotNull Commande.Duree duree) {
}
