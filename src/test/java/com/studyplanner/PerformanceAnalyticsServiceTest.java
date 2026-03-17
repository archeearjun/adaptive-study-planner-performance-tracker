package com.studyplanner;

import com.studyplanner.dto.DashboardSummary;
import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.DatabaseManager;
import com.studyplanner.persistence.RetentionTrainingDataRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.PerformanceAnalyticsService;
import com.studyplanner.service.RetentionPredictionService;
import com.studyplanner.service.pomodoro.PomodoroPlannerService;
import com.studyplanner.service.scheduler.SchedulerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceAnalyticsServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void subjectBreakdownUsesSessionSubjectSnapshotAfterTopicMovesSubjects() {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("analytics-subject-snapshot.db"));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        DailyPlanRepository dailyPlanRepository = new DailyPlanRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);
        RetentionPredictionService retentionPredictionService = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );

        PerformanceAnalyticsService analyticsService = new PerformanceAnalyticsService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            reviewRecordRepository,
            schedulerService,
            retentionPredictionService
        );

        Subject originalSubject = subjectRepository.save(
            new Subject(0, "Math", "", "#2563EB", LocalDateTime.of(2026, 3, 1, 9, 0))
        );
        Subject newSubject = subjectRepository.save(
            new Subject(0, "Physics", "", "#059669", LocalDateTime.of(2026, 3, 1, 9, 5))
        );

        Topic topic = topicRepository.save(new Topic(
            0,
            originalSubject.getId(),
            "Series",
            "",
            4,
            3,
            50,
            LocalDate.of(2026, 4, 10),
            64,
            LocalDate.of(2026, 3, 15),
            2.5,
            1,
            3,
            LocalDate.of(2026, 3, 20),
            false
        ));

        StudySession session = new StudySession();
        session.setTopicId(topic.getId());
        session.setSubjectId(originalSubject.getId());
        session.setSessionDate(LocalDate.of(2026, 3, 16));
        session.setStartedAt(LocalDateTime.of(2026, 3, 16, 18, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 3, 16, 18, 45));
        session.setEndedAt(LocalDateTime.of(2026, 3, 16, 18, 45));
        session.setPlannedMinutes(45);
        session.setActualMinutes(45);
        session.setStatus(SessionStatus.COMPLETED);
        session.setFocusQuality(4);
        session.setConfidenceAfter(72.0);
        session.setReviewSession(false);
        studySessionRepository.save(session);

        topic.setSubjectId(newSubject.getId());
        topicRepository.save(topic);

        var breakdown = analyticsService.getSubjectBreakdown();

        assertTrue(breakdown.stream().anyMatch(entry -> entry.subjectName().equals("Math") && entry.totalMinutes() == 45));
        assertFalse(breakdown.stream().anyMatch(entry -> entry.subjectName().equals("Physics") && entry.totalMinutes() == 45));
        assertEquals(45, breakdown.stream()
            .filter(entry -> entry.subjectName().equals("Math"))
            .findFirst()
            .orElseThrow()
            .totalMinutes());
    }

    @Test
    void dashboardSummaryRegeneratesStalePlanBeforeReadingPlannedMinutes() {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("analytics-stale-dashboard.db"));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        DailyPlanRepository dailyPlanRepository = new DailyPlanRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);
        RetentionPredictionService retentionPredictionService = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );
        PerformanceAnalyticsService analyticsService = new PerformanceAnalyticsService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            reviewRecordRepository,
            schedulerService,
            retentionPredictionService
        );

        Subject subject = subjectRepository.save(
            new Subject(0, "Biology", "", "#16A34A", LocalDateTime.of(2026, 3, 1, 8, 0))
        );
        topicRepository.save(new Topic(
            0,
            subject.getId(),
            "Cells",
            "",
            4,
            3,
            45,
            LocalDate.of(2026, 4, 12),
            58,
            LocalDate.of(2026, 3, 14),
            2.5,
            1,
            3,
            LocalDate.of(2026, 3, 17),
            false
        ));

        LocalDate planDate = LocalDate.of(2026, 3, 18);
        schedulerService.generateDailyPlan(new PlanGenerationRequest(planDate, 90, 30, 10));
        dailyPlanRepository.markPlansFromDateStale(planDate);
        assertTrue(dailyPlanRepository.findByDate(planDate).orElseThrow().isStale());

        DashboardSummary summary = analyticsService.getDashboardSummary(planDate);

        assertFalse(dailyPlanRepository.findByDate(planDate).orElseThrow().isStale());
        assertTrue(summary.todayPlannedMinutes() > 0);
        assertTrue(summary.tasksDueToday() > 0);
    }
}
