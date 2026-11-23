package com.redenius.metrics.service.controller;

import com.redenius.metrics.service.dto.HistoryResponse;
import com.redenius.metrics.service.service.HistoryService;
import com.redenius.metrics.service.service.aggregation.AggregationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/metrics")
@Tag(name = "History", description = "Metric history with time-series aggregation")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get aggregated metric history for charting")
    public HistoryResponse getHistory(
            @PathVariable Long id,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(defaultValue = "1 hour") String step,
            @RequestParam(defaultValue = "AVG") AggregationType agg) {
        return historyService.getHistory(id, from, to, step, agg);
    }
}
