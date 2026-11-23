package com.redenius.metrics.service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public static final String POINTS_INGESTED = "pointsIngestedCounter";
    public static final String HISTORY_QUERIES = "historyQueriesCounter";
    public static final String CACHE_HITS = "cacheHitsCounter";

    @Bean(POINTS_INGESTED)
    public Counter pointsIngestedCounter(MeterRegistry registry) {
        return Counter.builder("metrics.points.ingested")
                .description("Total number of metric points ingested")
                .register(registry);
    }

    @Bean(HISTORY_QUERIES)
    public Counter historyQueriesCounter(MeterRegistry registry) {
        return Counter.builder("metrics.history.queries")
                .description("Total number of history queries")
                .register(registry);
    }

    @Bean(CACHE_HITS)
    public Counter cacheHitsCounter(MeterRegistry registry) {
        return Counter.builder("metrics.cache.hits")
                .description("Total number of cache hits on history queries")
                .register(registry);
    }
}
