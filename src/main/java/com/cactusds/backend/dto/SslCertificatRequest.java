package com.cactusds.backend.dto;

import com.cactusds.backend.model.SslCertificat;
import java.time.LocalDate;

public record SslCertificatRequest(SslCertificat.Type type, LocalDate dateExpiration, SslCertificat.Statut statut) {}