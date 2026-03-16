package com.studyplanner.dto;

import java.time.LocalDate;

public record TimeSeriesPoint(
    LocalDate date,
    double value
) {
}
