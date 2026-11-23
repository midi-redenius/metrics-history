package com.redenius.metrics.service.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryHistoryCacheGenerations implements HistoryCacheGenerations {

    private final Map<Long, AtomicLong> generations = new ConcurrentHashMap<>();

    @Override
    public long current(Long metricId) {
        AtomicLong generation = generations.get(metricId);
        return generation == null ? 0 : generation.get();
    }

    @Override
    public void bump(Long metricId) {
        generations.computeIfAbsent(metricId, id -> new AtomicLong()).incrementAndGet();
    }
}
