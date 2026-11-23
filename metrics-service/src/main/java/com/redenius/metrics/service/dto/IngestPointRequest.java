package com.redenius.metrics.service.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record IngestPointRequest(
        @NotNull Instant ts,
        @NotNull Double value
) {}
