package com.studyplanner.dto;

import java.time.LocalDate;

public record TopicAnalyticsSnapshot(
    long topicId,
    String topicName,
    String subjectName,
    int totalSessions,
    int totalTimeSpentMinutes,
    double recallProbability,
    LocalDate nextReviewDate,
    double averageConfidence,
    double revisionSuccessRate,
    String neglectSummary
) {
}
