package com.redenius.metrics.service.actuator;

import com.redenius.metrics.service.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class ActuatorTest extends BaseIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldExposeHealthEndpoint() {
        given()
        .when()
                .get("/actuator/health")
        .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void shouldExposeProbesWithoutKafkaInReadiness() {
        given()
        .when()
                .get("/actuator/health/liveness")
        .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        given()
        .when()
                .get("/actuator/health/readiness")
        .then()
                .statusCode(200)
                .body("status", equalTo("UP"));

        given()
        .when()
                .get("/actuator/health/readiness/kafka")
        .then()
                .statusCode(404);
    }

    @Test
    void shouldEchoValidCorrelationId() {
        given()
                .header("X-Correlation-Id", "req-42.a:b")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .header("X-Correlation-Id", equalTo("req-42.a:b"));
    }

    @Test
    void shouldReplaceInvalidCorrelationId() {
        given()
                .header("X-Correlation-Id", "bad\"id with spaces")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .header("X-Correlation-Id", matchesPattern("[0-9a-f-]{36}"));
    }

    @Test
    void shouldExposePrometheusEndpoint() {
        given()
        .when()
                .get("/actuator/prometheus")
        .then()
                .statusCode(200)
                .body(containsString("jvm_memory"));
    }

    @Test
    void shouldExposeCustomMetrics() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "actuator.test", "unit": "%", "host": "act-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"ts": "2024-01-15T10:00:00Z", "value": 42.0}
                        """)
        .when()
                .post("/api/v1/metrics/{id}/points", id)
        .then()
                .statusCode(204);

        given()
        .when()
                .get("/actuator/prometheus")
        .then()
                .statusCode(200)
                .body(containsString("metrics_points_ingested_total"));
    }
}
