package com.cactusds.backend.controller;

import com.cactusds.backend.comon.monitoring.AgentUrlPolicy;
import com.cactusds.backend.dto.MonitoringTargetRequest;
import com.cactusds.backend.dto.MonitoringTargetResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.ServiceMonitoringTarget;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.ServiceMonitoringTargetRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/commandes/{commandeId}/monitoring")
public class AdminMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(AdminMonitoringController.class);

    private final CommandeRepository commandeRepository;
    private final ServiceMonitoringTargetRepository targetRepository;
    private final AgentUrlPolicy urlPolicy;
    private final CurrentUserResolver currentUserResolver;

    public AdminMonitoringController(CommandeRepository commandeRepository,
                                     ServiceMonitoringTargetRepository targetRepository,
                                     AgentUrlPolicy urlPolicy,
                                     CurrentUserResolver currentUserResolver) {
        this.commandeRepository = commandeRepository;
        this.targetRepository = targetRepository;
        this.urlPolicy = urlPolicy;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping
    public MonitoringTargetResponse get(@PathVariable Long commandeId) {
        return targetRepository.findByCommandeId(commandeId)
                .map(t -> MonitoringTargetResponse.from(commandeId, t))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No monitoring target for this order."));
    }

    @PutMapping
    @Transactional
    public MonitoringTargetResponse put(@PathVariable Long commandeId,
                                        @Valid @RequestBody MonitoringTargetRequest req,
                                        Authentication authentication) {
        User admin = currentUserResolver.resolve(authentication);
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));
        if (commande.getOffre().getCategorie().getFamille() != CategorieOffre.Famille.SERVEUR_CLOUD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only VPS / cloud orders can be monitored.");
        }
        String url;
        try {
            url = urlPolicy.normalize(req.agentUrl());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (targetRepository.existsByAgentUrlAndCommandeIdNot(url, commandeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This agent is already attached to another order.");
        }
        ServiceMonitoringTarget target = targetRepository.findByCommandeId(commandeId)
                .orElseGet(() -> ServiceMonitoringTarget.builder().commande(commande).build());
        target.setAgentUrl(url);
        target.setEnabled(req.enabled() == null || req.enabled());
        target.setUpdatedAt(LocalDateTime.now());
        ServiceMonitoringTarget saved = targetRepository.save(target);
        log.info("Monitoring target set for order {} by admin {} (enabled={})", commandeId, admin.getId(), saved.getEnabled());
        return MonitoringTargetResponse.from(commandeId, saved);
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long commandeId, Authentication authentication) {
        User admin = currentUserResolver.resolve(authentication);
        if (targetRepository.deleteByCommandeId(commandeId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No monitoring target for this order.");
        }
        log.info("Monitoring target removed for order {} by admin {}", commandeId, admin.getId());
        return ResponseEntity.noContent().build();
    }
}