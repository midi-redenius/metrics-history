package com.redenius.metrics.service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "metric", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "host"}))
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String unit;

    @Column(nullable = false, length = 128)
    private String host;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Metric() {}

    public Metric(String name, String unit, String host) {
        this.name = name;
        this.unit = unit;
        this.host = host;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Metric m)) return false;
        return id != null && id.equals(m.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
