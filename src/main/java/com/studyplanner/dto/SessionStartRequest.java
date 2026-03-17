package com.studyplanner.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SessionStartRequest(
    Long planItemId,
    long topicId,
    LocalDate sessionDate,
    LocalDateTime startedAt,
    int plannedMinutes,
    boolean reviewSession,
    String notes
) {
}
