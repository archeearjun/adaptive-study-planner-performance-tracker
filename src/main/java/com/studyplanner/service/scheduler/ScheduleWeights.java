package com.studyplanner.service.scheduler;

public class ScheduleWeights {
    private double priorityWeight = 0.27;
    private double urgencyWeight = 0.22;
    private double difficultyWeight = 0.13;
    private double recallRiskWeight = 0.28;
    private double backlogWeight = 0.10;
    private double dueReviewBoost = 0.08;

    public double getPriorityWeight() {
        return priorityWeight;
    }

    public void setPriorityWeight(double priorityWeight) {
        this.priorityWeight = priorityWeight;
    }

    public double getUrgencyWeight() {
        return urgencyWeight;
    }

    public void setUrgencyWeight(double urgencyWeight) {
        this.urgencyWeight = urgencyWeight;
    }

    public double getDifficultyWeight() {
        return difficultyWeight;
    }

    public void setDifficultyWeight(double difficultyWeight) {
        this.difficultyWeight = difficultyWeight;
    }

    public double getRecallRiskWeight() {
        return recallRiskWeight;
    }

    public void setRecallRiskWeight(double recallRiskWeight) {
        this.recallRiskWeight = recallRiskWeight;
    }

    public double getBacklogWeight() {
        return backlogWeight;
    }

    public void setBacklogWeight(double backlogWeight) {
        this.backlogWeight = backlogWeight;
    }

    public double getDueReviewBoost() {
        return dueReviewBoost;
    }

    public void setDueReviewBoost(double dueReviewBoost) {
        this.dueReviewBoost = dueReviewBoost;
    }
}
