package com.studyplanner.dto;

import java.time.LocalDate;

public record WeeklyStudyPoint(
    LocalDate date,
    int minutesStudied,
    int completedSessions
) {
}
