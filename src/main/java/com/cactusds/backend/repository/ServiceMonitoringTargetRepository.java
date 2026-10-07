package com.cactusds.backend.repository;

import com.cactusds.backend.model.ServiceMonitoringTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceMonitoringTargetRepository extends JpaRepository<ServiceMonitoringTarget, Long> {
    Optional<ServiceMonitoringTarget> findByCommandeId(Long commandeId);
    boolean existsByAgentUrlAndCommandeIdNot(String agentUrl, Long commandeId);
    long deleteByCommandeId(Long commandeId);
}