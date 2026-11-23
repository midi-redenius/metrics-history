package com.redenius.metrics.service.repository;

import com.redenius.metrics.service.entity.MetricPoint;
import com.redenius.metrics.service.entity.MetricPointId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MetricPointRepository extends JpaRepository<MetricPoint, MetricPointId> {

    @Modifying
    @Query(value = """
            INSERT INTO metric_point (metric_id, ts, value)
            VALUES (:metricId, :ts, :value)
            ON CONFLICT (metric_id, ts) DO UPDATE SET value = EXCLUDED.value
            """, nativeQuery = true)
    void upsertPoint(Long metricId, java.time.Instant ts, double value);

    long countByMetricId(Long metricId);

    @Query("""
            SELECT p.metricId AS metricId, COUNT(p) AS pointCount
            FROM MetricPoint p
            WHERE p.metricId IN :metricIds
            GROUP BY p.metricId
            """)
    List<MetricPointCount> countByMetricIds(Collection<Long> metricIds);

    void deleteByMetricId(Long metricId);
}
