package com.studyplanner;

import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItemType;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.DatabaseManager;
import com.studyplanner.persistence.DemoDataSeeder;
import com.studyplanner.persistence.RetentionTrainingDataRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.RetentionPredictionService;
import com.studyplanner.service.pomodoro.PomodoroPlannerService;
import com.studyplanner.service.scheduler.SchedulerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerServiceIntegrationTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesPersistedPlanWithReviewBiasForRiskyTopics() {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("planner.db"));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        DailyPlanRepository dailyPlanRepository = new DailyPlanRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);

        new DemoDataSeeder(
            subjectRepository,
            topicRepository,
            studySessionRepository,
            reviewRecordRepository,
            trainingDataRepository
        ).seedIfEmpty();

        RetentionPredictionService retentionPredictionService = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        retentionPredictionService.retrainModel();

        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );

        DailyPlan plan = schedulerService.generateDailyPlan(new PlanGenerationRequest(LocalDate.now(), 180, 25, 5));

        assertFalse(plan.getItems().isEmpty());
        assertTrue(plan.getItems().stream().anyMatch(item -> item.getItemType() == PlanItemType.REVIEW));
        assertTrue(plan.getItems().stream().allMatch(item -> !item.getReason().isBlank()));
        assertTrue(dailyPlanRepository.findByDate(LocalDate.now()).isPresent());
    }

    @Test
    void regenerationSkipsTopicAlreadyCompletedToday() {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("planner-progress.db"));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        DailyPlanRepository dailyPlanRepository = new DailyPlanRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);

        new DemoDataSeeder(
            subjectRepository,
            topicRepository,
            studySessionRepository,
            reviewRecordRepository,
            trainingDataRepository
        ).seedIfEmpty();

        Subject subject = subjectRepository.findAll().get(0);
        Topic topic = new Topic(
            0,
            subject.getId(),
            "Portfolio Regression Topic",
            "Used to verify adaptive replanning after a logged session.",
            4,
            2,
            45,
            LocalDate.now().plusDays(10),
            82,
            LocalDate.now().minusDays(1),
            2.6,
            3,
            7,
            LocalDate.now().plusDays(3),
            false
        );
        topicRepository.save(topic);

        RetentionPredictionService retentionPredictionService = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        retentionPredictionService.retrainModel();

        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );

        DailyPlan firstPlan = schedulerService.generateDailyPlan(new PlanGenerationRequest(LocalDate.now(), 600, 25, 5));
        var plannedItem = firstPlan.getItems().stream()
            .filter(item -> item.getTopicId() == topic.getId())
            .findFirst()
            .orElse(null);
        assertNotNull(plannedItem);

        StudySession session = new StudySession();
        session.setTopicId(topic.getId());
        session.setSubjectId(topic.getSubjectId());
        session.setSessionDate(LocalDate.now());
        session.setStartedAt(LocalDateTime.now());
        session.setPlannedMinutes(plannedItem.getPlannedMinutes());
        session.setActualMinutes(plannedItem.getPlannedMinutes());
        session.setStatus(SessionStatus.COMPLETED);
        session.setFocusQuality(4);
        session.setConfidenceAfter(88.0);
        session.setReviewSession(false);
        session.setNotes("Completed before regenerating plan.");
        studySessionRepository.save(session);

        DailyPlan secondPlan = schedulerService.generateDailyPlan(new PlanGenerationRequest(LocalDate.now(), 600, 25, 5));
        assertTrue(secondPlan.getItems().stream().noneMatch(item -> item.getTopicId() == topic.getId()));
    }

    @Test
    void planningForAFutureDateUsesThatDateForRecallProbability() {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve("planner-future-date.db"));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        DailyPlanRepository dailyPlanRepository = new DailyPlanRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);

        Subject subject = subjectRepository.save(
            new Subject(0, "Algorithms", "", "#2563EB", LocalDateTime.of(2026, 3, 1, 9, 0))
        );
        Topic topic = topicRepository.save(new Topic(
            0,
            subject.getId(),
            "Future Recall Topic",
            "",
            4,
            3,
            50,
            LocalDate.of(2026, 4, 10),
            60,
            LocalDate.of(2026, 3, 10),
            2.5,
            2,
            6,
            LocalDate.of(2026, 3, 16),
            false
        ));

        RetentionPredictionService retentionPredictionService = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        retentionPredictionService.retrainModel();

        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );

        LocalDate planDate = LocalDate.of(2026, 3, 20);
        DailyPlan plan = schedulerService.generateDailyPlan(new PlanGenerationRequest(planDate, 120, 25, 5));

        var plannedItem = plan.getItems().stream()
            .filter(item -> item.getTopicId() == topic.getId())
            .findFirst()
            .orElseThrow();

        assertEquals(
            retentionPredictionService.predictProbability(topic, planDate),
            plannedItem.getRecallProbability(),
            0.0000001
        );
    }

    @Test
    void overdueReviewIsRankedAheadOfNormalStudyWhenTimeIsTight() {
        SchedulerTestContext context = createSchedulerContext("planner-overdue-priority.db");
        Subject subject = saveSubject(context, "Systems");
        LocalDate today = LocalDate.of(2026, 3, 17);

        Topic overdueTopic = context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            "Overdue Review Topic",
            "",
            1,
            3,
            60,
            today.plusDays(40),
            42,
            today.minusDays(24),
            2.5,
            3,
            10,
            today.minusDays(18),
            false
        ));
        context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            "High Priority Study Topic",
            "",
            5,
            3,
            60,
            today.plusDays(30),
            95,
            today.minusDays(1),
            2.5,
            0,
            0,
            null,
            false
        ));

        DailyPlan plan = context.schedulerService().generateDailyPlan(new PlanGenerationRequest(today, 35, 25, 5));

        assertEquals(1, plan.getItems().size());
        assertEquals(overdueTopic.getId(), plan.getItems().get(0).getTopicId());
        assertEquals(PlanItemType.REVIEW, plan.getItems().get(0).getItemType());
        assertTrue(plan.getItems().get(0).getReason().contains("review overdue"));
    }

    @Test
    void missedPlannedWorkRaisesTopicPriorityOnTheNextDay() {
        SchedulerTestContext context = createSchedulerContext("planner-missed-work.db");
        Subject subject = saveSubject(context, "Databases");
        LocalDate today = LocalDate.of(2026, 3, 17);
        LocalDate yesterday = today.minusDays(1);

        Topic carriedForwardTopic = context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            "Carry Forward Topic",
            "",
            3,
            3,
            50,
            today.plusDays(25),
            70,
            null,
            2.5,
            0,
            0,
            null,
            false
        ));
        context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            "Fresh Topic",
            "",
            3,
            3,
            50,
            today.plusDays(25),
            70,
            null,
            2.5,
            0,
            0,
            null,
            false
        ));

        saveHistoricalPlan(context, yesterday, carriedForwardTopic, SessionStatus.PLANNED, PlanItemType.STUDY, 30);

        DailyPlan plan = context.schedulerService().generateDailyPlan(new PlanGenerationRequest(today, 35, 25, 5));

        assertEquals(1, plan.getItems().size());
        assertEquals(carriedForwardTopic.getId(), plan.getItems().get(0).getTopicId());
        assertTrue(plan.getItems().get(0).getReason().contains("missed earlier planned work"));
    }

    @Test
    void rejectsPlanRequestsBelowMinimumSchedulableMinutes() {
        SchedulerTestContext context = createSchedulerContext("planner-minutes-validation.db");

        assertThrows(
            IllegalArgumentException.class,
            () -> context.schedulerService().generateDailyPlan(new PlanGenerationRequest(LocalDate.of(2026, 3, 17), 10, 25, 5))
        );
    }

    @Test
    void stalePlanIsRegeneratedUsingPersistedPlannerSettings() {
        SchedulerTestContext context = createSchedulerContext("planner-stale-regeneration.db");
        Subject subject = saveSubject(context, "Networks");
        LocalDate planDate = LocalDate.of(2026, 3, 17);

        context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            "Routing",
            "",
            4,
            3,
            60,
            planDate.plusDays(7),
            62,
            planDate.minusDays(2),
            2.5,
            2,
            6,
            planDate.plusDays(2),
            false
        ));

        DailyPlan generated = context.schedulerService().generateDailyPlan(new PlanGenerationRequest(planDate, 150, 30, 10));
        LocalDateTime originalGeneratedAt = generated.getGeneratedAt();
        context.dailyPlanRepository().markPlansFromDateStale(planDate);

        DailyPlan refreshed = context.schedulerService().getPlan(planDate).orElseThrow();

        assertFalse(refreshed.isStale());
        assertEquals(150, refreshed.getAvailableMinutes());
        assertEquals(30, refreshed.getFocusMinutes());
        assertEquals(10, refreshed.getShortBreakMinutes());
        assertNotEquals(originalGeneratedAt, refreshed.getGeneratedAt());
    }

    private SchedulerTestContext createSchedulerContext(String databaseName) {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve(databaseName));
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
        retentionPredictionService.retrainModel();

        SchedulerService schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            new PomodoroPlannerService()
        );

        return new SchedulerTestContext(
            subjectRepository,
            topicRepository,
            dailyPlanRepository,
            schedulerService
        );
    }

    private Subject saveSubject(SchedulerTestContext context, String name) {
        return context.subjectRepository().save(
            new Subject(0, name, "", "#2563EB", LocalDateTime.of(2026, 3, 1, 9, 0))
        );
    }

    private void saveHistoricalPlan(SchedulerTestContext context, LocalDate planDate, Topic topic,
                                    SessionStatus status, PlanItemType itemType, int plannedMinutes) {
        DailyPlan plan = new DailyPlan();
        plan.setPlanDate(planDate);
        plan.setAvailableMinutes(plannedMinutes);
        plan.setTotalPlannedMinutes(plannedMinutes);
        plan.setGeneratedAt(LocalDateTime.of(planDate, java.time.LocalTime.of(7, 0)));
        plan.setSummary("Historical test plan");

        com.studyplanner.model.PlanItem item = new com.studyplanner.model.PlanItem();
        item.setTopicId(topic.getId());
        item.setSubjectId(topic.getSubjectId());
        item.setTopicName(topic.getName());
        item.setSubjectName(context.subjectRepository().findById(topic.getSubjectId()).orElseThrow().getName());
        item.setItemType(itemType);
        item.setRecommendedOrder(1);
        item.setPlannedMinutes(plannedMinutes);
        item.setScore(0.5);
        item.setReason("Historical carry-forward test");
        item.setPomodoroCount(0);
        item.setRecallProbability(0.5);
        item.setStatus(status);
        item.setCompletedMinutes(status == SessionStatus.PARTIALLY_COMPLETED ? plannedMinutes / 2 : 0);
        item.setPomodoroBlocks(List.of());
        plan.setItems(List.of(item));

        context.dailyPlanRepository().saveOrReplace(plan);
    }

    private record SchedulerTestContext(
        SubjectRepository subjectRepository,
        TopicRepository topicRepository,
        DailyPlanRepository dailyPlanRepository,
        SchedulerService schedulerService
    ) {
    }
}
