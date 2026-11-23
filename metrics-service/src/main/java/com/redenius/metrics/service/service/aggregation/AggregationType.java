package com.redenius.metrics.service.service.aggregation;

public enum AggregationType {
    AVG("AVG(value)"),
    MIN("MIN(value)"),
    MAX("MAX(value)"),
    P95("PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value)");

    private final String sqlExpression;

    AggregationType(String sqlExpression) {
        this.sqlExpression = sqlExpression;
    }

    public String sqlExpression() {
        return sqlExpression;
    }
}
