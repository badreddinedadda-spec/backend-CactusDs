package com.cactusds.backend.comon.audit;

import com.cactusds.backend.model.AuditLog;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;


@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(User acteur, String action, String description) {
        repository.save(AuditLog.builder()
                .action(action)
                .description(description)
                .auteurEmail(acteur.getEmail())
                .auteurNom(acteur.getFullName())
                .build());
    }
}