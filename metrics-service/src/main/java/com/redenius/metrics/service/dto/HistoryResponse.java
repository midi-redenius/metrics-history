package com.redenius.metrics.service.dto;

import java.time.Instant;
import java.util.List;

public record HistoryResponse(
        Long metricId,
        String metricName,
        String aggregation,
        String step,
        Instant from,
        Instant to,
        List<AggregatedPoint> points
) {}
