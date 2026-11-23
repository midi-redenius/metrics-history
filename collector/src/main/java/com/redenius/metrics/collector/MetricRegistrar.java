package com.redenius.metrics.collector;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MetricRegistrar {

    private static final Logger log = LoggerFactory.getLogger(MetricRegistrar.class);
    private static final String HOST = "collector";

    private final RestClient restClient;
    private final CollectorProperties properties;

    public MetricRegistrar(RestClient.Builder restClientBuilder, CollectorProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.metricsServiceUrl())
                .build();
    }

    public List<Long> ensureMetrics() {
        List<Long> ids = new ArrayList<>(properties.metricCount());
        for (int i = 1; i <= properties.metricCount(); i++) {
            String name = "collector.metric." + i;
            ids.add(createOrResolve(name));
        }
        log.info("Resolved {} collector metrics: {}", ids.size(), ids);
        return ids;
    }

    private Long createOrResolve(String name) {
        try {
            JsonNode response = restClient.post()
                    .uri("/api/v1/metrics")
                    .body(Map.of(
                            "name", name,
                            "unit", "n",
                            "host", HOST
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            return response.get("id").asLong();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                return findByNameAndHost(name);
            }
            throw ex;
        }
    }

    private Long findByNameAndHost(String name) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/metrics")
                        .queryParam("host", HOST)
                        .queryParam("name", name)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        JsonNode metric = root.path("content").path(0);
        if (metric.isMissingNode()) {
            throw new IllegalStateException("Metric conflict but not found by name: " + name);
        }
        return metric.path("id").asLong();
    }
}
