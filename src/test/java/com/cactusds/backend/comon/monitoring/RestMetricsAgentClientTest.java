package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestMetricsAgentClientTest {

    private static final String AGENT_JSON = """
            {"label":"srv","hostname":"vps-internal","platform":"linux · x64","cores":2,"uptime":90000,"load1":0.5,
             "memTotal":8589934592,"disk":{"total":100000,"used":42000,"device":"/dev/sda1"},"intervalMs":2000,
             "now":1700000002000,
             "samples":[{"t":1700000000000,"cpu":40,"mem":30,"rx":100,"tx":50},
                        {"t":1700000002000,"cpu":42.5,"mem":34,"rx":null,"tx":null}]}
            """;

    private HttpServer server;
    private final MonitoringProperties props = new MonitoringProperties();

    private URI start(int status, String body, long delayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/metrics", exchange -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeEach
    void shortTimeouts() {
        props.setConnectTimeoutMs(500);
        props.setRequestTimeoutMs(400);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsARealAgentAnswerAndKeepsOnlyTheWhitelistedFields() throws IOException {
        ServiceMetricsResponse r = new RestMetricsAgentClient(props).fetch(start(200, AGENT_JSON, 0));
        assertEquals("linux · x64", r.platform());
        assertEquals(2, r.cores());
        assertEquals(42_000L, r.disk().used());
        assertEquals(2, r.samples().size());
        assertEquals(100L, r.samples().get(0).rx());
        assertNull(r.samples().get(1).rx());
    }

    @Test
    void aTrailingSlashInTheConfiguredUrlIsHarmless() throws IOException {
        URI base = start(200, AGENT_JSON, 0);
        assertEquals(2, new RestMetricsAgentClient(props).fetch(URI.create(base + "/")).cores());
    }

    @Test
    void serverErrorMeansUnreachable() throws IOException {
        assertThrows(AgentUnreachableException.class,
                () -> new RestMetricsAgentClient(props).fetch(start(500, "{}", 0)));
    }

    @Test
    void unreadableBodyMeansUnreachable() throws IOException {
        assertThrows(AgentUnreachableException.class,
                () -> new RestMetricsAgentClient(props).fetch(start(200, "<html>nope</html>", 0)));
    }

    @Test
    void wellFormedJsonWithWrongShapeIsRejected() throws IOException {
        assertThrows(InvalidAgentPayloadException.class,
                () -> new RestMetricsAgentClient(props).fetch(start(200, "{\"cores\":\"many\"}", 0)));
    }

    @Test
    void aSlowAgentTimesOut() throws IOException {
        URI base = start(200, AGENT_JSON, 1500);
        assertThrows(AgentUnreachableException.class, () -> new RestMetricsAgentClient(props).fetch(base));
    }

    @Test
    void aClosedPortMeansUnreachable() throws IOException {
        int closed;
        try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            closed = s.getLocalPort();
        }
        URI base = URI.create("http://127.0.0.1:" + closed);
        assertThrows(AgentUnreachableException.class, () -> new RestMetricsAgentClient(props).fetch(base));
    }

    @Test
    void onlyHttpAndHttpsSchemesAreAccepted() {
        assertThrows(InvalidAgentPayloadException.class,
                () -> new RestMetricsAgentClient(props).fetch(URI.create("file:///etc/passwd")));
        assertThrows(InvalidAgentPayloadException.class,
                () -> new RestMetricsAgentClient(props).fetch(URI.create("ftp://10.0.0.1")));
    }
}
