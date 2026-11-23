package com.redenius.metrics.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MetricPointMessage(
        @NotNull @JsonProperty("metricId") Long metricId,
        @NotNull @JsonProperty("ts") Instant ts,
        @NotNull @JsonProperty("value") Double value
) {}
