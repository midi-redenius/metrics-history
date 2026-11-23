package com.redenius.metrics.service.service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record HistoryStep(String text, Duration duration) {

    private static final Pattern STEP_PATTERN = Pattern.compile(
            "^(\\d{1,9})\\s+(second|minute|hour|day|week)s?$",
            Pattern.CASE_INSENSITIVE
    );

    public static HistoryStep parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        Matcher matcher = STEP_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "step must match '<n> <unit>' where unit is second(s), minute(s), hour(s), day(s), or week(s)");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("step must be greater than zero");
        }
        ChronoUnit unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
            case "second" -> ChronoUnit.SECONDS;
            case "minute" -> ChronoUnit.MINUTES;
            case "hour" -> ChronoUnit.HOURS;
            case "day" -> ChronoUnit.DAYS;
            default -> ChronoUnit.WEEKS;
        };
        return new HistoryStep(text, unit.getDuration().multipliedBy(amount));
    }

    public long seconds() {
        return duration.toSeconds();
    }

    public String sqlInterval() {
        return seconds() + " seconds";
    }
}
