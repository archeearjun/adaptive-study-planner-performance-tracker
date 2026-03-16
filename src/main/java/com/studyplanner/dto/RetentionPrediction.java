package com.studyplanner.dto;

public record RetentionPrediction(
    double probability,
    String explanation
) {
}
