package com.studyplanner.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DailyPlan {
    private long id;
    private LocalDate planDate;
    private int availableMinutes;
    private int totalPlannedMinutes;
    private LocalDateTime generatedAt;
    private String summary;
    private List<PlanItem> items = new ArrayList<>();

    public DailyPlan() {
    }

    public DailyPlan(long id, LocalDate planDate, int availableMinutes, int totalPlannedMinutes,
                     LocalDateTime generatedAt, String summary) {
        this.id = id;
        this.planDate = planDate;
        this.availableMinutes = availableMinutes;
        this.totalPlannedMinutes = totalPlannedMinutes;
        this.generatedAt = generatedAt;
        this.summary = summary;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public int getAvailableMinutes() {
        return availableMinutes;
    }

    public void setAvailableMinutes(int availableMinutes) {
        this.availableMinutes = availableMinutes;
    }

    public int getTotalPlannedMinutes() {
        return totalPlannedMinutes;
    }

    public void setTotalPlannedMinutes(int totalPlannedMinutes) {
        this.totalPlannedMinutes = totalPlannedMinutes;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<PlanItem> getItems() {
        return items;
    }

    public void setItems(List<PlanItem> items) {
        this.items = items;
    }
}
