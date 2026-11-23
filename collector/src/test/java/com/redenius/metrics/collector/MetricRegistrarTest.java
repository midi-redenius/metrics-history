package com.redenius.metrics.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetricRegistrarTest {

    private static final String BASE_URL = "http://metrics-service";

    private MockRestServiceServer server;
    private MetricRegistrar registrar;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        registrar = new MetricRegistrar(builder,
                new CollectorProperties("metrics.raw", 1000, 2, BASE_URL));
    }

    @Test
    void shouldCreateMissingMetrics() {
        server.expect(requestTo(BASE_URL + "/api/v1/metrics")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\": 11}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/metrics")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\": 12}", MediaType.APPLICATION_JSON));

        assertThat(registrar.ensureMetrics()).containsExactly(11L, 12L);
        server.verify();
    }

    @Test
    void shouldLookUpExistingMetricByNameOnConflict() {
        server.expect(requestTo(BASE_URL + "/api/v1/metrics")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(BASE_URL + "/api/v1/metrics?host=collector&name=collector.metric.1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"content\": [{\"id\": 7, \"name\": \"collector.metric.1\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/v1/metrics")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\": 8}", MediaType.APPLICATION_JSON));

        assertThat(registrar.ensureMetrics()).containsExactly(7L, 8L);
        server.verify();
    }

    @Test
    void shouldFailWhenConflictingMetricCannotBeFound() {
        server.expect(requestTo(BASE_URL + "/api/v1/metrics")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(BASE_URL + "/api/v1/metrics?host=collector&name=collector.metric.1"))
                .andRespond(withSuccess("{\"content\": []}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> registrar.ensureMetrics())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collector.metric.1");
    }
}
