package com.redenius.metrics.service.dto;

import com.redenius.metrics.service.entity.Metric;

public final class MetricMapper {

    private MetricMapper() {}

    public static Metric toEntity(CreateMetricRequest request) {
        return new Metric(request.name(), request.unit(), request.host());
    }

    public static MetricResponse toResponse(Metric metric, long pointCount) {
        return new MetricResponse(
                metric.getId(),
                metric.getName(),
                metric.getUnit(),
                metric.getHost(),
                metric.getCreatedAt(),
                pointCount
        );
    }
}
