package com.redenius.metrics.service.service;

import com.redenius.metrics.service.cache.HistoryCacheGenerations;
import com.redenius.metrics.service.config.CacheConfig;
import com.redenius.metrics.service.config.MetricsConfig;
import com.redenius.metrics.service.dto.AggregatedPoint;
import com.redenius.metrics.service.dto.HistoryResponse;
import com.redenius.metrics.service.entity.Metric;
import com.redenius.metrics.service.exception.MetricNotFoundException;
import com.redenius.metrics.service.repository.MetricRepository;
import com.redenius.metrics.service.service.aggregation.AggregationType;
import io.micrometer.core.instrument.Counter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class HistoryService {

    private static final Logger log = LoggerFactory.getLogger(HistoryService.class);

    static final long MAX_BUCKETS = 10_000;

    private final MetricRepository metricRepository;
    private final EntityManager entityManager;
    private final CacheManager cacheManager;
    private final HistoryCacheGenerations cacheGenerations;
    private final Counter historyQueriesCounter;
    private final Counter cacheHitsCounter;

    public HistoryService(MetricRepository metricRepository,
                          EntityManager entityManager,
                          CacheManager cacheManager,
                          HistoryCacheGenerations cacheGenerations,
                          @Qualifier(MetricsConfig.HISTORY_QUERIES) Counter historyQueriesCounter,
                          @Qualifier(MetricsConfig.CACHE_HITS) Counter cacheHitsCounter) {
        this.metricRepository = metricRepository;
        this.entityManager = entityManager;
        this.cacheManager = cacheManager;
        this.cacheGenerations = cacheGenerations;
        this.historyQueriesCounter = historyQueriesCounter;
        this.cacheHitsCounter = cacheHitsCounter;
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistory(Long metricId, Instant from, Instant to,
                                      String rawStep, AggregationType aggType) {
        HistoryStep step = HistoryStep.parse(rawStep);
        validateRange(from, to, step);
        historyQueriesCounter.increment();

        String cacheName = CacheConfig.resolveCacheName(step);
        String cacheKey = cacheKey(metricId, from, to, step, aggType);

        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            HistoryResponse cached = cache.get(cacheKey, HistoryResponse.class);
            if (cached != null) {
                cacheHitsCounter.increment();
                log.debug("Cache hit: {} key={}", cacheName, cacheKey);
                return cached;
            }
        }

        Metric metric = metricRepository.findById(metricId)
                .orElseThrow(() -> new MetricNotFoundException(metricId));

        String sql = """
                SELECT date_bin(CAST(:step AS interval), ts, TIMESTAMPTZ '1970-01-01') AS bucket,
                       %s AS agg_value
                FROM metric_point
                WHERE metric_id = :metricId AND ts >= :fromTs AND ts < :toTs
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(aggType.sqlExpression());

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("step", step.sqlInterval());
        query.setParameter("metricId", metricId);
        query.setParameter("fromTs", Timestamp.from(from));
        query.setParameter("toTs", Timestamp.from(to));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<AggregatedPoint> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            Instant bucket;
            if (row[0] instanceof Timestamp ts) {
                bucket = ts.toInstant();
            } else if (row[0] instanceof java.time.OffsetDateTime odt) {
                bucket = odt.toInstant();
            } else {
                bucket = (Instant) row[0];
            }
            double value = ((Number) row[1]).doubleValue();
            points.add(new AggregatedPoint(bucket, value));
        }

        HistoryResponse response = new HistoryResponse(
                metricId, metric.getName(), aggType.name(),
                step.text(), from, to, points
        );

        if (cache != null) {
            cache.put(cacheKey, response);
        }

        return response;
    }

    private String cacheKey(Long metricId, Instant from, Instant to, HistoryStep step, AggregationType aggType) {
        long generation = cacheGenerations.current(metricId);
        return metricId + ":g" + generation + ":" + from + ":" + to + ":" + step.seconds() + "s:" + aggType;
    }

    private void validateRange(Instant from, Instant to, HistoryStep step) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        long buckets = Duration.between(from, to).toSeconds() / step.seconds();
        if (buckets > MAX_BUCKETS) {
            throw new IllegalArgumentException(
                    "range is too large for step: " + buckets + " buckets, max is " + MAX_BUCKETS);
        }
    }
}
