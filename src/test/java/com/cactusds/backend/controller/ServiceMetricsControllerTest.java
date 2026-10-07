package com.cactusds.backend.controller;

import com.cactusds.backend.comon.monitoring.MetricsResult;
import com.cactusds.backend.comon.monitoring.ServiceMetricsService;
import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ServiceMetricsControllerTest {

    private final ServiceMetricsService service = mock(ServiceMetricsService.class);
    private final CurrentUserResolver users = mock(CurrentUserResolver.class);
    private MockMvc mvc;
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("client@cactusds.test", null, List.of());

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ServiceMetricsController(service, users)).build();
        when(users.resolve(any())).thenReturn(User.builder().id(7L).email("client@cactusds.test").build());
    }

    @Test
    void liveMetricsAreReturnedWithNoStore() throws Exception {
        ServiceMetricsResponse snap = new ServiceMetricsResponse("linux", "Ubuntu 24.04", 2, 10, 0.5, 100, null, 2000, 5, List.of());
        when(service.metricsFor(4L, 7L)).thenReturn(MetricsResult.live(snap));
        MvcResult r = mvc.perform(get("/api/client/commandes/4/metrics").principal(auth)).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"));
        assertTrue(r.getResponse().getContentAsString().contains("\"cores\":2"));
    }

    @Test
    void usesTheAuthenticatedUsersIdNeverAnIdFromTheRequest() throws Exception {
        when(service.metricsFor(4L, 7L)).thenReturn(MetricsResult.notFound());
        mvc.perform(get("/api/client/commandes/4/metrics?userId=999").principal(auth)).andReturn();
        verify(service).metricsFor(4L, 7L);
    }

    @Test
    void notFoundHasNoBodyAndNoStore() throws Exception {
        when(service.metricsFor(4L, 7L)).thenReturn(MetricsResult.notFound());
        MvcResult r = mvc.perform(get("/api/client/commandes/4/metrics").principal(auth)).andReturn();
        assertEquals(404, r.getResponse().getStatus());
        assertEquals("", r.getResponse().getContentAsString());
        assertTrue(r.getResponse().getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    void unreachableAgentIsABadGateway() throws Exception {
        when(service.metricsFor(4L, 7L)).thenReturn(MetricsResult.unreachable());
        MvcResult r = mvc.perform(get("/api/client/commandes/4/metrics").principal(auth)).andReturn();
        assertEquals(502, r.getResponse().getStatus());
        assertNull(r.getResolvedException());
    }
}
