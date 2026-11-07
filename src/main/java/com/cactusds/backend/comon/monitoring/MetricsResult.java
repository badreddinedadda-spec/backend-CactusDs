package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;

public record MetricsResult(Status status, ServiceMetricsResponse snapshot) {

    public enum Status { LIVE, NOT_FOUND, UNREACHABLE }

    public static MetricsResult live(ServiceMetricsResponse snapshot) {
        return new MetricsResult(Status.LIVE, snapshot);
    }
    public static MetricsResult notFound() {
        return new MetricsResult(Status.NOT_FOUND, null);
    }

    public static MetricsResult unreachable() {
        return new MetricsResult(Status.UNREACHABLE, null);
    }
}
