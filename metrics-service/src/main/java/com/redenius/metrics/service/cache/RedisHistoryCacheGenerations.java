package com.redenius.metrics.service.cache;

import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisHistoryCacheGenerations implements HistoryCacheGenerations {

    private static final String KEY_PREFIX = "history-gen::";

    private final StringRedisTemplate redis;

    public RedisHistoryCacheGenerations(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long current(Long metricId) {
        String value = redis.opsForValue().get(KEY_PREFIX + metricId);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public void bump(Long metricId) {
        redis.opsForValue().increment(KEY_PREFIX + metricId);
    }
}
