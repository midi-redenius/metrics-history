package com.redenius.metrics.service.service;

import com.redenius.metrics.service.dto.CreateMetricRequest;
import com.redenius.metrics.service.exception.MetricConflictException;
import com.redenius.metrics.service.repository.MetricPointBatchWriter;
import com.redenius.metrics.service.repository.MetricPointRepository;
import com.redenius.metrics.service.repository.MetricRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricServiceTest {

    private final MetricRepository metricRepository = mock(MetricRepository.class);

    private final MetricService service = new MetricService(
            metricRepository,
            mock(MetricPointRepository.class),
            mock(MetricPointBatchWriter.class),
            mock(ApplicationEventPublisher.class),
            mock(Counter.class));

    @Test
    void shouldReportConflictWhenConcurrentInsertWinsTheRace() {
        when(metricRepository.findByNameAndHost("cpu", "web-01")).thenReturn(Optional.empty());
        when(metricRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createMetric(new CreateMetricRequest("cpu", "%", "web-01")))
                .isInstanceOf(MetricConflictException.class);
    }
}
