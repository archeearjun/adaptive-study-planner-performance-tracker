package com.studyplanner.model;

public enum SessionStatus {
    PLANNED,
    STARTED,
    PAUSED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    SKIPPED,
    ABANDONED;

    public boolean isActive() {
        return this == STARTED || this == PAUSED;
    }

    public boolean isTerminal() {
        return this == COMPLETED
            || this == PARTIALLY_COMPLETED
            || this == SKIPPED
            || this == ABANDONED;
    }

    public boolean countsAsStudiedWork() {
        return this == COMPLETED || this == PARTIALLY_COMPLETED;
    }
}
