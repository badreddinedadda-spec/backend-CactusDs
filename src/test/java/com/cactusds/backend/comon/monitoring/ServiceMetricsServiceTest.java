package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.model.ServiceMonitoringTarget;
import com.cactusds.backend.repository.ServiceMonitoringTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class ServiceMetricsServiceTest {

    private static final long ORDER = 4L;
    private static final long USER = 7L;

    private static class TestClock extends Clock {
        long ms = 1_000_000L;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(ms); }
    }

    private final CommandeRepository repo = mock(CommandeRepository.class);
    private final ServiceMonitoringTargetRepository targets = mock(ServiceMonitoringTargetRepository.class);
    private final MetricsAgentClient agent = mock(MetricsAgentClient.class);
    private final MonitoringProperties props = new MonitoringProperties();
    private final TestClock clock = new TestClock();
    private final ServiceMetricsResponse snapshot =
            new ServiceMetricsResponse("linux", null, 1, 1, 0.1, 1, null, 2000, 1, List.of());
    private ServiceMetricsService service;

    private static Commande order(Commande.Statut statut, CategorieOffre.Famille famille) {
        Offre offre = Offre.builder().categorie(CategorieOffre.builder().famille(famille).build()).build();
        return Commande.builder().id(ORDER).statut(statut).offre(offre).build();
    }

    @BeforeEach
    void setUp() {
        props.getAgents().put(ORDER, "http://10.0.0.12:9100");
        service = new ServiceMetricsService(repo, targets, agent, props, clock);
        when(agent.fetch(any(URI.class))).thenReturn(snapshot);
    }

    private void owns(Commande c) {
        when(repo.findOwnedWithOffre(ORDER, USER)).thenReturn(Optional.of(c));
    }
    private static ServiceMonitoringTarget target(String url, boolean enabled) {
        return ServiceMonitoringTarget.builder().agentUrl(url).enabled(enabled).build();
    }

    @Test
    void theDatabaseTargetWinsOverTheConfigFallback() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        when(targets.findByCommandeId(ORDER)).thenReturn(Optional.of(target("http://10.9.9.9:9100", true)));
        assertEquals(MetricsResult.Status.LIVE, service.metricsFor(ORDER, USER).status());
        verify(agent).fetch(URI.create("http://10.9.9.9:9100"));
    }

    @Test
    void aDisabledTargetMeansNoMetricsEvenIfTheConfigStillHasAnEntry() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        when(targets.findByCommandeId(ORDER)).thenReturn(Optional.of(target("http://10.9.9.9:9100", false)));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        verify(agent, never()).fetch(any());
    }

    @Test
    void withoutADatabaseRowTheConfigEntryIsUsed() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.LIVE, service.metricsFor(ORDER, USER).status());
        verify(agent).fetch(URI.create("http://10.0.0.12:9100"));
    }

    @Test
    void disablingATargetTakesEffectImmediatelyEvenWhileASnapshotIsCached() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.LIVE, service.metricsFor(ORDER, USER).status());
        when(targets.findByCommandeId(ORDER)).thenReturn(Optional.of(target("http://10.0.0.12:9100", false)));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
    }
    @Test
    void returnsLiveMetricsToTheOwnerOfAnActiveVps() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        MetricsResult r = service.metricsFor(ORDER, USER);
        assertEquals(MetricsResult.Status.LIVE, r.status());
        assertSame(snapshot, r.snapshot());
    }

    @Test
    void suspendedVpsIsStillMonitorable() {
        owns(order(Commande.Statut.SUSPENDUE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.LIVE, service.metricsFor(ORDER, USER).status());
    }

    @Test
    void aServiceThatIsNotTheCallers_isNotFound_andTheAgentIsNeverContacted() {
        when(repo.findOwnedWithOffre(ORDER, USER)).thenReturn(Optional.empty());
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        verify(agent, never()).fetch(any());
    }

    @Test
    void nonVpsOrNonActiveServicesAreNotFound() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SITE_WEB));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        owns(order(Commande.Statut.EN_ATTENTE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        owns(order(Commande.Statut.EXPIREE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        verify(agent, never()).fetch(any());
    }

    @Test
    void noAgentConfiguredMeansNotFound_soTheConsoleShowsNotConnected() {
        props.getAgents().clear();
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, USER).status());
        verify(agent, never()).fetch(any());
    }

    @Test
    void agentFailuresBecomeUnreachable() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        doThrow(new AgentUnreachableException("down")).when(agent).fetch(any(URI.class));
        assertEquals(MetricsResult.Status.UNREACHABLE, service.metricsFor(ORDER, USER).status());
        doThrow(new InvalidAgentPayloadException("bad")).when(agent).fetch(any(URI.class));
        assertEquals(MetricsResult.Status.UNREACHABLE, service.metricsFor(ORDER, USER).status());
    }

    @Test
    void cacheProtectsTheAgentButOwnershipIsCheckedOnEveryRequest() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        service.metricsFor(ORDER, USER);
        clock.ms += 1000; // inside the 1500 ms TTL
        service.metricsFor(ORDER, USER);
        verify(agent, times(1)).fetch(any());
        verify(repo, times(2)).findOwnedWithOffre(ORDER, USER);
        clock.ms += 1000; // TTL elapsed
        service.metricsFor(ORDER, USER);
        verify(agent, times(2)).fetch(any());
    }

    @Test
    void aCachedSnapshotIsNeverServedToSomeoneWhoDoesNotOwnTheService() {
        owns(order(Commande.Statut.ACTIVE, CategorieOffre.Famille.SERVEUR_CLOUD));
        assertEquals(MetricsResult.Status.LIVE, service.metricsFor(ORDER, USER).status());
        when(repo.findOwnedWithOffre(ORDER, 99L)).thenReturn(Optional.empty());
        assertEquals(MetricsResult.Status.NOT_FOUND, service.metricsFor(ORDER, 99L).status());
    }
}
