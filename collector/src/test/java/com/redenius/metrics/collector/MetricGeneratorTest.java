package com.redenius.metrics.collector;

import com.redenius.metrics.common.dto.MetricPointMessage;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetricGeneratorTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, MetricPointMessage> kafkaTemplate = mock(KafkaTemplate.class);
    private final MetricRegistrar registrar = mock(MetricRegistrar.class);
    private final MetricGenerator generator = new MetricGenerator(kafkaTemplate,
            new CollectorProperties("metrics.raw", 1000, 2, "http://metrics-service"), registrar);

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendOnePointPerMetricKeyedByMetricId() {
        when(registrar.ensureMetrics()).thenReturn(List.of(1L, 2L));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        generator.generate();
        generator.generate();

        ArgumentCaptor<ProducerRecord<String, MetricPointMessage>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(4)).send(captor.capture());
        verify(registrar, times(1)).ensureMetrics();

        ProducerRecord<String, MetricPointMessage> first = captor.getAllValues().getFirst();
        assertThat(first.topic()).isEqualTo("metrics.raw");
        assertThat(first.key()).isEqualTo("1");
        assertThat(first.value().metricId()).isEqualTo(1L);
        assertThat(first.headers().lastHeader("correlationId")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepSendingWhenKafkaRejectsAPoint() {
        when(registrar.ensureMetrics()).thenReturn(List.of(1L, 2L));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatCode(generator::generate).doesNotThrowAnyException();
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }
}
