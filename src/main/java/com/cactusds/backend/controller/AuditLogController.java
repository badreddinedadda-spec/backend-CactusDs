package com.cactusds.backend.controller;

import com.cactusds.backend.dto.AuditLogResponse;
import com.cactusds.backend.repository.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogRepository repository;

    public AuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AuditLogResponse> list() {
        return repository.findTop200ByOrderByCreatedAtDesc().stream().map(AuditLogResponse::from).toList();
    }
}