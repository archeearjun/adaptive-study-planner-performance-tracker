package com.studyplanner.service;

import com.studyplanner.dto.DashboardSummary;
import com.studyplanner.dto.SubjectStudyBreakdown;
import com.studyplanner.dto.TopicAnalyticsSnapshot;
import com.studyplanner.dto.TopicOverview;
import com.studyplanner.dto.WeeklyStudyPoint;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.utils.DateUtils;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PerformanceAnalyticsService {
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final RetentionPredictionService retentionPredictionService;

    public PerformanceAnalyticsService(TopicRepository topicRepository, SubjectRepository subjectRepository,
                                       StudySessionRepository studySessionRepository,
                                       ReviewRecordRepository reviewRecordRepository,
                                       DailyPlanRepository dailyPlanRepository,
                                       RetentionPredictionService retentionPredictionService) {
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.retentionPredictionService = retentionPredictionService;
    }

    public DashboardSummary getDashboardSummary(LocalDate date) {
        Optional<DailyPlan> todayPlan = dailyPlanRepository.findByDate(date);
        List<Topic> activeTopics = topicRepository.findAll().stream().filter(topic -> !topic.isArchived()).toList();
        int loggedMinutesToday = studySessionRepository.findByDateRange(date, date).stream()
            .filter(session -> session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .mapToInt(StudySession::getActualMinutes)
            .sum();
        int overdueReviews = (int) activeTopics.stream()
            .filter(topic -> topic.getNextReviewDate() != null && topic.getNextReviewDate().isBefore(date))
            .count();
        double averageRecall = activeTopics.isEmpty()
            ? 0.0
            : activeTopics.stream().mapToDouble(retentionPredictionService::predictProbability).average().orElse(0.0);
        int tasksDueToday = todayPlan.map(plan -> plan.getItems().size()).orElseGet(() ->
            (int) activeTopics.stream()
                .filter(topic -> topic.getNextReviewDate() != null && !topic.getNextReviewDate().isAfter(date))
                .count()
        );

        return new DashboardSummary(
            todayPlan.map(DailyPlan::getTotalPlannedMinutes).orElse(0),
            loggedMinutesToday,
            overdueReviews,
            getStudyStreak(date),
            averageRecall,
            tasksDueToday
        );
    }

    public int getStudyStreak() {
        return getStudyStreak(LocalDate.now());
    }

    public int getStudyStreak(LocalDate referenceDate) {
        List<StudySession> sessions = studySessionRepository.findAll().stream()
            .filter(session -> session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .toList();
        if (sessions.isEmpty()) {
            return 0;
        }

        List<LocalDate> completedDates = sessions.stream()
            .map(StudySession::getSessionDate)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();

        int streak = 0;
        LocalDate cursor = referenceDate;
        for (LocalDate date : completedDates) {
            if (date.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else if (date.equals(cursor.minusDays(1)) && streak == 0) {
                streak++;
                cursor = date.minusDays(1);
            } else if (date.isBefore(cursor)) {
                break;
            }
        }
        return streak;
    }

    public List<WeeklyStudyPoint> getWeeklyStudyConsistency(int days) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(0, days - 1L));
        Map<LocalDate, List<StudySession>> byDate = studySessionRepository.findByDateRange(start, end).stream()
            .collect(Collectors.groupingBy(StudySession::getSessionDate, HashMap::new, Collectors.toList()));

        List<WeeklyStudyPoint> points = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            List<StudySession> sessions = byDate.getOrDefault(cursor, List.of());
            int studiedMinutes = sessions.stream().mapToInt(StudySession::getActualMinutes).sum();
            int completedSessions = (int) sessions.stream()
                .filter(session -> session.getStatus() == SessionStatus.COMPLETED)
                .count();
            points.add(new WeeklyStudyPoint(cursor, studiedMinutes, completedSessions));
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    public double getCompletionRate(LocalDate start, LocalDate end) {
        List<StudySession> sessions = studySessionRepository.findByDateRange(start, end);
        if (sessions.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        for (StudySession session : sessions) {
            score += switch (session.getStatus()) {
                case COMPLETED -> 1.0;
                case PARTIALLY_COMPLETED -> 0.5;
                case PLANNED, SKIPPED -> 0.0;
            };
        }
        return ValidationUtils.clamp(score / sessions.size(), 0.0, 1.0);
    }

    public int getTotalPomodorosCompleted() {
        return studySessionRepository.findAll().stream()
            .filter(session -> session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .mapToInt(session -> Math.max(1, (int) Math.round(session.getActualMinutes() / 25.0)))
            .sum();
    }

    public double getAverageSessionQuality() {
        return studySessionRepository.findAll().stream()
            .mapToInt(StudySession::getFocusQuality)
            .average()
            .orElse(0.0);
    }

    public List<SubjectStudyBreakdown> getSubjectBreakdown() {
        Map<Long, Subject> subjectsById = subjectRepository.findAll().stream()
            .collect(Collectors.toMap(Subject::getId, subject -> subject));
        Map<Long, Topic> topicsById = topicRepository.findAll().stream()
            .collect(Collectors.toMap(Topic::getId, topic -> topic));
        Map<Long, List<StudySession>> sessionsBySubject = new HashMap<>();

        for (StudySession session : studySessionRepository.findAll()) {
            Topic topic = topicsById.get(session.getTopicId());
            if (topic == null) {
                continue;
            }
            sessionsBySubject.computeIfAbsent(topic.getSubjectId(), ignored -> new ArrayList<>()).add(session);
        }

        List<SubjectStudyBreakdown> breakdown = new ArrayList<>();
        for (Map.Entry<Long, List<StudySession>> entry : sessionsBySubject.entrySet()) {
            List<StudySession> sessions = entry.getValue();
            int minutes = sessions.stream().mapToInt(StudySession::getActualMinutes).sum();
            int pomodoros = sessions.stream()
                .filter(session -> session.getStatus() != SessionStatus.SKIPPED)
                .mapToInt(session -> Math.max(1, (int) Math.round(session.getActualMinutes() / 25.0)))
                .sum();
            double completionRate = sessions.stream().mapToDouble(session -> switch (session.getStatus()) {
                case COMPLETED -> 1.0;
                case PARTIALLY_COMPLETED -> 0.5;
                case PLANNED, SKIPPED -> 0.0;
            }).average().orElse(0.0);

            breakdown.add(new SubjectStudyBreakdown(
                subjectsById.getOrDefault(entry.getKey(), new Subject(0, "Unknown Subject", "", "#64748B", null)).getName(),
                minutes,
                pomodoros,
                completionRate
            ));
        }

        breakdown.sort(Comparator.comparingInt(SubjectStudyBreakdown::totalMinutes).reversed());
        return breakdown;
    }

    public List<TopicAnalyticsSnapshot> getTopicAnalytics() {
        Map<Long, List<StudySession>> sessionsByTopic = studySessionRepository.findAll().stream()
            .collect(Collectors.groupingBy(StudySession::getTopicId, HashMap::new, Collectors.toList()));
        Map<Long, String> subjectNames = topicRepository.findAllOverviews().stream()
            .collect(Collectors.toMap(TopicOverview::topicId, TopicOverview::subjectName));

        return topicRepository.findAll().stream()
            .filter(topic -> !topic.isArchived())
            .map(topic -> {
                List<StudySession> sessions = sessionsByTopic.getOrDefault(topic.getId(), List.of());
                int totalSessions = sessions.size();
                int totalMinutes = sessions.stream().mapToInt(StudySession::getActualMinutes).sum();
                double averageConfidence = sessions.isEmpty()
                    ? topic.getConfidenceLevel()
                    : sessions.stream().mapToDouble(StudySession::getConfidenceAfter).average().orElse(topic.getConfidenceLevel());
                double revisionSuccessRate = reviewRecordRepository.findByTopicId(topic.getId()).stream()
                    .mapToInt(review -> review.getQuality() >= 3 ? 1 : 0)
                    .average()
                    .orElse(0.0);
                long daysSinceStudy = topic.getLastStudiedDate() == null ? 999 : DateUtils.daysSince(topic.getLastStudiedDate());
                String neglectSummary = topic.getLastStudiedDate() == null
                    ? "Never studied"
                    : "Last studied " + daysSinceStudy + " days ago";

                return new TopicAnalyticsSnapshot(
                    topic.getId(),
                    topic.getName(),
                    subjectNames.getOrDefault(topic.getId(), "Unknown Subject"),
                    totalSessions,
                    totalMinutes,
                    retentionPredictionService.predictProbability(topic),
                    topic.getNextReviewDate(),
                    averageConfidence,
                    revisionSuccessRate,
                    neglectSummary
                );
            })
            .sorted(Comparator.comparingDouble(TopicAnalyticsSnapshot::recallProbability)
                .thenComparing(TopicAnalyticsSnapshot::topicName))
            .toList();
    }

    public List<TopicOverview> getMostNeglectedTopics(int limit) {
        return topicRepository.findAllOverviews().stream()
            .filter(topic -> !topic.archived())
            .sorted(Comparator.comparingLong((TopicOverview topic) ->
                topic.lastStudiedDate() == null ? Long.MAX_VALUE : DateUtils.daysSince(topic.lastStudiedDate())
            ).reversed())
            .limit(limit)
            .toList();
    }
}
