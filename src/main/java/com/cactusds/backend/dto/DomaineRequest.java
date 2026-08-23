package com.cactusds.backend.dto;

import com.cactusds.backend.model.Domaine;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record DomaineRequest(
        Long userId, // required on create, ignored on update
        @NotBlank String nom,
        @NotBlank String extension,
        LocalDate dateExpiration,
        Domaine.Statut statut,
        Boolean renouvellementAuto
) {}