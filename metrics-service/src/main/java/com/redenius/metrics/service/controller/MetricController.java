package com.redenius.metrics.service.controller;

import com.redenius.metrics.service.dto.BatchIngestRequest;
import com.redenius.metrics.service.dto.BatchIngestResponse;
import com.redenius.metrics.service.dto.CreateMetricRequest;
import com.redenius.metrics.service.dto.IngestPointRequest;
import com.redenius.metrics.service.dto.MetricResponse;
import com.redenius.metrics.service.service.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "Metrics", description = "Metric registration and point ingestion")
public class MetricController {

    private final MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }

    @PostMapping
    @Operation(summary = "Register a new metric")
    public ResponseEntity<MetricResponse> createMetric(@Valid @RequestBody CreateMetricRequest request) {
        MetricResponse response = metricService.createMetric(request);
        return ResponseEntity
                .created(URI.create("/api/v1/metrics/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get metric by ID")
    public MetricResponse getMetric(@PathVariable Long id) {
        return metricService.getMetric(id);
    }

    @GetMapping
    @Operation(summary = "List metrics, optionally filtered by host and/or exact name")
    public Page<MetricResponse> listMetrics(@RequestParam(required = false) String host,
                                            @RequestParam(required = false) String name,
                                            Pageable pageable) {
        return metricService.listMetrics(host, name, pageable);
    }

    @PostMapping("/{id}/points")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ingest a data point for a metric")
    public void ingestPoint(@PathVariable Long id, @Valid @RequestBody IngestPointRequest request) {
        metricService.ingestPoint(id, request);
    }

    @PostMapping("/{id}/points/batch")
    @Operation(summary = "Batch ingest data points for a metric (up to 1000)")
    public BatchIngestResponse batchIngest(@PathVariable Long id,
                                           @Valid @RequestBody BatchIngestRequest request) {
        int count = metricService.batchIngest(id, request);
        return new BatchIngestResponse(count);
    }
}
