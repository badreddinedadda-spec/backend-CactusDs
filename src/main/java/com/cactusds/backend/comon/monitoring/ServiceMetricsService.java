package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.repository.CommandeRepository;
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
    private final MetricsAgentClient agentClient;
    private final MonitoringProperties props;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceMetricsService(CommandeRepository commandeRepository, MetricsAgentClient agentClient,
                                 MonitoringProperties props) {
        this(commandeRepository, agentClient, props, Clock.systemUTC());
    }

    ServiceMetricsService(CommandeRepository commandeRepository, MetricsAgentClient agentClient,
                          MonitoringProperties props, Clock clock) {
        this.commandeRepository = commandeRepository;
        this.agentClient = agentClient;
        this.props = props;
        this.clock = clock;
    }

    public MetricsResult metricsFor(Long commandeId, Long userId) {
        Optional<Commande> owned = commandeRepository.findOwnedWithOffre(commandeId, userId);
        if (owned.isEmpty() || !isMonitorable(owned.get())) {
            return MetricsResult.notFound();
        }
        String base = props.getAgents().get(commandeId);
        if (base == null || base.isBlank()) {
            return MetricsResult.notFound();
        }

        long now = clock.millis();
        Cached hit = cache.get(commandeId);
        if (hit != null && now - hit.atMs() < props.getCacheTtlMs()) {
            return MetricsResult.live(hit.snapshot());
        }
        try {
            ServiceMetricsResponse fresh = agentClient.fetch(URI.create(base.trim()));
            cache.put(commandeId, new Cached(fresh, now));
            return MetricsResult.live(fresh);
        } catch (AgentUnreachableException | InvalidAgentPayloadException e) {
            log.warn("Metrics unavailable for order {}: {}", commandeId, e.getMessage());
            return MetricsResult.unreachable();
        }
    }
    static boolean isMonitorable(Commande c) {
        boolean statusOk = c.getStatut() == Commande.Statut.ACTIVE || c.getStatut() == Commande.Statut.SUSPENDUE;
        boolean cloud = c.getOffre() != null
                && c.getOffre().getCategorie() != null
                && c.getOffre().getCategorie().getFamille() == CategorieOffre.Famille.SERVEUR_CLOUD;
        return statusOk && cloud;
    }
}
