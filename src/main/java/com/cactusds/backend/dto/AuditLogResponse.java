package com.cactusds.backend.dto;

import com.cactusds.backend.model.AuditLog;
import java.time.LocalDateTime;

public record AuditLogResponse(Long id, String action, String description, String auteurEmail,
                               String auteurNom, LocalDateTime createdAt) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAction(), log.getDescription(),
                log.getAuteurEmail(), log.getAuteurNom(), log.getCreatedAt());
    }
}