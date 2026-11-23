package com.redenius.metrics.service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cache.type=redis")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("timescale/timescaledb:2.32.1-pg17")
                    .asCompatibleSubstituteFor("postgres"))
                    .withEnv("TIMESCALEDB_TELEMETRY", "off");

    @ServiceConnection
    protected static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.4.0"));

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                    .withExposedPorts(6379);

    static {
        postgres.start();
        kafka.start();
        redis.start();
    }
}
