package com.cactusds.backend.dto;

import com.cactusds.backend.model.Portfolio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PortfolioRequest(
        @NotBlank String titre,
        String description,
        String imageUrl,
        String lienUrl,
        @NotNull Portfolio.Categorie categorie,
        String client,
        LocalDate dateProjet,
        Boolean enVedette,
        Integer ordre,
        Boolean actif
) {

}