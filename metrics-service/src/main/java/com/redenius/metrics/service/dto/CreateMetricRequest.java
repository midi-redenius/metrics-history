package com.redenius.metrics.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMetricRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 32) String unit,
        @NotBlank @Size(max = 128) String host
) {}
