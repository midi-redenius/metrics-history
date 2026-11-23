package com.redenius.metrics.service.service;

import com.redenius.metrics.service.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryStepTest {

    @ParameterizedTest
    @CsvSource({
            "30 seconds, PT30S",
            "1 minute, PT1M",
            "15 MINUTES, PT15M",
            "' 6 hours ', PT6H",
            "1 day, PT24H",
            "2 weeks, PT336H"
    })
    void shouldParseStep(String raw, String expected) {
        assertThat(HistoryStep.parse(raw).duration()).isEqualTo(Duration.parse(expected));
    }

    @Test
    void shouldTreatSingularAndPluralAsSameDuration() {
        assertThat(HistoryStep.parse("1 minute").seconds()).isEqualTo(HistoryStep.parse("1 minutes").seconds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "hour", "1 month", "1h", "-1 minute", "drop table", "1 minute; select 1"})
    void shouldRejectMalformedStep(String raw) {
        assertThatThrownBy(() -> HistoryStep.parse(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step must match");
    }

    @Test
    void shouldRejectNullStep() {
        assertThatThrownBy(() -> HistoryStep.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectZeroStep() {
        assertThatThrownBy(() -> HistoryStep.parse("0 seconds"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @ParameterizedTest
    @CsvSource({
            "30 seconds, history-short",
            "5 minutes, history-short",
            "10 minutes, history-medium",
            "59 minutes, history-medium",
            "60 minutes, history-long",
            "1 hour, history-long",
            "1 week, history-long"
    })
    void shouldResolveCacheNameByDuration(String raw, String expected) {
        assertThat(CacheConfig.resolveCacheName(HistoryStep.parse(raw))).isEqualTo(expected);
    }
}
