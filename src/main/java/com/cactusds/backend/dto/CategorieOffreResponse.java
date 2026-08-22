package com.cactusds.backend.dto;

import com.cactusds.backend.model.CategorieOffre;

public record CategorieOffreResponse(Long id, String nom, String slug, String description, String icone, String famille) {
    public static CategorieOffreResponse from(CategorieOffre c) {
        return new CategorieOffreResponse(c.getId(), c.getNom(), c.getSlug(), c.getDescription(), c.getIcone(), c.getFamille().name());
    }
}