package com.redenius.metrics.service.service.aggregation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AggregationTypeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "AVG | AVG(value)",
            "MIN | MIN(value)",
            "MAX | MAX(value)",
            "P95 | PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY value)"
    })
    void shouldMapToSqlAggregate(AggregationType type, String expected) {
        assertThat(type.sqlExpression()).isEqualTo(expected);
    }
}
