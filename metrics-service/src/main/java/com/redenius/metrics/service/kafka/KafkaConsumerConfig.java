package com.redenius.metrics.service.kafka;

import com.redenius.metrics.service.exception.MetricNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> dltKafkaTemplate) {
        // Partition -1 lets the producer pick one: the DLT may have fewer partitions than the source topic.
        var recoverer = new DeadLetterPublishingRecoverer(dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));

        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
        errorHandler.setLogLevel(KafkaException.Level.WARN);
        errorHandler.addNotRetryableExceptions(
                MetricNotFoundException.class,
                ConstraintViolationException.class);
        return errorHandler;
    }
}
