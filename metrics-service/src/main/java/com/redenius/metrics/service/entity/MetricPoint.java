package com.redenius.metrics.service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "metric_point")
@IdClass(MetricPointId.class)
public class MetricPoint {

    @Id
    @Column(name = "metric_id")
    private Long metricId;

    @Id
    @Column(name = "ts")
    private Instant ts;

    @Column(nullable = false)
    private double value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metric_id", insertable = false, updatable = false)
    private Metric metric;

    protected MetricPoint() {}

    public MetricPoint(Long metricId, Instant ts, double value) {
        this.metricId = metricId;
        this.ts = ts;
        this.value = value;
    }

    public Long getMetricId() { return metricId; }
    public Instant getTs() { return ts; }
    public double getValue() { return value; }
    public Metric getMetric() { return metric; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetricPoint that)) return false;
        return Objects.equals(metricId, that.metricId) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricId, ts);
    }
}
