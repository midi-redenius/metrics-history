package com.redenius.metrics.service.config;

import com.redenius.metrics.service.cache.HistoryCacheGenerations;
import com.redenius.metrics.service.cache.InMemoryHistoryCacheGenerations;
import com.redenius.metrics.service.cache.RedisHistoryCacheGenerations;
import com.redenius.metrics.service.dto.HistoryResponse;
import com.redenius.metrics.service.service.HistoryStep;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String HISTORY_SHORT = "history-short";
    public static final String HISTORY_MEDIUM = "history-medium";
    public static final String HISTORY_LONG = "history-long";

    @Configuration
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    static class RedisCacheConfigurationBeans {

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
            JacksonJsonRedisSerializer<HistoryResponse> valueSerializer =
                    new JacksonJsonRedisSerializer<>(HistoryResponse.class);

            RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(valueSerializer))
                    .disableCachingNullValues();

            return RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                    .withCacheConfiguration(HISTORY_SHORT, base.entryTtl(Duration.ofSeconds(30)))
                    .withCacheConfiguration(HISTORY_MEDIUM, base.entryTtl(Duration.ofMinutes(5)))
                    .withCacheConfiguration(HISTORY_LONG, base.entryTtl(Duration.ofMinutes(15)))
                    .build();
        }

        @Bean
        public HistoryCacheGenerations historyCacheGenerations(StringRedisTemplate redisTemplate) {
            return new RedisHistoryCacheGenerations(redisTemplate);
        }
    }

    @Bean
    @ConditionalOnExpression("'${spring.cache.type:}' != 'redis'")
    public HistoryCacheGenerations inMemoryHistoryCacheGenerations() {
        return new InMemoryHistoryCacheGenerations();
    }

    public static String resolveCacheName(HistoryStep step) {
        Duration duration = step.duration();
        if (duration.compareTo(Duration.ofMinutes(5)) <= 0) {
            return HISTORY_SHORT;
        }
        if (duration.compareTo(Duration.ofHours(1)) >= 0) {
            return HISTORY_LONG;
        }
        return HISTORY_MEDIUM;
    }
}
