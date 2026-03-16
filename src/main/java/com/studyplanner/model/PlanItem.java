package com.studyplanner.model;

import java.util.ArrayList;
import java.util.List;

public class PlanItem {
    private long id;
    private long planId;
    private long topicId;
    private long subjectId;
    private String topicName;
    private String subjectName;
    private PlanItemType itemType;
    private int recommendedOrder;
    private int plannedMinutes;
    private double score;
    private String reason;
    private int pomodoroCount;
    private double recallProbability;
    private SessionStatus status;
    private int completedMinutes;
    private Integer qualityRating;
    private List<PomodoroBlock> pomodoroBlocks = new ArrayList<>();

    public PlanItem() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPlanId() {
        return planId;
    }

    public void setPlanId(long planId) {
        this.planId = planId;
    }

    public long getTopicId() {
        return topicId;
    }

    public void setTopicId(long topicId) {
        this.topicId = topicId;
    }

    public long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(long subjectId) {
        this.subjectId = subjectId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public PlanItemType getItemType() {
        return itemType;
    }

    public void setItemType(PlanItemType itemType) {
        this.itemType = itemType;
    }

    public int getRecommendedOrder() {
        return recommendedOrder;
    }

    public void setRecommendedOrder(int recommendedOrder) {
        this.recommendedOrder = recommendedOrder;
    }

    public int getPlannedMinutes() {
        return plannedMinutes;
    }

    public void setPlannedMinutes(int plannedMinutes) {
        this.plannedMinutes = plannedMinutes;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getPomodoroCount() {
        return pomodoroCount;
    }

    public void setPomodoroCount(int pomodoroCount) {
        this.pomodoroCount = pomodoroCount;
    }

    public double getRecallProbability() {
        return recallProbability;
    }

    public void setRecallProbability(double recallProbability) {
        this.recallProbability = recallProbability;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public int getCompletedMinutes() {
        return completedMinutes;
    }

    public void setCompletedMinutes(int completedMinutes) {
        this.completedMinutes = completedMinutes;
    }

    public Integer getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(Integer qualityRating) {
        this.qualityRating = qualityRating;
    }

    public List<PomodoroBlock> getPomodoroBlocks() {
        return pomodoroBlocks;
    }

    public void setPomodoroBlocks(List<PomodoroBlock> pomodoroBlocks) {
        this.pomodoroBlocks = pomodoroBlocks;
    }
}
