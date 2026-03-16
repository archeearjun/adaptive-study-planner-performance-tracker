package com.studyplanner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StudySession {
    private long id;
    private Long planItemId;
    private long topicId;
    private LocalDate sessionDate;
    private LocalDateTime startedAt;
    private int plannedMinutes;
    private int actualMinutes;
    private SessionStatus status;
    private int focusQuality;
    private double confidenceAfter;
    private Double quizScore;
    private boolean reviewSession;
    private String notes;

    public StudySession() {
    }

    public StudySession(long id, Long planItemId, long topicId, LocalDate sessionDate, LocalDateTime startedAt,
                        int plannedMinutes, int actualMinutes, SessionStatus status, int focusQuality,
                        double confidenceAfter, Double quizScore, boolean reviewSession, String notes) {
        this.id = id;
        this.planItemId = planItemId;
        this.topicId = topicId;
        this.sessionDate = sessionDate;
        this.startedAt = startedAt;
        this.plannedMinutes = plannedMinutes;
        this.actualMinutes = actualMinutes;
        this.status = status;
        this.focusQuality = focusQuality;
        this.confidenceAfter = confidenceAfter;
        this.quizScore = quizScore;
        this.reviewSession = reviewSession;
        this.notes = notes;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Long getPlanItemId() {
        return planItemId;
    }

    public void setPlanItemId(Long planItemId) {
        this.planItemId = planItemId;
    }

    public long getTopicId() {
        return topicId;
    }

    public void setTopicId(long topicId) {
        this.topicId = topicId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(int plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public int getActualMinutes() {
        return actualMinutes;
    }

    public void setActualMinutes(int actualMinutes) {
        this.actualMinutes = actualMinutes;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getFocusQuality() {
        return focusQuality;
    }

    public void setFocusQuality(int focusQuality) {
        this.focusQuality = focusQuality;
    }

    public double getConfidenceAfter() {
        return confidenceAfter;
    }

    public void setConfidenceAfter(double confidenceAfter) {
        this.confidenceAfter = confidenceAfter;
    }

    public Double getQuizScore() {
        return quizScore;
    }

    public void setQuizScore(Double quizScore) {
        this.quizScore = quizScore;
    }

    public boolean isReviewSession() {
        return reviewSession;
    }

    public void setReviewSession(boolean reviewSession) {
        this.reviewSession = reviewSession;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
