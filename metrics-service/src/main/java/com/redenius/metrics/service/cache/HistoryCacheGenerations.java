package com.redenius.metrics.service.cache;

/**
 * Per-metric version that is part of every history cache key. Bumping it makes all cached
 * history of the metric unreachable without scanning Redis; stale entries expire by TTL.
 */
public interface HistoryCacheGenerations {

    long current(Long metricId);

    void bump(Long metricId);
}
