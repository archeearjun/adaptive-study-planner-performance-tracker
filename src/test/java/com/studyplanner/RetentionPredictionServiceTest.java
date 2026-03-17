package com.studyplanner;

import com.studyplanner.dto.RetentionPrediction;
import com.studyplanner.model.RetentionFeatureVector;
import com.studyplanner.model.RetentionTrainingExample;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DatabaseManager;
import com.studyplanner.persistence.RetentionTrainingDataRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.RetentionPredictionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPredictionServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void usesAnchorDateWhenBuildingFeatureVector() {
        TestContext context = createContext("anchor-date.db");
        Topic topic = saveTopic(context, LocalDate.of(2026, 3, 10));

        RetentionFeatureVector vector = context.service().buildFeatureVector(topic, LocalDate.of(2026, 3, 15));

        assertEquals(5.0, vector.getDaysSinceLastRevision(), 0.0001);
    }

    @Test
    void capturesOnlyObjectiveLabelsAndUsesTheProvidedObservationSnapshot() {
        TestContext context = createContext("objective-labels.db");
        Topic topic = saveTopic(context, LocalDate.of(2026, 3, 10));
        RetentionFeatureVector snapshot = new RetentionFeatureVector(4.0, 5.0, 2.0, 0.35, 0.5, 2.0, 0.8);

        boolean skippedCapture = context.service().captureTrainingExample(
            topic,
            snapshot,
            null,
            null,
            LocalDate.of(2026, 3, 16)
        );

        topic.setConfidenceLevel(99);
        boolean captured = context.service().captureTrainingExample(
            topic,
            snapshot,
            4,
            null,
            LocalDate.of(2026, 3, 16)
        );

        assertFalse(skippedCapture);
        assertTrue(captured);
        assertEquals(1, context.trainingDataRepository().count());

        RetentionTrainingExample stored = context.trainingDataRepository().findAll().get(0);
        assertEquals(4.0, stored.getDaysSinceLastRevision(), 0.0001);
        assertEquals(5.0, stored.getDifficulty(), 0.0001);
        assertEquals(2.0, stored.getPreviousReviewQuality(), 0.0001);
        assertEquals(0.35, stored.getConfidenceScore(), 0.0001);
        assertEquals(1, stored.getLabel());
    }

    @Test
    void onlyMarksTheModelAsTrainedWhenEnoughMixedLabelsExist() {
        TestContext context = createContext("training-threshold.db");
        Topic topic = saveTopic(context, LocalDate.of(2026, 3, 10));

        for (int index = 0; index < 8; index++) {
            context.trainingDataRepository().save(trainingExample(topic.getId(), index, 1));
        }
        context.service().retrainModel();
        RetentionPrediction bootstrapPrediction = context.service().predict(topic);

        assertFalse(context.service().isModelTrained());
        assertTrue(bootstrapPrediction.explanation().contains("Bootstrap estimate"));

        context.trainingDataRepository().save(trainingExample(topic.getId(), 8, 0));
        context.service().retrainModel();
        RetentionPrediction trainedPrediction = context.service().predict(topic);

        assertTrue(context.service().isModelTrained());
        assertTrue(trainedPrediction.explanation().contains("Locally trained"));
    }

    @Test
    void deduplicatesTrainingExamplesBySourceSession() {
        TestContext context = createContext("training-source-session.db");
        Topic topic = saveTopic(context, LocalDate.of(2026, 3, 10));

        StudySession session = new StudySession();
        session.setTopicId(topic.getId());
        session.setSubjectId(topic.getSubjectId());
        session.setSessionDate(LocalDate.of(2026, 3, 16));
        session.setStartedAt(LocalDateTime.of(2026, 3, 16, 18, 0));
        session.setUpdatedAt(LocalDateTime.of(2026, 3, 16, 18, 45));
        session.setEndedAt(LocalDateTime.of(2026, 3, 16, 18, 45));
        session.setPlannedMinutes(45);
        session.setActualMinutes(45);
        session.setStatus(SessionStatus.COMPLETED);
        session.setFocusQuality(4);
        session.setConfidenceAfter(74.0);
        session.setReviewSession(false);
        context.studySessionRepository().save(session);

        RetentionFeatureVector snapshot = new RetentionFeatureVector(4.0, 4.0, 3.0, 0.5, 0.6, 2.0, 0.8);
        context.service().captureTrainingExample(topic, snapshot, 4, null, LocalDate.of(2026, 3, 16), session.getId());
        context.service().captureTrainingExample(topic, snapshot, 2, null, LocalDate.of(2026, 3, 16), session.getId());

        assertEquals(1, context.trainingDataRepository().count());
        assertEquals(0, context.trainingDataRepository().findAll().get(0).getLabel());
    }

    private TestContext createContext(String databaseName) {
        DatabaseManager databaseManager = new DatabaseManager(tempDir.resolve(databaseName));
        databaseManager.initialize();

        SubjectRepository subjectRepository = new SubjectRepository(databaseManager);
        TopicRepository topicRepository = new TopicRepository(databaseManager);
        StudySessionRepository studySessionRepository = new StudySessionRepository(databaseManager);
        ReviewRecordRepository reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        RetentionTrainingDataRepository trainingDataRepository = new RetentionTrainingDataRepository(databaseManager);
        RetentionPredictionService service = new RetentionPredictionService(
            trainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        return new TestContext(subjectRepository, topicRepository, studySessionRepository, trainingDataRepository, service);
    }

    private Topic saveTopic(TestContext context, LocalDate lastStudiedDate) {
        Subject subject = context.subjectRepository().save(
            new Subject(0, "Machine Learning", "", "#2563EB", LocalDateTime.of(2026, 3, 1, 10, 0))
        );
        Topic topic = new Topic(
            0,
            subject.getId(),
            "Retention Topic",
            "",
            4,
            4,
            60,
            LocalDate.of(2026, 4, 10),
            58,
            lastStudiedDate,
            2.5,
            2,
            6,
            LocalDate.of(2026, 3, 18),
            false
        );
        return context.topicRepository().save(topic);
    }

    private RetentionTrainingExample trainingExample(long topicId, int daysOffset, int label) {
        RetentionTrainingExample example = new RetentionTrainingExample();
        example.setTopicId(topicId);
        example.setCapturedOn(LocalDate.of(2026, 3, 1).plusDays(daysOffset));
        example.setDaysSinceLastRevision(4 + daysOffset);
        example.setDifficulty(4);
        example.setPreviousReviewQuality(label == 1 ? 4 : 2);
        example.setConfidenceScore(label == 1 ? 0.8 : 0.35);
        example.setCompletionConsistency(label == 1 ? 0.85 : 0.3);
        example.setRepetitions(label == 1 ? 3 : 1);
        example.setAverageSessionQuality(label == 1 ? 0.8 : 0.45);
        example.setLabel(label);
        return example;
    }

    private record TestContext(
        SubjectRepository subjectRepository,
        TopicRepository topicRepository,
        StudySessionRepository studySessionRepository,
        RetentionTrainingDataRepository trainingDataRepository,
        RetentionPredictionService service
    ) {
    }
}
