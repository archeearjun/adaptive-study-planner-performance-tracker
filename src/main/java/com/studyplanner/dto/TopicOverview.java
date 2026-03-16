package com.studyplanner.dto;

import java.time.LocalDate;

public record TopicOverview(
    long topicId,
    long subjectId,
    String subjectName,
    String topicName,
    int priority,
    int difficulty,
    int estimatedStudyMinutes,
    LocalDate targetExamDate,
    double confidenceLevel,
    LocalDate lastStudiedDate,
    LocalDate nextReviewDate,
    boolean archived
) {
}
