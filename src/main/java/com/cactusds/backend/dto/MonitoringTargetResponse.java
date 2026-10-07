package com.cactusds.backend.dto;

import com.cactusds.backend.model.ServiceMonitoringTarget;

import java.time.LocalDateTime;

public record MonitoringTargetResponse(Long commandeId, String agentUrl, boolean enabled, LocalDateTime updatedAt) {
    public static MonitoringTargetResponse from(Long commandeId, ServiceMonitoringTarget t) {
        return new MonitoringTargetResponse(commandeId, t.getAgentUrl(), Boolean.TRUE.equals(t.getEnabled()),
                t.getUpdatedAt());
    }
}