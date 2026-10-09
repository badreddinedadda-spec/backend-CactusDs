package com.cactusds.backend.comon.controller;

import com.cactusds.backend.comon.monitoring.AgentUrlPolicy;
import com.cactusds.backend.controller.AdminMonitoringController;
import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.model.ServiceMonitoringTarget;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.CommandeRepository;
import com.cactusds.backend.repository.ServiceMonitoringTargetRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class AdminMonitoringControllerTest {

    private static final String URL = "/api/admin/commandes/4/monitoring";

    private final CommandeRepository commandes = mock(CommandeRepository.class);
    private final ServiceMonitoringTargetRepository targets = mock(ServiceMonitoringTargetRepository.class);
    private final CurrentUserResolver users = mock(CurrentUserResolver.class);
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("admin@cactusds.test", null, List.of());
    private MockMvc mvc;

    private static Commande order(CategorieOffre.Famille famille) {
        Offre offre = Offre.builder().categorie(CategorieOffre.builder().famille(famille).build()).build();
        return Commande.builder().id(4L).offre(offre).build();
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new AdminMonitoringController(commandes, targets, new AgentUrlPolicy(), users)).build();
        when(users.resolve(any())).thenReturn(User.builder().id(1L).email("admin@cactusds.test").build());
        when(commandes.findById(4L)).thenReturn(Optional.of(order(CategorieOffre.Famille.SERVEUR_CLOUD)));
        when(targets.save(any(ServiceMonitoringTarget.class))).thenAnswer(i -> i.getArgument(0));
    }

    private MvcResult putJson(String json) throws Exception {
        return mvc.perform(put(URL).principal(auth).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    @Test
    void attachesAnAgentToAVpsOrderAndNormalisesTheUrl() throws Exception {
        MvcResult r = putJson("{\"agentUrl\":\"http://10.0.0.12:9100/\",\"enabled\":true}");
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("\"agentUrl\":\"http://10.0.0.12:9100\""));
        verify(targets).save(any(ServiceMonitoringTarget.class));
    }

    @Test
    void enabledDefaultsToTrue() throws Exception {
        MvcResult r = putJson("{\"agentUrl\":\"http://10.0.0.12:9100\"}");
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("\"enabled\":true"));
    }

    @Test
    void updatesTheExistingTargetInsteadOfCreatingASecondOne() throws Exception {
        ServiceMonitoringTarget existing = ServiceMonitoringTarget.builder().agentUrl("http://10.0.0.99:9100").build();
        when(targets.findByCommandeId(4L)).thenReturn(Optional.of(existing));
        putJson("{\"agentUrl\":\"http://10.0.0.12:9100\",\"enabled\":false}");
        assertEquals("http://10.0.0.12:9100", existing.getAgentUrl());
        assertEquals(Boolean.FALSE, existing.getEnabled());
    }

    @Test
    void refusesToMonitorAnythingThatIsNotAVpsOrCloudOrder() throws Exception {
        when(commandes.findById(4L)).thenReturn(Optional.of(order(CategorieOffre.Famille.SITE_WEB)));
        assertEquals(400, putJson("{\"agentUrl\":\"http://10.0.0.12:9100\"}").getResponse().getStatus());
        verify(targets, never()).save(any());
    }

    @Test
    void refusesForbiddenAddressesAndNeverSaves() throws Exception {
        assertEquals(400, putJson("{\"agentUrl\":\"http://169.254.169.254\"}").getResponse().getStatus());
        assertEquals(400, putJson("{\"agentUrl\":\"http://user:pw@10.0.0.12:9100\"}").getResponse().getStatus());
        verify(targets, never()).save(any());
    }

    @Test
    void refusesABlankUrl() throws Exception {
        assertEquals(400, putJson("{\"agentUrl\":\"  \"}").getResponse().getStatus());
        verify(targets, never()).save(any());
    }

    @Test
    void refusesToShareOneAgentBetweenTwoOrders_soTwoClientsNeverSeeTheSameServer() throws Exception {
        when(targets.existsByAgentUrlAndCommandeIdNot("http://10.0.0.12:9100", 4L)).thenReturn(true);
        assertEquals(409, putJson("{\"agentUrl\":\"http://10.0.0.12:9100\"}").getResponse().getStatus());
        verify(targets, never()).save(any());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        when(commandes.findById(4L)).thenReturn(Optional.empty());
        assertEquals(404, putJson("{\"agentUrl\":\"http://10.0.0.12:9100\"}").getResponse().getStatus());
    }

    @Test
    void getAndDeleteReturn404WhenThereIsNoTarget() throws Exception {
        when(targets.findByCommandeId(4L)).thenReturn(Optional.empty());
        when(targets.deleteByCommandeId(4L)).thenReturn(0L);
        assertEquals(404, mvc.perform(get(URL).principal(auth)).andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(delete(URL).principal(auth)).andReturn().getResponse().getStatus());
    }

    @Test
    void getReturnsTheTargetAndDeleteRemovesIt() throws Exception {
        ServiceMonitoringTarget t = ServiceMonitoringTarget.builder().agentUrl("http://10.0.0.12:9100").build();
        when(targets.findByCommandeId(4L)).thenReturn(Optional.of(t));
        when(targets.deleteByCommandeId(4L)).thenReturn(1L);
        MvcResult g = mvc.perform(get(URL).principal(auth)).andReturn();
        assertEquals(200, g.getResponse().getStatus());
        assertTrue(g.getResponse().getContentAsString().contains("http://10.0.0.12:9100"));
        assertEquals(204, mvc.perform(delete(URL).principal(auth)).andReturn().getResponse().getStatus());
    }
}