package com.studyplanner.dto;

public record SubjectStudyBreakdown(
    String subjectName,
    int totalMinutes,
    int totalPomodoros,
    double completionRate
) {
}
