package com.cactusds.backend.dto;

import com.cactusds.backend.model.Domaine;
import java.time.LocalDate;

public record DomaineResponse(
        Long id, String nom, String extension, String nomComplet, LocalDate dateExpiration,
        String statut, Boolean renouvellementAuto, String clientEmail, SslCertificatResponse sslCertificat
) {
    public static DomaineResponse from(Domaine d) {
        SslCertificatResponse ssl = d.getSslCertificat() != null ? SslCertificatResponse.from(d.getSslCertificat()) : null;
        return new DomaineResponse(
                d.getId(), d.getNom(), d.getExtension(), d.getNomComplet(), d.getDateExpiration(),
                d.getStatut().name(), d.getRenouvellementAuto(), d.getUser().getEmail(), ssl
        );
    }
}