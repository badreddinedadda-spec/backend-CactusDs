package com.cactusds.backend.controller;

import com.cactusds.backend.comon.monitoring.MetricsResult;
import com.cactusds.backend.comon.monitoring.ServiceMetricsService;
import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.security.CurrentUserResolver;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceMetricsController {

    private final ServiceMetricsService metricsService;
    private final CurrentUserResolver currentUserResolver;

    public ServiceMetricsController(ServiceMetricsService metricsService, CurrentUserResolver currentUserResolver) {
        this.metricsService = metricsService;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/api/client/commandes/{id}/metrics")
    public ResponseEntity<ServiceMetricsResponse> metrics(@PathVariable Long id, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        MetricsResult result = metricsService.metricsFor(id, user.getId());
        CacheControl noStore = CacheControl.noStore();
        return switch (result.status()) {
            case LIVE -> ResponseEntity.ok().cacheControl(noStore).body(result.snapshot());
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(noStore).build();
            case UNREACHABLE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(noStore).build();
        };
    }
}
