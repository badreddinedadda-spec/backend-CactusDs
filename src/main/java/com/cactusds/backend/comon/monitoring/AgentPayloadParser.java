package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class AgentPayloadParser {
    static final int MAX_SAMPLES = 450;
    private static final int MAX_ACCEPTED_SAMPLES = 2000;

    private AgentPayloadParser() {
    }

    static ServiceMetricsResponse parse(Map<?, ?> p) {
        if (p == null) {
            throw invalid("empty payload");
        }
        int cores = (int) integer(p, "cores", 1, 4096);
        long uptime = integer(p, "uptime", 0, Long.MAX_VALUE);
        double load1 = number(p, "load1", 0, 1_000_000);
        long memTotal = integer(p, "memTotal", 1, Long.MAX_VALUE);
        long intervalMs = integer(p, "intervalMs", 100, 3_600_000);
        long now = integer(p, "now", 0, Long.MAX_VALUE);
        String platform = text(p.get("platform"), 64);
        if (platform == null) {
            throw invalid("platform");
        }
        String os = text(p.get("os"), 100);

        ServiceMetricsResponse.Disk disk = null;
        Object d = p.get("disk");
        if (d != null) {
            if (!(d instanceof Map<?, ?> dm)) {
                throw invalid("disk");
            }
            long total = integer(dm, "total", 1, Long.MAX_VALUE);
            long used = integer(dm, "used", 0, total);
            disk = new ServiceMetricsResponse.Disk(total, used);
        }

        if (!(p.get("samples") instanceof List<?> raw) || raw.size() > MAX_ACCEPTED_SAMPLES) {
            throw invalid("samples");
        }
        List<?> kept = raw.size() > MAX_SAMPLES ? raw.subList(raw.size() - MAX_SAMPLES, raw.size()) : raw;
        List<ServiceMetricsResponse.Sample> samples = new ArrayList<>(kept.size());
        for (Object o : kept) {
            if (!(o instanceof Map<?, ?> s)) {
                throw invalid("sample");
            }
            samples.add(new ServiceMetricsResponse.Sample(
                    integer(s, "t", 0, Long.MAX_VALUE),
                    number(s, "cpu", 0, 100),
                    number(s, "mem", 0, 100),
                    optionalInteger(s, "rx"),
                    optionalInteger(s, "tx")));
        }
        return new ServiceMetricsResponse(platform, os, cores, uptime, load1, memTotal, disk, intervalMs, now,
                List.copyOf(samples));
    }

    private static double number(Map<?, ?> m, String key, double min, double max) {
        Object v = m.get(key);
        if (!(v instanceof Number n)) {
            throw invalid(key);
        }
        double d = n.doubleValue();
        if (!Double.isFinite(d) || d < min || d > max) {
            throw invalid(key);
        }
        return d;
    }

    private static long integer(Map<?, ?> m, String key, long min, long max) {
        double d = number(m, key, min, max);
        if (d != Math.rint(d)) {
            throw invalid(key);
        }
        return ((Number) m.get(key)).longValue();
    }

    private static Long optionalInteger(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : integer(m, key, 0, Long.MAX_VALUE);
    }
    private static String text(Object v, int maxLength) {
        if (!(v instanceof String s)) {
            return null;
        }
        String clean = s.replaceAll("\\p{Cntrl}", "").trim();
        if (clean.isEmpty()) {
            return null;
        }
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }

    private static InvalidAgentPayloadException invalid(String field) {
        return new InvalidAgentPayloadException("invalid agent payload: " + field);
    }
}
