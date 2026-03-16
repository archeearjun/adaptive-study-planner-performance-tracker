package com.studyplanner.utils;

import java.text.DecimalFormat;

public final class FormatUtils {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0");

    private FormatUtils() {
    }

    public static String percentage(double probability) {
        return DECIMAL_FORMAT.format(probability * 100.0) + "%";
    }

    public static String hoursFromMinutes(int minutes) {
        double hours = minutes / 60.0;
        return DECIMAL_FORMAT.format(hours) + "h";
    }

    public static String score(double value) {
        return DECIMAL_FORMAT.format(value);
    }
}
