package com.studyplanner.dto;

import com.studyplanner.model.SessionStatus;

import java.time.LocalDate;

public record SessionLogRequest(
    Long planItemId,
    long topicId,
    LocalDate sessionDate,
    int plannedMinutes,
    int actualMinutes,
    SessionStatus status,
    int focusQuality,
    double confidenceAfter,
    Double quizScore,
    Integer reviewQuality,
    boolean reviewSession,
    String notes
) {
}
