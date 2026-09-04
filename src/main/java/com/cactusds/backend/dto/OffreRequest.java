package com.cactusds.backend.dto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record OffreRequest(
        @NotNull Long categorieId,
        @NotBlank String nom,
        String description,
        @NotNull BigDecimal prixMensuel,
        BigDecimal prixAnnuel,
        @NotNull @Min(1) Integer espaceDisqueGo,
        Integer bandePassanteGo,
        Integer nbDomaines,
        Integer nbEmails,
        Boolean sslInclus,
        Boolean actif,
        Integer ordreAffichage,
        Map<String, String> specifications
) {
}
