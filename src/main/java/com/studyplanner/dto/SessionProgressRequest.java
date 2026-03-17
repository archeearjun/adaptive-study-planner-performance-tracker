package com.studyplanner.dto;

import java.time.LocalDateTime;

public record SessionProgressRequest(
    long sessionId,
    int actualMinutes,
    String notes,
    LocalDateTime occurredAt
) {
}
