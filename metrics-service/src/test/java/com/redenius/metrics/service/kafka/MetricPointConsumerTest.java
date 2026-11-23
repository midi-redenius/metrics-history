package com.redenius.metrics.service.kafka;

import com.redenius.metrics.common.dto.MetricPointMessage;
import com.redenius.metrics.service.BaseIntegrationTest;
import com.redenius.metrics.service.entity.Metric;
import com.redenius.metrics.service.entity.MetricPoint;
import com.redenius.metrics.service.entity.MetricPointId;
import com.redenius.metrics.service.repository.MetricPointRepository;
import com.redenius.metrics.service.repository.MetricRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Payload;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Import(MetricPointConsumerTest.DltTestConfig.class)
class MetricPointConsumerTest extends BaseIntegrationTest {

    @Autowired
    KafkaTemplate<String, Object> dltKafkaTemplate;

    @Autowired
    MetricRepository metricRepository;

    @Autowired
    MetricPointRepository metricPointRepository;

    @Autowired
    BlockingQueue<MetricPointMessage> dltMessages;

    @BeforeEach
    void clearDlt() {
        dltMessages.clear();
    }

    @Test
    void shouldConsumeMessageAndPersistPoint() {
        Metric metric = metricRepository.save(new Metric("kafka.test", "ms", "consumer-host"));
        Long metricId = metric.getId();

        Instant ts = Instant.parse("2024-06-01T12:00:00Z");
        MetricPointMessage msg = new MetricPointMessage(metricId, ts, 42.5);

        ProducerRecord<String, Object> record =
                new ProducerRecord<>("metrics.raw", String.valueOf(metricId), msg);
        record.headers().add(new RecordHeader("correlationId",
                UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));

        dltKafkaTemplate.send(record);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(metricPointRepository.countByMetricId(metricId)).isEqualTo(1));
    }

    @Test
    void shouldOverwriteDuplicateTimestampWithLatestValue() {
        Metric metric = metricRepository.save(new Metric("kafka.dedup", "ms", "dedup-host"));
        Long metricId = metric.getId();

        Instant ts = Instant.parse("2024-06-01T13:00:00Z");

        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", String.valueOf(metricId),
                new MetricPointMessage(metricId, ts, 55.0)));
        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", String.valueOf(metricId),
                new MetricPointMessage(metricId, ts, 77.0)));
        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", String.valueOf(metricId),
                new MetricPointMessage(metricId, ts, 99.0)));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(metricPointRepository.countByMetricId(metricId)).isEqualTo(1);
            MetricPoint point = metricPointRepository.findById(new MetricPointId(metricId, ts)).orElseThrow();
            assertThat(point.getValue()).isEqualTo(99.0);
        });
    }

    @Test
    void shouldRouteUnknownMetricToDlt() throws Exception {
        Instant ts = Instant.parse("2024-06-01T14:00:00Z");
        long unknownId = 9_999_991L;
        MetricPointMessage msg = new MetricPointMessage(unknownId, ts, 1.0);

        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", String.valueOf(unknownId), msg)).get(5, TimeUnit.SECONDS);

        MetricPointMessage dlt = dltMessages.poll(20, TimeUnit.SECONDS);
        assertThat(dlt).isNotNull();
        assertThat(dlt.metricId()).isEqualTo(unknownId);
        assertThat(dlt.value()).isEqualTo(1.0);
    }

    @Test
    void shouldStoreValidRecordsAroundARejectedOneInTheSameBatch() throws Exception {
        Metric metric = metricRepository.save(new Metric("kafka.mixed", "ms", "mixed-host"));
        Long metricId = metric.getId();
        String key = String.valueOf(metricId);
        long unknownId = 9_999_992L;

        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", key,
                new MetricPointMessage(metricId, Instant.parse("2024-06-02T10:00:00Z"), 1.0)));
        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", key,
                new MetricPointMessage(unknownId, Instant.parse("2024-06-02T10:01:00Z"), 2.0)));
        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", key,
                new MetricPointMessage(metricId, Instant.parse("2024-06-02T10:02:00Z"), 3.0))).get(5, TimeUnit.SECONDS);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(metricPointRepository.countByMetricId(metricId)).isEqualTo(2));

        MetricPointMessage dlt = dltMessages.poll(20, TimeUnit.SECONDS);
        assertThat(dlt).isNotNull();
        assertThat(dlt.metricId()).isEqualTo(unknownId);
    }

    @Test
    void shouldRouteInvalidMessageToDlt() throws Exception {
        Metric metric = metricRepository.save(new Metric("kafka.invalid", "ms", "invalid-host"));
        MetricPointMessage msg = new MetricPointMessage(metric.getId(), null, 5.0);

        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", String.valueOf(metric.getId()), msg))
                .get(5, TimeUnit.SECONDS);

        MetricPointMessage dlt = dltMessages.poll(20, TimeUnit.SECONDS);
        assertThat(dlt).isNotNull();
        assertThat(dlt.metricId()).isEqualTo(metric.getId());
        assertThat(dlt.ts()).isNull();
        assertThat(metricPointRepository.countByMetricId(metric.getId())).isZero();
    }

    @Test
    void shouldRoutePoisonPillToDltAsRawBytes() throws Exception {
        String key = "poison-" + UUID.randomUUID();
        byte[] payload = "{not json".getBytes(StandardCharsets.UTF_8);

        dltKafkaTemplate.send(new ProducerRecord<>("metrics.raw", key, payload)).get(5, TimeUnit.SECONDS);

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "poison-probe-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of("metrics.raw" + KafkaConsumerConfig.DLT_SUFFIX));

            ConsumerRecord<String, byte[]> dlt = null;
            long deadline = System.currentTimeMillis() + 20_000;
            while (dlt == null && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        dlt = record;
                    }
                }
            }

            assertThat(dlt).isNotNull();
            assertThat(dlt.value()).isEqualTo(payload);
            assertThat(dlt.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value())
                    .asString(StandardCharsets.UTF_8)
                    .isEqualTo("metrics.raw");
        }
    }

    @TestConfiguration
    static class DltTestConfig {

        @Bean
        BlockingQueue<MetricPointMessage> dltMessages() {
            return new LinkedBlockingQueue<>();
        }

        @Bean
        DltProbe dltProbe(BlockingQueue<MetricPointMessage> dltMessages) {
            return new DltProbe(dltMessages);
        }
    }

    static class DltProbe {

        private final BlockingQueue<MetricPointMessage> dltMessages;

        DltProbe(BlockingQueue<MetricPointMessage> dltMessages) {
            this.dltMessages = dltMessages;
        }

        @KafkaListener(topics = "metrics.raw.DLT", groupId = "dlt-probe")
        void onDlt(@Payload MetricPointMessage message) {
            dltMessages.add(message);
        }
    }
}
