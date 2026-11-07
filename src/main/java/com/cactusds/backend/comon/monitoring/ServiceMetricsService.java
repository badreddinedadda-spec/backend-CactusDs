package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.ServiceMonitoringTarget;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.ServiceMonitoringTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServiceMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ServiceMetricsService.class);

    private record Cached(ServiceMetricsResponse snapshot, long atMs) {
    }

    private final CommandeRepository commandeRepository;
    private final ServiceMonitoringTargetRepository targetRepository;
    private final MetricsAgentClient agentClient;
    private final MonitoringProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceMetricsService(CommandeRepository commandeRepository,
                                 ServiceMonitoringTargetRepository targetRepository,
                                 MetricsAgentClient agentClient, MonitoringProperties props) {
        this(commandeRepository, targetRepository, agentClient, props, Clock.systemUTC());
    }

    ServiceMetricsService(CommandeRepository commandeRepository, ServiceMonitoringTargetRepository targetRepository,
                          MetricsAgentClient agentClient, MonitoringProperties props, Clock clock) {
        this.commandeRepository = commandeRepository;
        this.targetRepository = targetRepository;
        this.agentClient = agentClient;
        this.props = props;
        this.clock = clock;
    }

    public MetricsResult metricsFor(Long commandeId, Long userId) {
        Optional<Commande> owned = commandeRepository.findOwnedWithOffre(commandeId, userId);
        if (owned.isEmpty() || !isMonitorable(owned.get())) {
            return MetricsResult.notFound();
        }
        Optional<String> agentUrl = agentUrlFor(commandeId);
        if (agentUrl.isEmpty()) {
            return MetricsResult.notFound();
        }

        long now = clock.millis();
        Cached hit = cache.get(commandeId);
        if (hit != null && now - hit.atMs() < props.getCacheTtlMs()) {
            return MetricsResult.live(hit.snapshot());
        }
        try {
            ServiceMetricsResponse fresh = agentClient.fetch(URI.create(agentUrl.get()));
            cache.put(commandeId, new Cached(fresh, now));
            return MetricsResult.live(fresh);
        } catch (AgentUnreachableException | InvalidAgentPayloadException e) {
            log.warn("Metrics unavailable for order {}: {}", commandeId, e.getMessage());
            return MetricsResult.unreachable();
        }
    }

    /** Database row wins (enabled or not); application.properties is only a bootstrap fallback when there is no row. */
    private Optional<String> agentUrlFor(Long commandeId) {
        Optional<ServiceMonitoringTarget> target = targetRepository.findByCommandeId(commandeId);
        if (target.isPresent()) {
            return Boolean.TRUE.equals(target.get().getEnabled()) ? Optional.of(target.get().getAgentUrl()) : Optional.empty();
        }
        String fallback = props.getAgents().get(commandeId);
        return fallback == null || fallback.isBlank() ? Optional.empty() : Optional.of(fallback.trim());
    }

    static boolean isMonitorable(Commande c) {
        boolean statusOk = c.getStatut() == Commande.Statut.ACTIVE || c.getStatut() == Commande.Statut.SUSPENDUE;
        boolean cloud = c.getOffre() != null
                && c.getOffre().getCategorie() != null
                && c.getOffre().getCategorie().getFamille() == CategorieOffre.Famille.SERVEUR_CLOUD;
        return statusOk && cloud;
    }
}