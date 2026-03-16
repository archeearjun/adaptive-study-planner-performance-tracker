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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        session.setSessionDate(LocalDate.now());
        session.setStartedAt(LocalDateTime.now());
        session.setPlannedMinutes(plannedItem.getPlannedMinutes());
        session.setActualMinutes(plannedItem.getPlannedMinutes());
        session.setStatus(SessionStatus.COMPLETED);
        session.setFocusQuality(4);
        session.setConfidenceAfter(88);
        session.setReviewSession(false);
        session.setNotes("Completed before regenerating plan.");
        studySessionRepository.save(session);

        DailyPlan secondPlan = schedulerService.generateDailyPlan(new PlanGenerationRequest(LocalDate.now(), 600, 25, 5));
        assertTrue(secondPlan.getItems().stream().noneMatch(item -> item.getTopicId() == topic.getId()));
    }
}
