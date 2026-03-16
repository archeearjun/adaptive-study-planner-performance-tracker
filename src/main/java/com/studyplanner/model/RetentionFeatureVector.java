package com.studyplanner.model;

public class RetentionFeatureVector {
    private final double daysSinceLastRevision;
    private final double difficulty;
    private final double previousReviewQuality;
    private final double confidenceScore;
    private final double completionConsistency;
    private final double repetitions;
    private final double averageSessionQuality;

    public RetentionFeatureVector(double daysSinceLastRevision, double difficulty, double previousReviewQuality,
                                  double confidenceScore, double completionConsistency, double repetitions,
                                  double averageSessionQuality) {
        this.daysSinceLastRevision = daysSinceLastRevision;
        this.difficulty = difficulty;
        this.previousReviewQuality = previousReviewQuality;
        this.confidenceScore = confidenceScore;
        this.completionConsistency = completionConsistency;
        this.repetitions = repetitions;
        this.averageSessionQuality = averageSessionQuality;
    }

    public double getDaysSinceLastRevision() {
        return daysSinceLastRevision;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public double getPreviousReviewQuality() {
        return previousReviewQuality;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public double getCompletionConsistency() {
        return completionConsistency;
    }

    public double getRepetitions() {
        return repetitions;
    }

    public double getAverageSessionQuality() {
        return averageSessionQuality;
    }

    public double[] toArray() {
        return new double[] {
            daysSinceLastRevision,
            difficulty,
            previousReviewQuality,
            confidenceScore,
            completionConsistency,
            repetitions,
            averageSessionQuality
        };
    }
}
