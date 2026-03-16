package com.studyplanner.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class DateUtils {
    private DateUtils() {
    }

    public static long daysUntil(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    public static long daysSince(LocalDate date) {
        if (date == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(date, LocalDate.now());
    }

    public static String toIsoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    public static String toIsoDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toString();
    }

    public static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    public static LocalDateTime parseDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }
}
