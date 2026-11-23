package com.redenius.metrics.service.controller;

import com.redenius.metrics.service.BaseIntegrationTest;
import com.redenius.metrics.service.entity.MetricPoint;
import com.redenius.metrics.service.entity.MetricPointId;
import com.redenius.metrics.service.repository.MetricPointRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class BatchIngestTest extends BaseIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MetricPointRepository metricPointRepository;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldBatchIngestPoints_200() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "batch.cpu", "unit": "%", "host": "batch-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "points": [
                            {"ts": "2024-01-15T10:00:00Z", "value": 10.0},
                            {"ts": "2024-01-15T10:01:00Z", "value": 20.0},
                            {"ts": "2024-01-15T10:02:00Z", "value": 30.0}
                          ]
                        }
                        """)
        .when()
                .post("/api/v1/metrics/{id}/points/batch", id)
        .then()
                .statusCode(200)
                .body("accepted", equalTo(3));

        given()
        .when()
                .get("/api/v1/metrics/{id}", id)
        .then()
                .statusCode(200)
                .body("pointCount", equalTo(3));
    }

    @Test
    void shouldOverwriteDuplicateTimestampInBatch() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "batch.overwrite", "unit": "%", "host": "batch-03"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "points": [
                            {"ts": "2024-01-15T10:00:00Z", "value": 10.0},
                            {"ts": "2024-01-15T10:00:00Z", "value": 42.0}
                          ]
                        }
                        """)
        .when()
                .post("/api/v1/metrics/{id}/points/batch", id)
        .then()
                .statusCode(200)
                .body("accepted", equalTo(2));

        Instant ts = Instant.parse("2024-01-15T10:00:00Z");
        MetricPoint point = metricPointRepository.findById(new MetricPointId((long) id, ts)).orElseThrow();
        assertThat(point.getValue()).isEqualTo(42.0);
        assertThat(metricPointRepository.countByMetricId((long) id)).isEqualTo(1);
    }

    @Test
    void shouldReturnNotFound_404_onBatchForUnknownMetric() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "points": [
                            {"ts": "2024-01-15T10:00:00Z", "value": 10.0}
                          ]
                        }
                        """)
        .when()
                .post("/api/v1/metrics/999999/points/batch")
        .then()
                .statusCode(404);
    }

    @Test
    void shouldRejectEmptyBatch_400() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "batch.empty", "unit": "%", "host": "batch-02"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"points": []}
                        """)
        .when()
                .post("/api/v1/metrics/{id}/points/batch", id)
        .then()
                .statusCode(400)
                .body("title", equalTo("Validation Failed"));
    }
}
