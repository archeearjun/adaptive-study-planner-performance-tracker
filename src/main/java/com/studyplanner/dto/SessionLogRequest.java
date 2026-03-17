package com.studyplanner.dto;

import com.studyplanner.model.SessionStatus;

import java.time.LocalDate;

public record SessionLogRequest(
    Long sessionId,
    Long planItemId,
    long topicId,
    LocalDate sessionDate,
    int plannedMinutes,
    int actualMinutes,
    SessionStatus status,
    Integer focusQuality,
    Double confidenceAfter,
    Double quizScore,
    Integer reviewQuality,
    boolean reviewSession,
    String notes
) {
}
