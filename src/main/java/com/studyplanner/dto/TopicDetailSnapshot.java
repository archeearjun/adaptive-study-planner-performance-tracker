package com.studyplanner.dto;

import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Topic;

import java.util.List;

public record TopicDetailSnapshot(
    Topic topic,
    String subjectName,
    int totalSessions,
    int totalTimeSpentMinutes,
    double recallProbability,
    double revisionSuccessRate,
    double averageConfidence,
    List<TimeSeriesPoint> confidenceTrend,
    List<StudySession> recentSessions,
    List<ReviewRecord> reviewHistory
) {
}
