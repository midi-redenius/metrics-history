package com.redenius.metrics.service.controller;

import com.redenius.metrics.service.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

class MetricControllerTest extends BaseIntegrationTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void shouldCreateMetric_201() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "cpu.usage", "unit": "%", "host": "web-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .header("Location", containsString("/api/v1/metrics/"))
                .body("name", equalTo("cpu.usage"))
                .body("host", equalTo("web-01"))
                .body("id", notNullValue());
    }

    @Test
    void shouldReturnConflict_409() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "mem.used", "unit": "MB", "host": "db-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "mem.used", "unit": "MB", "host": "db-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(409)
                .body("title", equalTo("Metric Conflict"));
    }

    @Test
    void shouldReturnBadRequest_400_whenNameMissing() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"unit": "%", "host": "web-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(400)
                .body("title", equalTo("Validation Failed"))
                .body("detail", containsString("name"));
    }

    @Test
    void shouldGetMetricById_200() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "disk.io", "unit": "MB/s", "host": "storage-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
        .when()
                .get("/api/v1/metrics/{id}", id)
        .then()
                .statusCode(200)
                .body("name", equalTo("disk.io"))
                .body("pointCount", equalTo(0));
    }

    @Test
    void shouldReturnNotFound_404() {
        given()
        .when()
                .get("/api/v1/metrics/999999")
        .then()
                .statusCode(404)
                .body("title", equalTo("Metric Not Found"));
    }

    @Test
    void shouldListMetricsByHost() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "net.in", "unit": "Mbps", "host": "net-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201);

        given()
                .queryParam("host", "net-01")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("content.size()", greaterThanOrEqualTo(1))
                .body("content[0].host", equalTo("net-01"));
    }

    @Test
    void shouldReturnPointCountsInList() {
        int withPoints = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "count.a", "unit": "n", "host": "count-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "count.b", "unit": "n", "host": "count-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201);

        for (String ts : new String[]{"2024-01-15T10:00:00Z", "2024-01-15T10:01:00Z"}) {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {"ts": "%s", "value": 1.0}
                            """.formatted(ts))
            .when()
                    .post("/api/v1/metrics/{id}/points", withPoints)
            .then()
                    .statusCode(204);
        }

        given()
                .queryParam("host", "count-01")
                .queryParam("sort", "id,asc")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("content.size()", equalTo(2))
                .body("page.totalElements", equalTo(2))
                .body("page.number", equalTo(0))
                .body("content[0].pointCount", equalTo(2))
                .body("content[1].pointCount", equalTo(0));
    }

    @Test
    void shouldFilterByExactNameAndHost() {
        for (String body : new String[]{
                "{\"name\": \"lookup.target\", \"host\": \"lookup-01\"}",
                "{\"name\": \"lookup.other\", \"host\": \"lookup-01\"}",
                "{\"name\": \"lookup.target\", \"host\": \"lookup-02\"}"}) {
            given()
                    .contentType(ContentType.JSON)
                    .body(body)
            .when()
                    .post("/api/v1/metrics")
            .then()
                    .statusCode(201);
        }

        given()
                .queryParam("host", "lookup-01")
                .queryParam("name", "lookup.target")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].name", equalTo("lookup.target"))
                .body("content[0].host", equalTo("lookup-01"));

        given()
                .queryParam("name", "lookup.target")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("content.size()", equalTo(2));
    }

    @Test
    void shouldReturnEmptyPageWhenNothingMatches() {
        given()
                .queryParam("host", "no-such-host")
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("content", empty())
                .body("page.totalElements", equalTo(0));
    }

    @Test
    void shouldReturnBadRequest_400_forMalformedJson() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"name\": \"broken\", ")
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(400)
                .body("detail", equalTo("Malformed request body"));
    }

    @Test
    void shouldCapPageSize() {
        given()
                .queryParam("size", 5000)
        .when()
                .get("/api/v1/metrics")
        .then()
                .statusCode(200)
                .body("page.size", equalTo(100));
    }

    @Test
    void shouldIngestPoint_204() {
        int id = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "cpu.temp", "unit": "°C", "host": "gpu-01"}
                        """)
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"ts": "2024-01-15T10:00:00Z", "value": 72.5}
                        """)
        .when()
                .post("/api/v1/metrics/{id}/points", id)
        .then()
                .statusCode(204);
    }

    @Test
    void shouldReturnNotFound_404_onIngestForUnknownMetric() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"ts": "2024-01-15T10:00:00Z", "value": 42.0}
                        """)
        .when()
                .post("/api/v1/metrics/999999/points")
        .then()
                .statusCode(404);
    }
}
