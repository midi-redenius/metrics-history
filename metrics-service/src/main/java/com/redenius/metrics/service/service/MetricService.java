package com.redenius.metrics.service.service;

import com.redenius.metrics.service.cache.MetricPointsIngestedEvent;
import com.redenius.metrics.service.config.MetricsConfig;
import com.redenius.metrics.service.dto.*;
import com.redenius.metrics.service.entity.Metric;
import com.redenius.metrics.service.exception.MetricConflictException;
import com.redenius.metrics.service.exception.MetricNotFoundException;
import com.redenius.metrics.service.repository.MetricPointBatchWriter;
import com.redenius.metrics.service.repository.MetricPointCount;
import com.redenius.metrics.service.repository.MetricPointRepository;
import com.redenius.metrics.service.repository.MetricRepository;
import com.redenius.metrics.service.repository.PointRow;
import io.micrometer.core.instrument.Counter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MetricService {

    private final MetricRepository metricRepository;
    private final MetricPointRepository metricPointRepository;
    private final MetricPointBatchWriter metricPointBatchWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter pointsIngestedCounter;

    public MetricService(MetricRepository metricRepository,
                         MetricPointRepository metricPointRepository,
                         MetricPointBatchWriter metricPointBatchWriter,
                         ApplicationEventPublisher eventPublisher,
                         @Qualifier(MetricsConfig.POINTS_INGESTED) Counter pointsIngestedCounter) {
        this.metricRepository = metricRepository;
        this.metricPointRepository = metricPointRepository;
        this.metricPointBatchWriter = metricPointBatchWriter;
        this.eventPublisher = eventPublisher;
        this.pointsIngestedCounter = pointsIngestedCounter;
    }

    @Transactional
    public MetricResponse createMetric(CreateMetricRequest request) {
        metricRepository.findByNameAndHost(request.name(), request.host())
                .ifPresent(existing -> {
                    throw new MetricConflictException(request.name(), request.host());
                });

        try {
            Metric metric = metricRepository.saveAndFlush(MetricMapper.toEntity(request));
            return MetricMapper.toResponse(metric, 0);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request inserted the same (name, host) after the lookup above.
            throw new MetricConflictException(request.name(), request.host());
        }
    }

    @Transactional(readOnly = true)
    public MetricResponse getMetric(Long id) {
        Metric metric = metricRepository.findById(id)
                .orElseThrow(() -> new MetricNotFoundException(id));
        long count = metricPointRepository.countByMetricId(id);
        return MetricMapper.toResponse(metric, count);
    }

    @Transactional(readOnly = true)
    public Page<MetricResponse> listMetrics(String host, String name, Pageable pageable) {
        boolean byHost = host != null && !host.isBlank();
        boolean byName = name != null && !name.isBlank();
        Page<Metric> page;
        if (byHost && byName) {
            page = metricRepository.findByNameAndHost(name, host, pageable);
        } else if (byHost) {
            page = metricRepository.findByHost(host, pageable);
        } else if (byName) {
            page = metricRepository.findByName(name, pageable);
        } else {
            page = metricRepository.findAll(pageable);
        }

        List<Long> ids = page.map(Metric::getId).getContent();
        Map<Long, Long> counts = ids.isEmpty()
                ? Map.of()
                : metricPointRepository.countByMetricIds(ids).stream()
                        .collect(Collectors.toMap(MetricPointCount::getMetricId, MetricPointCount::getPointCount));

        return page.map(metric -> MetricMapper.toResponse(metric, counts.getOrDefault(metric.getId(), 0L)));
    }

    @Transactional
    public void ingestPoint(Long metricId, IngestPointRequest request) {
        if (!metricRepository.existsById(metricId)) {
            throw new MetricNotFoundException(metricId);
        }
        metricPointRepository.upsertPoint(metricId, request.ts(), request.value());
        pointsIngestedCounter.increment();
        eventPublisher.publishEvent(new MetricPointsIngestedEvent(metricId));
    }

    @Transactional
    public int batchIngest(Long metricId, BatchIngestRequest request) {
        if (!metricRepository.existsById(metricId)) {
            throw new MetricNotFoundException(metricId);
        }
        ingestPoints(request.points().stream()
                .map(point -> new PointRow(metricId, point.ts(), point.value()))
                .toList());
        return request.points().size();
    }

    @Transactional(readOnly = true)
    public Set<Long> existingMetricIds(Collection<Long> metricIds) {
        return metricIds.isEmpty() ? Set.of() : metricRepository.findExistingIds(metricIds);
    }

    /** Callers must check that the metrics exist; the foreign key rejects the whole batch otherwise. */
    @Transactional
    public void ingestPoints(List<PointRow> points) {
        if (points.isEmpty()) {
            return;
        }
        metricPointBatchWriter.batchUpsert(points);
        pointsIngestedCounter.increment(points.size());
        points.stream()
                .map(PointRow::metricId)
                .distinct()
                .forEach(metricId -> eventPublisher.publishEvent(new MetricPointsIngestedEvent(metricId)));
    }
}
