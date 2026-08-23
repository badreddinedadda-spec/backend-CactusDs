package com.cactusds.backend.dto;

import com.cactusds.backend.model.SslCertificat;
import java.time.LocalDate;

public record SslCertificatResponse(Long id, String type, LocalDate dateExpiration, String statut) {
    public static SslCertificatResponse from(SslCertificat s) {
        return new SslCertificatResponse(s.getId(), s.getType().name(), s.getDateExpiration(), s.getStatut().name());
    }
}