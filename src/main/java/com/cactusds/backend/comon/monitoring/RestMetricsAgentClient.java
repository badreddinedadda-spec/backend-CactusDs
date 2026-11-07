package com.cactusds.backend.comon.monitoring;

import com.cactusds.backend.dto.ServiceMetricsResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class RestMetricsAgentClient implements MetricsAgentClient {

    private final RestClient restClient;

    public RestMetricsAgentClient(MonitoringProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(props.getRequestTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ServiceMetricsResponse fetch(URI agentBaseUri) {
        String scheme = agentBaseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new InvalidAgentPayloadException("unsupported agent URL scheme");
        }
        URI target = URI.create(agentBaseUri.toString().replaceAll("/+$", "") + "/metrics");
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(target)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            // network error, timeout, non-2xx answer or unreadable body: the server is not usable right now
            throw new AgentUnreachableException("agent unreachable (" + e.getClass().getSimpleName() + ")");
        }
        return AgentPayloadParser.parse(body);
    }
}
