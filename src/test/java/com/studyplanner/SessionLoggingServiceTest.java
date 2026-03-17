package com.studyplanner;

import com.studyplanner.dto.SessionLogRequest;
import com.studyplanner.dto.SessionProgressRequest;
import com.studyplanner.dto.SessionStartRequest;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.PlanItemType;
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
import com.studyplanner.service.RetentionPredictionService;
import com.studyplanner.service.SessionLoggingService;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLoggingServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void startPauseResumeAndCompleteUseOnePersistedSessionRow() {
        TestContext context = createContext("session-lifecycle.db");
        LocalDate sessionDate = LocalDate.of(2026, 3, 17);
        Topic topic = saveTopic(context, "Lifecycle Topic", sessionDate.minusDays(2), 52);
        DailyPlan plan = savePlan(context, topic, sessionDate, 45);
        long planItemId = plan.getItems().get(0).getId();

        StudySession started = context.sessionLoggingService().startSession(new SessionStartRequest(
            planItemId,
            topic.getId(),
            sessionDate,
            sessionDate.atTime(18, 0),
            45,
            false,
            "Started focused block"
        ));

        assertEquals(SessionStatus.STARTED, started.getStatus());
        assertThrows(IllegalArgumentException.class, () -> context.sessionLoggingService().startSession(new SessionStartRequest(
            planItemId,
            topic.getId(),
            sessionDate,
            sessionDate.atTime(18, 5),
            45,
            false,
            "Duplicate start"
        )));

        StudySession paused = context.sessionLoggingService().pauseSession(new SessionProgressRequest(
            started.getId(),
            20,
            "Paused after interruption",
            sessionDate.atTime(18, 25)
        ));
        assertEquals(SessionStatus.PAUSED, paused.getStatus());
        assertEquals(20, paused.getActualMinutes());

        StudySession resumed = context.sessionLoggingService().resumeSession(started.getId(), sessionDate.atTime(18, 35));
        assertEquals(SessionStatus.STARTED, resumed.getStatus());

        StudySession completed = context.sessionLoggingService().logSession(new SessionLogRequest(
            started.getId(),
            planItemId,
            topic.getId(),
            sessionDate,
            45,
            45,
            SessionStatus.COMPLETED,
            4,
            78.0,
            82.0,
            null,
            false,
            "Finished successfully"
        ));

        assertEquals(SessionStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getEndedAt());

        List<StudySession> sessions = context.studySessionRepository().findAll();
        assertEquals(1, sessions.size());
        assertEquals(SessionStatus.COMPLETED, sessions.get(0).getStatus());
        assertEquals(45, sessions.get(0).getActualMinutes());

        DailyPlan persistedPlan = context.dailyPlanRepository().findByDate(sessionDate).orElseThrow();
        PlanItem updatedItem = persistedPlan.getItems().get(0);
        assertEquals(SessionStatus.COMPLETED, updatedItem.getStatus());
        assertEquals(45, updatedItem.getCompletedMinutes());

        Topic updatedTopic = context.topicRepository().findById(topic.getId()).orElseThrow();
        assertEquals(sessionDate, updatedTopic.getLastStudiedDate());
        assertEquals(78.0, updatedTopic.getConfidenceLevel(), 0.0001);
    }

    @Test
    void skippedSessionDoesNotUpdateTopicStudyStateOrTrainingData() {
        TestContext context = createContext("session-skip.db");
        LocalDate sessionDate = LocalDate.of(2026, 3, 17);
        Topic topic = saveTopic(context, "Skip Topic", sessionDate.minusDays(6), 41);

        StudySession skipped = context.sessionLoggingService().logSession(new SessionLogRequest(
            null,
            null,
            topic.getId(),
            sessionDate,
            30,
            0,
            SessionStatus.SKIPPED,
            null,
            null,
            null,
            null,
            false,
            "Skipped planned work"
        ));

        Topic reloadedTopic = context.topicRepository().findById(topic.getId()).orElseThrow();
        assertEquals(topic.getLastStudiedDate(), reloadedTopic.getLastStudiedDate());
        assertEquals(topic.getConfidenceLevel(), reloadedTopic.getConfidenceLevel(), 0.0001);
        assertEquals(0, context.trainingDataRepository().count());
        assertEquals(SessionStatus.SKIPPED, skipped.getStatus());
        assertNull(skipped.getFocusQuality());
        assertNull(skipped.getConfidenceAfter());
    }

    @Test
    void staleOpenSessionsAreAbandonedOnRecovery() {
        TestContext context = createContext("session-stale-recovery.db");
        LocalDate yesterday = LocalDate.of(2026, 3, 16);
        Topic topic = saveTopic(context, "Stale Topic", yesterday.minusDays(2), 55);

        StudySession started = context.sessionLoggingService().startSession(new SessionStartRequest(
            null,
            topic.getId(),
            yesterday,
            LocalDateTime.of(yesterday, LocalTime.of(19, 0)),
            35,
            false,
            "Forgot to finish"
        ));

        int abandoned = context.sessionLoggingService().abandonStaleSessions(yesterday.plusDays(1));
        StudySession recovered = context.studySessionRepository().findById(started.getId()).orElseThrow();

        assertEquals(1, abandoned);
        assertEquals(SessionStatus.ABANDONED, recovered.getStatus());
        assertNotNull(recovered.getEndedAt());
        assertTrue(recovered.getNotes().contains("Automatically abandoned"));
    }

    private TestContext createContext(String databaseName) {
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

        SessionLoggingService sessionLoggingService = new SessionLoggingService(
            databaseManager,
            topicRepository,
            studySessionRepository,
            reviewRecordRepository,
            dailyPlanRepository,
            new SpacedRepetitionService(),
            retentionPredictionService
        );

        return new TestContext(
            subjectRepository,
            topicRepository,
            studySessionRepository,
            dailyPlanRepository,
            trainingDataRepository,
            sessionLoggingService
        );
    }

    private Topic saveTopic(TestContext context, String topicName, LocalDate lastStudiedDate, double confidence) {
        Subject subject = context.subjectRepository().save(
            new Subject(0, topicName + " Subject", "", "#2563EB", LocalDateTime.of(2026, 3, 1, 9, 0))
        );
        return context.topicRepository().save(new Topic(
            0,
            subject.getId(),
            topicName,
            "",
            4,
            3,
            45,
            LocalDate.of(2026, 4, 10),
            confidence,
            lastStudiedDate,
            2.5,
            1,
            3,
            LocalDate.of(2026, 3, 20),
            false
        ));
    }

    private DailyPlan savePlan(TestContext context, Topic topic, LocalDate planDate, int plannedMinutes) {
        DailyPlan plan = new DailyPlan();
        plan.setPlanDate(planDate);
        plan.setAvailableMinutes(plannedMinutes);
        plan.setTotalPlannedMinutes(plannedMinutes);
        plan.setGeneratedAt(LocalDateTime.of(planDate, LocalTime.of(7, 0)));
        plan.setSummary("Session lifecycle test plan");

        Subject subject = context.subjectRepository().findById(topic.getSubjectId()).orElseThrow();
        PlanItem item = new PlanItem();
        item.setTopicId(topic.getId());
        item.setSubjectId(topic.getSubjectId());
        item.setTopicName(topic.getName());
        item.setSubjectName(subject.getName());
        item.setItemType(PlanItemType.STUDY);
        item.setRecommendedOrder(1);
        item.setPlannedMinutes(plannedMinutes);
        item.setScore(0.75);
        item.setReason("Test item");
        item.setPomodoroCount(0);
        item.setRecallProbability(0.5);
        item.setStatus(SessionStatus.PLANNED);
        item.setCompletedMinutes(0);
        item.setPomodoroBlocks(List.of());
        plan.setItems(List.of(item));

        return context.dailyPlanRepository().saveOrReplace(plan);
    }

    private record TestContext(
        SubjectRepository subjectRepository,
        TopicRepository topicRepository,
        StudySessionRepository studySessionRepository,
        DailyPlanRepository dailyPlanRepository,
        RetentionTrainingDataRepository trainingDataRepository,
        SessionLoggingService sessionLoggingService
    ) {
    }
}
