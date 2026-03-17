package com.studyplanner.model;

import java.time.LocalDate;

public record PlanHistoryEntry(
    LocalDate planDate,
    SessionStatus status,
    int plannedMinutes,
    int completedMinutes,
    PlanItemType itemType
) {
}
