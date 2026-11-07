package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPayloadParserTest {

    private static Map<String, Object> sample(long t, double cpu, double mem, Object rx, Object tx) {
        Map<String, Object> s = new HashMap<>();
        s.put("t", t);
        s.put("cpu", cpu);
        s.put("mem", mem);
        s.put("rx", rx);
        s.put("tx", tx);
        return s;
    }

    private static Map<String, Object> valid() {
        Map<String, Object> p = new HashMap<>();
        p.put("platform", "linux · x64");
        p.put("os", "Ubuntu 24.04.4 LTS");
        p.put("cores", 2);
        p.put("uptime", 90000);
        p.put("load1", 0.5);
        p.put("memTotal", 8_589_934_592L);
        p.put("disk", new HashMap<>(Map.of("total", 100_000L, "used", 42_000L)));
        p.put("intervalMs", 2000);
        p.put("now", 1_700_000_002_000L);
        p.put("samples", new ArrayList<>(List.of(sample(1_700_000_000_000L, 40, 30, 100, 50),
                sample(1_700_000_002_000L, 42.5, 34, 200, 80))));
        return p;
    }

    @Test
    void parsesAValidPayload() {
        ServiceMetricsResponse r = AgentPayloadParser.parse(valid());
        assertEquals("linux · x64", r.platform());
        assertEquals("Ubuntu 24.04.4 LTS", r.os());
        assertEquals(2, r.cores());
        assertEquals(42_000L, r.disk().used());
        assertEquals(2, r.samples().size());
        assertEquals(42.5, r.samples().get(1).cpu());
        assertEquals(200L, r.samples().get(1).rx());
    }

    @Test
    void copiesOnlyWhitelistedFields_noHostnameIpOrProcessesEverReachTheBrowser() {
        Map<String, Object> p = valid();
        p.put("hostname", "vps-internal-17");
        p.put("ip", "10.0.0.12");
        p.put("label", "Serveur interne");
        p.put("processes", List.of("sshd", "mysqld"));
        ((Map<String, Object>) p.get("disk")).put("device", "/dev/sda1");
        ServiceMetricsResponse r = AgentPayloadParser.parse(p);
        Set<String> exposed = Arrays.stream(ServiceMetricsResponse.class.getRecordComponents())
                .map(c -> c.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("platform", "os", "cores", "uptime", "load1", "memTotal", "disk", "intervalMs", "now",
                "samples"), exposed);
        assertEquals(2, ServiceMetricsResponse.Disk.class.getRecordComponents().length);
        assertEquals("linux · x64", r.platform());
    }

    @Test
    void osDiskAndNetworkAreOptional() {
        Map<String, Object> p = valid();
        p.remove("os");
        p.put("disk", null);
        p.put("samples", new ArrayList<>(List.of(sample(1L, 1, 1, null, null))));
        ServiceMetricsResponse r = AgentPayloadParser.parse(p);
        assertNull(r.os());
        assertNull(r.disk());
        assertNull(r.samples().get(0).rx());
        assertNull(r.samples().get(0).tx());
    }

    @Test
    void rejectsMissingFieldsAndWrongTypes() {
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(null));
        Map<String, Object> noSamples = valid();
        noSamples.remove("samples");
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(noSamples));
        Map<String, Object> stringCores = valid();
        stringCores.put("cores", "two");
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(stringCores));
        Map<String, Object> noPlatform = valid();
        noPlatform.put("platform", "   ");
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(noPlatform));
        Map<String, Object> diskNotAnObject = valid();
        diskNotAnObject.put("disk", "full");
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(diskNotAnObject));
    }

    @Test
    void rejectsImplausibleValues() {
        Map<String, Object> cpu = valid();
        cpu.put("samples", new ArrayList<>(List.of(sample(1L, 101, 10, 1, 1))));
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(cpu));

        Map<String, Object> negativeRx = valid();
        negativeRx.put("samples", new ArrayList<>(List.of(sample(1L, 10, 10, -5, 1))));
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(negativeRx));

        Map<String, Object> nan = valid();
        nan.put("load1", Double.NaN);
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(nan));

        Map<String, Object> usedAboveTotal = valid();
        usedAboveTotal.put("disk", new HashMap<>(Map.of("total", 10L, "used", 11L)));
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(usedAboveTotal));

        Map<String, Object> fractionalCores = valid();
        fractionalCores.put("cores", 1.5);
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(fractionalCores));
    }

    @Test
    void keepsOnlyTheLast450SamplesAndRejectsAbsurdPayloads() {
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            many.add(sample(i, 10, 10, 1, 1));
        }
        Map<String, Object> p = valid();
        p.put("samples", many);
        ServiceMetricsResponse r = AgentPayloadParser.parse(p);
        assertEquals(450, r.samples().size());
        assertEquals(150L, r.samples().get(0).t());

        List<Object> absurd = new ArrayList<>();
        for (int i = 0; i < 2001; i++) {
            absurd.add(sample(i, 10, 10, 1, 1));
        }
        Map<String, Object> tooMany = valid();
        tooMany.put("samples", absurd);
        assertThrows(InvalidAgentPayloadException.class, () -> AgentPayloadParser.parse(tooMany));
    }

    @Test
    void sanitizesTextFields() {
        Map<String, Object> p = valid();
        p.put("os", "Ubuntu\n\u0000 24.04 " + "x".repeat(300));
        ServiceMetricsResponse r = AgentPayloadParser.parse(p);
        assertEquals(100, r.os().length());
        assertEquals("Ubuntu 24.04 ", r.os().substring(0, 13));
    }
}
