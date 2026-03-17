package com.studyplanner.service;

import com.studyplanner.dto.RetentionPrediction;
import com.studyplanner.ml.LogisticRegressionModel;
import com.studyplanner.model.RetentionFeatureVector;
import com.studyplanner.model.RetentionTrainingExample;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.RetentionTrainingDataRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.utils.ValidationUtils;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public class RetentionPredictionService {
    private static final int FEATURE_COUNT = 7;
    private static final int MIN_TRAINING_EXAMPLES = 8;
    private static final int RETRAIN_EVERY_N_EXAMPLES = 5;
    private static final double MIN_PROBABILITY = 0.05;
    private static final double MAX_PROBABILITY = 0.98;
    // Bootstrap coefficients give deterministic behavior before enough labeled outcomes exist.
    private static final double[] BOOTSTRAP_WEIGHTS = {-1.70, -0.60, 1.10, 1.60, 1.20, 0.90, 1.00};
    private static final double BOOTSTRAP_BIAS = -1.00;

    private final RetentionTrainingDataRepository trainingDataRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final LogisticRegressionModel model = new LogisticRegressionModel(FEATURE_COUNT);

    public RetentionPredictionService(RetentionTrainingDataRepository trainingDataRepository,
                                      StudySessionRepository studySessionRepository,
                                      ReviewRecordRepository reviewRecordRepository) {
        this.trainingDataRepository = trainingDataRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.model.setParameters(BOOTSTRAP_WEIGHTS, BOOTSTRAP_BIAS);
    }

    public void retrainModel() {
        model.setParameters(BOOTSTRAP_WEIGHTS, BOOTSTRAP_BIAS);
        List<RetentionTrainingExample> examples = trainingDataRepository.findAll();
        if (examples.size() < MIN_TRAINING_EXAMPLES || !hasBothClasses(examples)) {
            return;
        }

        List<double[]> features = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        for (RetentionTrainingExample example : examples) {
            features.add(transformFeatures(example.toFeatureVector()));
            labels.add(example.getLabel());
        }
        model.train(features, labels, 2200, 0.12, 0.01);
    }

    public RetentionPrediction predict(Topic topic) {
        return predict(topic, LocalDate.now());
    }

    public RetentionPrediction predict(Topic topic, LocalDate anchorDate) {
        ValidationUtils.require(anchorDate != null, "Anchor date is required");
        RetentionFeatureVector features = buildFeatureVector(topic, anchorDate);
        double probability = model.predictProbability(transformFeatures(features));
        probability = ValidationUtils.clamp(probability, MIN_PROBABILITY, MAX_PROBABILITY);
        return new RetentionPrediction(probability, describePrediction(topic, features, probability));
    }

    public double predictProbability(Topic topic) {
        return predict(topic).probability();
    }

    public double predictProbability(Topic topic, LocalDate anchorDate) {
        return predict(topic, anchorDate).probability();
    }

    public RetentionFeatureVector buildFeatureVector(Topic topic) {
        return buildFeatureVector(topic, LocalDate.now());
    }

    public RetentionFeatureVector buildFeatureVector(Topic topic, LocalDate anchorDate) {
        ValidationUtils.require(topic != null, "Topic is required");
        ValidationUtils.require(anchorDate != null, "Anchor date is required");

        List<StudySession> sessions = studySessionRepository.findByTopicId(topic.getId());
        double previousReviewQuality = reviewRecordRepository.findLatestByTopicId(topic.getId())
            .map(record -> (double) record.getQuality())
            .orElse(3.0);

        double consistency = completionConsistency(sessions);
        double averageSessionQuality = averageSessionQuality(sessions);
        double confidenceScore = ValidationUtils.clamp(topic.getConfidenceLevel() / 100.0, 0.0, 1.0);
        double daysSinceLastRevision = topic.getLastStudiedDate() == null
            ? 21.0
            : Math.max(0, ChronoUnit.DAYS.between(topic.getLastStudiedDate(), anchorDate));

        return new RetentionFeatureVector(
            daysSinceLastRevision,
            ValidationUtils.clamp(topic.getDifficulty(), 1, 5),
            ValidationUtils.clamp(previousReviewQuality, 0.0, 5.0),
            confidenceScore,
            consistency,
            Math.max(0, topic.getRepetitionCount()),
            averageSessionQuality
        );
    }

    public boolean captureTrainingExample(Topic topic, RetentionFeatureVector observationSnapshot,
                                          Integer reviewQuality, Double quizScore, LocalDate capturedOn) {
        return captureTrainingExample(topic, observationSnapshot, reviewQuality, quizScore, capturedOn, null);
    }

    public boolean captureTrainingExample(Topic topic, RetentionFeatureVector observationSnapshot,
                                          Integer reviewQuality, Double quizScore, LocalDate capturedOn,
                                          Long sourceSessionId) {
        boolean captured = captureTrainingExample(null, topic, observationSnapshot, reviewQuality, quizScore, capturedOn, sourceSessionId);
        if (!captured) {
            return false;
        }

        long sampleCount = trainingDataRepository.count();
        boolean shouldRetrain = !model.isTrained() || sampleCount < 24 || sampleCount % RETRAIN_EVERY_N_EXAMPLES == 0;
        if (shouldRetrain) {
            retrainModel();
        }
        return true;
    }

    public boolean captureTrainingExample(Connection connection, Topic topic, RetentionFeatureVector observationSnapshot,
                                          Integer reviewQuality, Double quizScore, LocalDate capturedOn,
                                          Long sourceSessionId) {
        ValidationUtils.require(topic != null, "Topic is required");
        ValidationUtils.require(observationSnapshot != null, "Observation snapshot is required");
        ValidationUtils.require(capturedOn != null, "Captured date is required");

        Integer label = deriveObjectiveLabel(reviewQuality, quizScore);
        if (label == null) {
            return false;
        }

        RetentionTrainingExample example = new RetentionTrainingExample();
        example.setTopicId(topic.getId());
        example.setSourceSessionId(sourceSessionId);
        example.setCapturedOn(capturedOn);
        example.setDaysSinceLastRevision(observationSnapshot.getDaysSinceLastRevision());
        example.setDifficulty(observationSnapshot.getDifficulty());
        example.setPreviousReviewQuality(observationSnapshot.getPreviousReviewQuality());
        example.setConfidenceScore(observationSnapshot.getConfidenceScore());
        example.setCompletionConsistency(observationSnapshot.getCompletionConsistency());
        example.setRepetitions(observationSnapshot.getRepetitions());
        example.setAverageSessionQuality(observationSnapshot.getAverageSessionQuality());
        example.setLabel(label);
        if (connection == null) {
            trainingDataRepository.save(example);
        } else {
            trainingDataRepository.save(connection, example);
        }
        return true;
    }

    public boolean isModelTrained() {
        return model.isTrained();
    }

    public double getTrainingAccuracy() {
        return model.getTrainingAccuracy();
    }

    private Integer deriveObjectiveLabel(Integer reviewQuality, Double quizScore) {
        if (reviewQuality != null) {
            return reviewQuality >= 3 ? 1 : 0;
        }
        if (quizScore != null) {
            return quizScore >= 65 ? 1 : 0;
        }
        return null;
    }

    private double[] transformFeatures(RetentionFeatureVector vector) {
        return new double[] {
            ValidationUtils.clamp(vector.getDaysSinceLastRevision() / 21.0, 0.0, 2.0),
            ValidationUtils.clamp(vector.getDifficulty() / 5.0, 0.0, 1.0),
            ValidationUtils.clamp(vector.getPreviousReviewQuality() / 5.0, 0.0, 1.0),
            ValidationUtils.clamp(vector.getConfidenceScore(), 0.0, 1.0),
            ValidationUtils.clamp(vector.getCompletionConsistency(), 0.0, 1.0),
            ValidationUtils.clamp(vector.getRepetitions() / 8.0, 0.0, 1.5),
            ValidationUtils.clamp(vector.getAverageSessionQuality(), 0.0, 1.0)
        };
    }

    private double completionConsistency(List<StudySession> sessions) {
        if (sessions.isEmpty()) {
            return 0.45;
        }
        List<StudySession> recent = sessions.stream()
            .sorted(Comparator.comparing(StudySession::getSessionDate).reversed())
            .limit(6)
            .toList();

        double total = 0.0;
        for (StudySession session : recent) {
            total += switch (session.getStatus()) {
                case COMPLETED -> 1.0;
                case PARTIALLY_COMPLETED -> 0.55;
                case PLANNED -> 0.25;
                case STARTED, PAUSED -> 0.25;
                case SKIPPED, ABANDONED -> 0.0;
            };
        }
        return ValidationUtils.clamp(total / recent.size(), 0.0, 1.0);
    }

    private double averageSessionQuality(List<StudySession> sessions) {
        OptionalDouble average = sessions.stream()
            .sorted(Comparator.comparing(StudySession::getSessionDate).reversed())
            .limit(5)
            .filter(session -> session.getStatus() == SessionStatus.COMPLETED
                || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .map(StudySession::getFocusQuality)
            .filter(quality -> quality != null)
            .mapToInt(Integer::intValue)
            .average();
        return ValidationUtils.clamp(average.orElse(3.0) / 5.0, 0.0, 1.0);
    }

    private String describePrediction(Topic topic, RetentionFeatureVector features, double probability) {
        List<String> drivers = new ArrayList<>();
        if (features.getDaysSinceLastRevision() >= 5) {
            drivers.add((int) features.getDaysSinceLastRevision() + " days since last revision");
        }
        if (topic.getConfidenceLevel() < 55) {
            drivers.add("confidence is " + Math.round(topic.getConfidenceLevel()) + "%");
        }
        if (features.getPreviousReviewQuality() <= 2.5) {
            drivers.add("recent review quality has been weak");
        }
        if (features.getCompletionConsistency() < 0.6) {
            drivers.add("completion consistency is below target");
        }
        if (features.getRepetitions() >= 4) {
            drivers.add("the topic already has several successful repetitions");
        }
        if (drivers.isEmpty()) {
            drivers.add("recent study signals are balanced");
        }

        String modelSource = model.isTrained()
            ? "Locally trained from labeled review and quiz outcomes"
            : "Bootstrap estimate using heuristic coefficients until enough labeled review or quiz outcomes exist";
        String prefix = probability >= 0.6 ? "Higher retention expected because " : "Lower retention expected because ";
        return prefix + String.join(", ", drivers.subList(0, Math.min(3, drivers.size())))
            + ". " + modelSource + ".";
    }

    private boolean hasBothClasses(List<RetentionTrainingExample> examples) {
        long distinctLabels = examples.stream()
            .map(RetentionTrainingExample::getLabel)
            .filter(label -> label == 0 || label == 1)
            .distinct()
            .count();
        return distinctLabels == 2;
    }
}
