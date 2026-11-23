package com.redenius.metrics.collector;

import com.redenius.metrics.common.dto.MetricPointMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class MetricGenerator {

    private static final Logger log = LoggerFactory.getLogger(MetricGenerator.class);

    private final KafkaTemplate<String, MetricPointMessage> kafkaTemplate;
    private final CollectorProperties properties;
    private final MetricRegistrar metricRegistrar;

    private volatile List<Long> metricIds;

    public MetricGenerator(KafkaTemplate<String, MetricPointMessage> kafkaTemplate,
                           CollectorProperties properties,
                           MetricRegistrar metricRegistrar) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.metricRegistrar = metricRegistrar;
    }

    @Scheduled(fixedDelayString = "${collector.interval-ms}")
    public void generate() {
        List<Long> ids = resolveMetricIds();
        for (Long metricId : ids) {
            String correlationId = UUID.randomUUID().toString();
            double value = ThreadLocalRandom.current().nextDouble(0, 100);
            MetricPointMessage msg = new MetricPointMessage(metricId, Instant.now(), value);

            ProducerRecord<String, MetricPointMessage> record =
                    new ProducerRecord<>(properties.topic(), String.valueOf(metricId), msg);
            record.headers().add(new RecordHeader("correlationId",
                    correlationId.getBytes(StandardCharsets.UTF_8)));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to send metric point: metricId={}, correlationId={}",
                            metricId, correlationId, ex);
                } else {
                    log.debug("Sent metric point: metricId={}, value={}, correlationId={}",
                            metricId, value, correlationId);
                }
            });
        }
    }

    private List<Long> resolveMetricIds() {
        List<Long> current = metricIds;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (metricIds == null) {
                metricIds = metricRegistrar.ensureMetrics();
            }
            return metricIds;
        }
    }
}
