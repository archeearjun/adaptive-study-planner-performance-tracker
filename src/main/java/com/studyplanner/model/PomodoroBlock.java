package com.studyplanner.model;

public class PomodoroBlock {
    private long id;
    private long planItemId;
    private int blockIndex;
    private int focusMinutes;
    private int breakMinutes;
    private PomodoroStatus status;
    private Integer qualityRating;

    public PomodoroBlock() {
    }

    public PomodoroBlock(long id, long planItemId, int blockIndex, int focusMinutes, int breakMinutes,
                         PomodoroStatus status, Integer qualityRating) {
        this.id = id;
        this.planItemId = planItemId;
        this.blockIndex = blockIndex;
        this.focusMinutes = focusMinutes;
        this.breakMinutes = breakMinutes;
        this.status = status;
        this.qualityRating = qualityRating;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPlanItemId() {
        return planItemId;
    }

    public void setPlanItemId(long planItemId) {
        this.planItemId = planItemId;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }

    public int getFocusMinutes() {
        return focusMinutes;
    }

    public void setFocusMinutes(int focusMinutes) {
        this.focusMinutes = focusMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public PomodoroStatus getStatus() {
        return status;
    }

    public void setStatus(PomodoroStatus status) {
        this.status = status;
    }

    public Integer getQualityRating() {
        return qualityRating;
    }

    public void setQualityRating(Integer qualityRating) {
        this.qualityRating = qualityRating;
    }
}
