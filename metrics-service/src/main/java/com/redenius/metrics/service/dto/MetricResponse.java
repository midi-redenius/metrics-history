package com.redenius.metrics.service.dto;

import java.time.Instant;

public record MetricResponse(
        Long id,
        String name,
        String unit,
        String host,
        Instant createdAt,
        long pointCount
) {}
