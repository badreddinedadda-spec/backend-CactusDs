package com.cactusds.backend.comon.monitoring;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.monitoring")
public class MonitoringProperties {

    private Map<Long, String> agents = new HashMap<>();
    private long cacheTtlMs = 1500;
    private int connectTimeoutMs = 1000;
    private int requestTimeoutMs = 3000;

    @PostConstruct
    void validate() {
        agents.forEach((orderId, url) -> {
            URI uri;
            try {
                uri = URI.create(url.trim());
            } catch (RuntimeException e) {
                throw new IllegalStateException("app.monitoring.agents[" + orderId + "] is not a valid URL");
            }
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!httpScheme || uri.getHost() == null) {
                throw new IllegalStateException("app.monitoring.agents[" + orderId + "] must be an http(s) URL with a host");
            }
        });
    }

    public Map<Long, String> getAgents() {
        return agents;
    }
    public void setAgents(Map<Long, String> agents) {
        this.agents = agents == null ? new HashMap<>() : agents;
    }
    public long getCacheTtlMs() {
        return cacheTtlMs;
    }
    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }
    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }
    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
