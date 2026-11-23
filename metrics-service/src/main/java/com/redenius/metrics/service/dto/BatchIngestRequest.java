package com.redenius.metrics.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchIngestRequest(
        @NotEmpty @Size(max = 1000) List<@Valid IngestPointRequest> points
) {
}
