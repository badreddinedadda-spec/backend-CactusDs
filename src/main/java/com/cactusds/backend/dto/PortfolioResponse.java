package com.cactusds.backend.dto;

import com.cactusds.backend.model.Portfolio;
import java.time.LocalDate;

public record PortfolioResponse(
        Long id, String titre, String description, String imageUrl, String lienUrl,
        String categorie, String client, LocalDate dateProjet, Boolean enVedette
) {
    public static PortfolioResponse from(Portfolio p) {
        return new PortfolioResponse(
                p.getId(), p.getTitre(), p.getDescription(), p.getImageUrl(), p.getLienUrl(),
                p.getCategorie().name(), p.getClient(), p.getDateProjet(), p.getEnVedette()
        );
    }
}