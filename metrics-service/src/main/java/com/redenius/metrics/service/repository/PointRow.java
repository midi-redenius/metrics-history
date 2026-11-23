package com.redenius.metrics.service.repository;

import java.time.Instant;

public record PointRow(Long metricId, Instant ts, double value) {}
