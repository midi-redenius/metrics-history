package com.redenius.metrics.service.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class MetricPointId implements Serializable {

    private Long metricId;
    private Instant ts;

    public MetricPointId() {}

    public MetricPointId(Long metricId, Instant ts) {
        this.metricId = metricId;
        this.ts = ts;
    }

    public Long getMetricId() { return metricId; }
    public Instant getTs() { return ts; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricPointId that)) return false;
        return Objects.equals(metricId, that.metricId) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricId, ts);
    }
}
