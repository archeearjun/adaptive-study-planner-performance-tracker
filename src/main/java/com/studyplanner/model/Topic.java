package com.studyplanner.model;

import java.time.LocalDate;

public class Topic {
    private long id;
    private long subjectId;
    private String name;
    private String notes;
    private int priority;
    private int difficulty;
    private int estimatedStudyMinutes;
    private LocalDate targetExamDate;
    private double confidenceLevel;
    private LocalDate lastStudiedDate;
    private double easinessFactor;
    private int repetitionCount;
    private int intervalDays;
    private LocalDate nextReviewDate;
    private boolean archived;

    public Topic() {
    }

    public Topic(long id, long subjectId, String name, String notes, int priority, int difficulty,
                 int estimatedStudyMinutes, LocalDate targetExamDate, double confidenceLevel,
                 LocalDate lastStudiedDate, double easinessFactor, int repetitionCount,
                 int intervalDays, LocalDate nextReviewDate, boolean archived) {
        this.id = id;
        this.subjectId = subjectId;
        this.name = name;
        this.notes = notes;
        this.priority = priority;
        this.difficulty = difficulty;
        this.estimatedStudyMinutes = estimatedStudyMinutes;
        this.targetExamDate = targetExamDate;
        this.confidenceLevel = confidenceLevel;
        this.lastStudiedDate = lastStudiedDate;
        this.easinessFactor = easinessFactor;
        this.repetitionCount = repetitionCount;
        this.intervalDays = intervalDays;
        this.nextReviewDate = nextReviewDate;
        this.archived = archived;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(long subjectId) {
        this.subjectId = subjectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getEstimatedStudyMinutes() {
        return estimatedStudyMinutes;
    }

    public void setEstimatedStudyMinutes(int estimatedStudyMinutes) {
        this.estimatedStudyMinutes = estimatedStudyMinutes;
    }

    public LocalDate getTargetExamDate() {
        return targetExamDate;
    }

    public void setTargetExamDate(LocalDate targetExamDate) {
        this.targetExamDate = targetExamDate;
    }

    public double getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(double confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public LocalDate getLastStudiedDate() {
        return lastStudiedDate;
    }

    public void setLastStudiedDate(LocalDate lastStudiedDate) {
        this.lastStudiedDate = lastStudiedDate;
    }

    public double getEasinessFactor() {
        return easinessFactor;
    }

    public void setEasinessFactor(double easinessFactor) {
        this.easinessFactor = easinessFactor;
    }

    public int getRepetitionCount() {
        return repetitionCount;
    }

    public void setRepetitionCount(int repetitionCount) {
        this.repetitionCount = repetitionCount;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
