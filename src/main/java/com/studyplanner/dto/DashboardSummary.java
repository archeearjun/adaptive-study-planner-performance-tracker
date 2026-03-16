package com.studyplanner.dto;

public record DashboardSummary(
    int todayPlannedMinutes,
    int todayLoggedMinutes,
    int overdueReviews,
    int studyStreak,
    double averageRecallProbability,
    int tasksDueToday
) {
}
