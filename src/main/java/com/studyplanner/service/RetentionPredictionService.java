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
import com.studyplanner.utils.DateUtils;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public class RetentionPredictionService {
    private static final int FEATURE_COUNT = 7;
    private static final double[] DEFAULT_WEIGHTS = {-1.70, -0.60, 1.10, 1.60, 1.20, 0.90, 1.00};
    private static final double DEFAULT_BIAS = -1.00;

    private final RetentionTrainingDataRepository trainingDataRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final LogisticRegressionModel model = new LogisticRegressionModel(FEATURE_COUNT);
    private long lastTrainingSampleCount;

    public RetentionPredictionService(RetentionTrainingDataRepository trainingDataRepository,
                                      StudySessionRepository studySessionRepository,
                                      ReviewRecordRepository reviewRecordRepository) {
        this.trainingDataRepository = trainingDataRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.model.setParameters(DEFAULT_WEIGHTS, DEFAULT_BIAS);
    }

    public void retrainModel() {
        model.setParameters(DEFAULT_WEIGHTS, DEFAULT_BIAS);
        List<RetentionTrainingExample> examples = trainingDataRepository.findAll();
        lastTrainingSampleCount = examples.size();
        if (examples.size() < 8) {
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
        RetentionFeatureVector features = buildFeatureVector(topic);
        double probability = model.predictProbability(transformFeatures(features));
        probability = ValidationUtils.clamp(probability, 0.05, 0.98);
        return new RetentionPrediction(probability, describePrediction(topic, features, probability));
    }

    public double predictProbability(Topic topic) {
        return predict(topic).probability();
    }

    public RetentionFeatureVector buildFeatureVector(Topic topic) {
        List<StudySession> sessions = studySessionRepository.findByTopicId(topic.getId());
        double previousReviewQuality = reviewRecordRepository.findLatestByTopicId(topic.getId())
            .map(record -> (double) record.getQuality())
            .orElse(3.0);

        double consistency = completionConsistency(sessions);
        double averageSessionQuality = averageSessionQuality(sessions);
        double confidenceScore = ValidationUtils.clamp(topic.getConfidenceLevel() / 100.0, 0.0, 1.0);
        double daysSinceLastRevision = topic.getLastStudiedDate() == null
            ? 21.0
            : Math.max(0, DateUtils.daysSince(topic.getLastStudiedDate()));

        return new RetentionFeatureVector(
            daysSinceLastRevision,
            topic.getDifficulty(),
            previousReviewQuality,
            confidenceScore,
            consistency,
            topic.getRepetitionCount(),
            averageSessionQuality
        );
    }

    public void captureTrainingExample(Topic topic, Integer reviewQuality, SessionStatus status, Double quizScore) {
        RetentionFeatureVector vector = buildFeatureVector(topic);
        RetentionTrainingExample example = new RetentionTrainingExample();
        example.setTopicId(topic.getId());
        example.setCapturedOn(LocalDate.now());
        example.setDaysSinceLastRevision(vector.getDaysSinceLastRevision());
        example.setDifficulty(vector.getDifficulty());
        example.setPreviousReviewQuality(reviewQuality != null ? reviewQuality : vector.getPreviousReviewQuality());
        example.setConfidenceScore(vector.getConfidenceScore());
        example.setCompletionConsistency(vector.getCompletionConsistency());
        example.setRepetitions(vector.getRepetitions());
        example.setAverageSessionQuality(vector.getAverageSessionQuality());
        example.setLabel(deriveLabel(reviewQuality, status, quizScore, topic.getConfidenceLevel()));
        trainingDataRepository.save(example);

        long sampleCount = lastTrainingSampleCount + 1;
        boolean shouldRetrain = !model.isTrained() || sampleCount < 24 || sampleCount % 5 == 0;
        if (shouldRetrain) {
            retrainModel();
        } else {
            lastTrainingSampleCount = sampleCount;
        }
    }

    public boolean isModelTrained() {
        return model.isTrained();
    }

    public double getTrainingAccuracy() {
        return model.getTrainingAccuracy();
    }

    private int deriveLabel(Integer reviewQuality, SessionStatus status, Double quizScore, double confidenceAfter) {
        if (reviewQuality != null) {
            return reviewQuality >= 3 ? 1 : 0;
        }
        if (quizScore != null) {
            return quizScore >= 65 ? 1 : 0;
        }
        if (status == SessionStatus.SKIPPED) {
            return 0;
        }
        return confidenceAfter >= 60 ? 1 : 0;
    }

    private double[] transformFeatures(RetentionFeatureVector vector) {
        return new double[] {
            Math.min(vector.getDaysSinceLastRevision() / 21.0, 2.0),
            vector.getDifficulty() / 5.0,
            vector.getPreviousReviewQuality() / 5.0,
            vector.getConfidenceScore(),
            vector.getCompletionConsistency(),
            Math.min(vector.getRepetitions() / 8.0, 1.5),
            vector.getAverageSessionQuality()
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
                case SKIPPED -> 0.0;
            };
        }
        return ValidationUtils.clamp(total / recent.size(), 0.0, 1.0);
    }

    private double averageSessionQuality(List<StudySession> sessions) {
        OptionalDouble average = sessions.stream()
            .sorted(Comparator.comparing(StudySession::getSessionDate).reversed())
            .limit(5)
            .mapToInt(StudySession::getFocusQuality)
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

        String prefix = probability >= 0.6 ? "Higher retention expected because " : "Lower retention expected because ";
        return prefix + String.join(", ", drivers.subList(0, Math.min(3, drivers.size()))) + ".";
    }
}
