package com.cactusds.backend.dto;
import com.cactusds.backend.model.CategorieOffre;
import jakarta.validation.constraints.NotBlank;

public record CategorieOffreRequest(
        @NotBlank String nom,
        @NotBlank String slug,
        String description,
        String icone,
        CategorieOffre.Famille famille,
        Integer ordreAffichage,
        Boolean actif
) {}