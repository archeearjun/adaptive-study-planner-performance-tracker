package com.studyplanner.model;

import java.time.LocalDate;

public class RetentionTrainingExample {
    private long id;
    private long topicId;
    private Long sourceSessionId;
    private LocalDate capturedOn;
    private double daysSinceLastRevision;
    private double difficulty;
    private double previousReviewQuality;
    private double confidenceScore;
    private double completionConsistency;
    private double repetitions;
    private double averageSessionQuality;
    private int label;

    public RetentionTrainingExample() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTopicId() {
        return topicId;
    }

    public void setTopicId(long topicId) {
        this.topicId = topicId;
    }

    public Long getSourceSessionId() {
        return sourceSessionId;
    }

    public void setSourceSessionId(Long sourceSessionId) {
        this.sourceSessionId = sourceSessionId;
    }

    public LocalDate getCapturedOn() {
        return capturedOn;
    }

    public void setCapturedOn(LocalDate capturedOn) {
        this.capturedOn = capturedOn;
    }

    public double getDaysSinceLastRevision() {
        return daysSinceLastRevision;
    }

    public void setDaysSinceLastRevision(double daysSinceLastRevision) {
        this.daysSinceLastRevision = daysSinceLastRevision;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(double difficulty) {
        this.difficulty = difficulty;
    }

    public double getPreviousReviewQuality() {
        return previousReviewQuality;
    }

    public void setPreviousReviewQuality(double previousReviewQuality) {
        this.previousReviewQuality = previousReviewQuality;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public double getCompletionConsistency() {
        return completionConsistency;
    }

    public void setCompletionConsistency(double completionConsistency) {
        this.completionConsistency = completionConsistency;
    }

    public double getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(double repetitions) {
        this.repetitions = repetitions;
    }

    public double getAverageSessionQuality() {
        return averageSessionQuality;
    }

    public void setAverageSessionQuality(double averageSessionQuality) {
        this.averageSessionQuality = averageSessionQuality;
    }

    public int getLabel() {
        return label;
    }

    public void setLabel(int label) {
        this.label = label;
    }

    public RetentionFeatureVector toFeatureVector() {
        return new RetentionFeatureVector(
            daysSinceLastRevision,
            difficulty,
            previousReviewQuality,
            confidenceScore,
            completionConsistency,
            repetitions,
            averageSessionQuality
        );
    }
}
