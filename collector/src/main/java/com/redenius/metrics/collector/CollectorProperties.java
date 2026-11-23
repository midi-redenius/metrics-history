package com.redenius.metrics.collector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(
        String topic,
        long intervalMs,
        int metricCount,
        String metricsServiceUrl
) {}
