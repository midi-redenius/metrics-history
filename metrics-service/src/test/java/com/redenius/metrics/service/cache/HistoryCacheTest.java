package com.redenius.metrics.service.cache;

import com.redenius.metrics.service.BaseIntegrationTest;
import com.redenius.metrics.service.config.CacheConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class HistoryCacheTest extends BaseIntegrationTest {

    private static final double INITIAL_AVG = 110.0;

    @LocalServerPort
    int port;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        for (String name : new String[]{CacheConfig.HISTORY_SHORT,
                CacheConfig.HISTORY_MEDIUM, CacheConfig.HISTORY_LONG}) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    @Test
    void shouldServeCachedHistoryUntilInvalidated() {
        int metricId = createMetricWithPoints("cache.test", "cache-host");

        assertThat(hourlyAvg(metricId)).isEqualTo(INITIAL_AVG);

        insertBypassingService(metricId, "2024-01-15T10:40:00Z", 1000);

        assertThat(hourlyAvg(metricId)).isEqualTo(INITIAL_AVG);
    }

    @Test
    void shouldInvalidateCacheOnIngest() {
        int metricId = createMetricWithPoints("evict.test", "evict-host");

        assertThat(hourlyAvg(metricId)).isEqualTo(INITIAL_AVG);

        ingest(metricId, "2024-01-15T10:30:00Z", 130);

        assertThat(hourlyAvg(metricId)).isEqualTo(115.0);
    }

    @Test
    void shouldNotInvalidateOtherMetricCacheOnIngest() {
        int metricA = createMetricWithPoints("evict.a", "iso-host-a");
        int metricB = createMetricWithPoints("evict.b", "iso-host-b");

        assertThat(hourlyAvg(metricA)).isEqualTo(INITIAL_AVG);
        assertThat(hourlyAvg(metricB)).isEqualTo(INITIAL_AVG);

        ingest(metricA, "2024-01-15T10:30:00Z", 130);
        insertBypassingService(metricB, "2024-01-15T10:40:00Z", 1000);

        assertThat(hourlyAvg(metricA)).isEqualTo(115.0);
        assertThat(hourlyAvg(metricB)).isEqualTo(INITIAL_AVG);
    }

    @Test
    void shouldUseShortCacheForSmallStep() {
        int metricId = createMetricWithPoints("short.cache", "short-host");

        given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "5 minutes")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200);

        assertThat(redisTemplate.keys(CacheConfig.HISTORY_SHORT + "::" + metricId + ":*")).hasSize(1);
        assertThat(redisTemplate.keys(CacheConfig.HISTORY_LONG + "::" + metricId + ":*")).isEmpty();
    }

    private double hourlyAvg(int metricId) {
        float value = given()
                .queryParam("from", "2024-01-15T10:00:00Z")
                .queryParam("to", "2024-01-15T11:00:00Z")
                .queryParam("step", "1 hour")
                .queryParam("agg", "AVG")
        .when()
                .get("/api/v1/metrics/{id}/history", metricId)
        .then()
                .statusCode(200)
                .extract().path("points[0].value");
        return value;
    }

    private void insertBypassingService(int metricId, String ts, double value) {
        jdbcTemplate.update("INSERT INTO metric_point (metric_id, ts, value) VALUES (?, ?, ?)",
                metricId, Timestamp.from(Instant.parse(ts)), value);
    }

    private void ingest(int metricId, String ts, double value) {
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

    private int createMetricWithPoints(String name, String host) {
        int metricId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name": "%s", "unit": "ms", "host": "%s"}
                        """.formatted(name, host))
        .when()
                .post("/api/v1/metrics")
        .then()
                .statusCode(201)
                .extract().path("id");

        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            ingest(metricId, base.plus(i * 10L, ChronoUnit.MINUTES).toString(), 100 + i * 10);
        }
        return metricId;
    }
}
