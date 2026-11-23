package com.redenius.metrics.service.kafka;

import com.redenius.metrics.common.dto.MetricPointMessage;
import com.redenius.metrics.service.config.CorrelationIdFilter;
import com.redenius.metrics.service.exception.MetricNotFoundException;
import com.redenius.metrics.service.repository.PointRow;
import com.redenius.metrics.service.service.MetricService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.KafkaUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MetricPointConsumer {

    private static final Logger log = LoggerFactory.getLogger(MetricPointConsumer.class);
    private static final LogAccessor LOG_ACCESSOR = new LogAccessor(MetricPointConsumer.class);
    private static final String CORRELATION_ID = "correlationId";

    private final MetricService metricService;
    private final Validator validator;

    public MetricPointConsumer(MetricService metricService, Validator validator) {
        this.metricService = metricService;
        this.validator = validator;
    }

    /**
     * Valid records before the first bad one are stored, then the bad one is reported by index: the error
     * handler commits the stored prefix, sends the bad record to the DLT and redelivers the rest.
     */
    @KafkaListener(topics = "${app.kafka.topic:metrics.raw}", groupId = "${spring.kafka.consumer.group-id}",
            batch = "true")
    public void consume(List<ConsumerRecord<String, MetricPointMessage>> records) {
        Set<Long> existingIds = metricService.existingMetricIds(records.stream()
                .map(ConsumerRecord::value)
                .filter(Objects::nonNull)
                .map(MetricPointMessage::metricId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<PointRow> valid = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<String, MetricPointMessage> record = records.get(i);
            RuntimeException problem = findProblem(record, existingIds);
            if (problem != null) {
                metricService.ingestPoints(valid);
                log.warn("Rejecting record {}-{}@{} correlationId={}: {}", record.topic(), record.partition(),
                        record.offset(), extractCorrelationId(record), problem.getMessage());
                throw new BatchListenerFailedException(problem.getMessage(), problem, i);
            }
            MetricPointMessage msg = record.value();
            valid.add(new PointRow(msg.metricId(), msg.ts(), msg.value()));
        }

        metricService.ingestPoints(valid);
        log.debug("Stored {} metric points from Kafka", valid.size());
    }

    private RuntimeException findProblem(ConsumerRecord<String, MetricPointMessage> record, Set<Long> existingIds) {
        DeserializationException deserialization = SerializationUtils.getExceptionFromHeader(
                record, KafkaUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER, LOG_ACCESSOR);
        if (deserialization != null) {
            return deserialization;
        }
        MetricPointMessage msg = record.value();
        if (msg == null) {
            return new ConstraintViolationException("Metric point message must not be null", Set.of());
        }
        Set<ConstraintViolation<MetricPointMessage>> violations = validator.validate(msg);
        if (!violations.isEmpty()) {
            return new ConstraintViolationException(violations);
        }
        if (!existingIds.contains(msg.metricId())) {
            return new MetricNotFoundException(msg.metricId());
        }
        return null;
    }

    private String extractCorrelationId(ConsumerRecord<String, MetricPointMessage> record) {
        var header = record.headers().lastHeader(CORRELATION_ID);
        if (header != null) {
            String correlationId = new String(header.value(), StandardCharsets.UTF_8);
            if (CorrelationIdFilter.isValid(correlationId)) {
                return correlationId;
            }
        }
        return "kafka-" + record.topic() + "-" + record.partition() + "-" + record.offset();
    }
}
