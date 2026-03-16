package com.studyplanner.dto;

import java.time.LocalDate;

public record PlanGenerationRequest(
    LocalDate planDate,
    int availableMinutes,
    int focusMinutes,
    int shortBreakMinutes
) {
}
