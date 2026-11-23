package com.redenius.metrics.service.dto;

import java.time.Instant;

public record AggregatedPoint(
        Instant bucket,
        double value
) {
}
