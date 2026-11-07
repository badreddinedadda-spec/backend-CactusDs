package com.cactusds.backend.dto;

import java.util.List;

public record ServiceMetricsResponse(String platform, String os, int cores, long uptime, double load1,
                                     long memTotal, Disk disk, long intervalMs, long now, List<Sample> samples) {

    public record Disk(long total, long used) {
    }
    public record Sample(long t, double cpu, double mem, Long rx, Long tx) {
    }
}
