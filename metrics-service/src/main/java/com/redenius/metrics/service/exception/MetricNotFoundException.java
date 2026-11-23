package com.redenius.metrics.service.exception;

public class MetricNotFoundException extends RuntimeException {

    public MetricNotFoundException(Long id) {
        super("Metric not found: id=" + id);
    }
}
