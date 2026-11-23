package com.redenius.metrics.service.controller;

import com.redenius.metrics.service.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class HistoryControllerTest extends BaseIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldReturnAggregatedHistory_200() {
        int metricId = createMetric("api.latency", "api-01");
        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            ingest(metricId, base.plus(i * 10L, ChronoUnit.MINUTES), 100 + i * 10);
        }

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .body("metricId", equalTo(metricId))
                .body("aggregation", equalTo("AVG"))
                .body("points.size()", equalTo(1))
                .body("points[0].bucket", equalTo("2024-01-15T10:00:00Z"))
                .body("points[0].value", equalTo(120.0F));
    }

    @Test
    void shouldSplitRangeIntoStepAlignedBucketsExcludingTo() {
        int metricId = createMetric("bucketed.metric", "bucket-host");
        ingest(metricId, Instant.parse("2024-01-15T10:00:00Z"), 10);
        ingest(metricId, Instant.parse("2024-01-15T10:14:59Z"), 30);
        ingest(metricId, Instant.parse("2024-01-15T10:15:00Z"), 50);
        ingest(metricId, Instant.parse("2024-01-15T10:59:00Z"), 70);
        ingest(metricId, Instant.parse("2024-01-15T11:00:00Z"), 1000);

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "15 minutes")
                .queryParam("agg", "MAX")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .body("points.bucket", contains(
                        "2024-01-15T10:00:00Z", "2024-01-15T10:15:00Z", "2024-01-15T10:45:00Z"))
                .body("points.value", contains(30.0F, 50.0F, 70.0F));
    }

    @Test
    void shouldReturnEmptyHistoryForNoData() {
        int metricId = createMetric("empty.metric", "test-01");

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .body("points.size()", equalTo(0));
    }

    @Test
    void shouldReturnNotFound_404_forUnknownMetric() {
        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/999999/history")
        .then()
                .statusCode(404);
    }

    @Test
    void shouldSupportMinAggregation() {
        int metricId = createMetric("disk.usage", "stor-01");
        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            ingest(metricId, base.plus(i * 5L, ChronoUnit.MINUTES), 50 + i * 20);
        }

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "MIN")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .body("aggregation", equalTo("MIN"))
                .body("points[0].value", equalTo(50.0F));
    }

    @Test
    void shouldSupportP95Aggregation() {
        int metricId = createMetric("p95.metric", "p95-host");
        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        for (int i = 1; i <= 20; i++) {
            ingest(metricId, base.plus(i, ChronoUnit.MINUTES), i);
        }

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "P95")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .body("aggregation", equalTo("P95"))
                .body("points.size()", equalTo(1))
                .body("points[0].value", equalTo(19.05F));
    }

    @Test
    void shouldRejectInvalidRange_400() {
        int metricId = createMetric("bad.range", "range-host");

        given()
                .queryParam("from", "2024-01-15T12:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("detail", containsString("from must be before to"));
    }

    @Test
    void shouldRejectInvalidStep_400() {
        int metricId = createMetric("bad.step", "step-host");

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "drop table")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(400)
                .body("title", equalTo("Bad Request"))
                .body("detail", containsString("step must match"));
    }

    @Test
    void shouldRejectZeroStep_400() {
        int metricId = createMetric("zero.step", "step-host");

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "0 minutes")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(400)
                .body("detail", containsString("greater than zero"));
    }

    @Test
    void shouldRejectTooManyBuckets_400() {
        int metricId = createMetric("huge.range", "step-host");

        given()
                .queryParam("from", "2024-01-01T00:00:00Z")
                .queryParam("to", "2025-01-01T00:00:00Z")
                .queryParam("step", "1 second")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(400)
                .body("detail", containsString("range is too large"));
    }

    private int createMetric(String name, String host) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "unit": "ms", "host": "%s"}
                        """.formatted(name, host))
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");
    }

    private void ingest(int metricId, Instant ts, double value) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"ts": "%s", "value": %s}
                        """.formatted(ts, value))
        .when()
                .post("/api/v1/metrics/{id}/points", metricId)
        .then()
                .statusCode(204);
    }
}
