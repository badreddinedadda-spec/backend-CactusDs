package com.cactusds.backend.dto;
import com.cactusds.backend.model.Offre;
import java.math.BigDecimal;
import java.util.Map;
public record OffreResponse(
        Long id, String nom, String description,
        BigDecimal prixMensuel, BigDecimal prixAnnuel,
        Integer espaceDisqueGo, Integer bandePassanteGo,
        Integer nbDomaines, Integer nbEmails, Boolean sslInclus,
        Boolean actif, Long categorieId, String categorieNom,
        Map<String, String> specifications
) {
    public static OffreResponse from(Offre o) {
        return new OffreResponse(
                o.getId(), o.getNom(), o.getDescription(),
                o.getPrixMensuel(), o.getPrixAnnuel(),
                o.getEspaceDisqueGo(), o.getBandePassanteGo(),
                o.getNbDomaines(), o.getNbEmails(), o.getSslInclus(),
                o.getActif(), o.getCategorie().getId(), o.getCategorie().getNom(),
                o.getSpecifications()
        );
    }
}