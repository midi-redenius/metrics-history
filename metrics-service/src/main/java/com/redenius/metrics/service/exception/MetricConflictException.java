package com.redenius.metrics.service.exception;

public class MetricConflictException extends RuntimeException {

    public MetricConflictException(String name, String host) {
        super("Metric already exists: name=" + name + ", host=" + host);
    }
}
