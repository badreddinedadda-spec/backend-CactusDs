package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;

import java.net.URI;
public interface MetricsAgentClient {
    ServiceMetricsResponse fetch(URI agentBaseUri);
}
