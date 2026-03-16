package com.studyplanner.model;

import java.time.LocalDate;

public class ReviewRecord {
    private long id;
    private long topicId;
    private LocalDate reviewDate;
    private int quality;
    private double easinessFactorBefore;
    private double easinessFactorAfter;
    private int intervalBefore;
    private int intervalAfter;
    private LocalDate nextReviewDate;

    public ReviewRecord() {
    }

    public ReviewRecord(long id, long topicId, LocalDate reviewDate, int quality,
                        double easinessFactorBefore, double easinessFactorAfter,
                        int intervalBefore, int intervalAfter, LocalDate nextReviewDate) {
        this.id = id;
        this.topicId = topicId;
        this.reviewDate = reviewDate;
        this.quality = quality;
        this.easinessFactorBefore = easinessFactorBefore;
        this.easinessFactorAfter = easinessFactorAfter;
        this.intervalBefore = intervalBefore;
        this.intervalAfter = intervalAfter;
        this.nextReviewDate = nextReviewDate;
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

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(int quality) {
        this.quality = quality;
    }

    public double getEasinessFactorBefore() {
        return easinessFactorBefore;
    }

    public void setEasinessFactorBefore(double easinessFactorBefore) {
        this.easinessFactorBefore = easinessFactorBefore;
    }

    public double getEasinessFactorAfter() {
        return easinessFactorAfter;
    }

    public void setEasinessFactorAfter(double easinessFactorAfter) {
        this.easinessFactorAfter = easinessFactorAfter;
    }

    public int getIntervalBefore() {
        return intervalBefore;
    }

    public void setIntervalBefore(int intervalBefore) {
        this.intervalBefore = intervalBefore;
    }

    public int getIntervalAfter() {
        return intervalAfter;
    }

    public void setIntervalAfter(int intervalAfter) {
        this.intervalAfter = intervalAfter;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }
}
