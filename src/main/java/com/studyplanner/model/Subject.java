package com.studyplanner.model;

import java.time.LocalDateTime;

public class Subject {
    private long id;
    private String name;
    private String description;
    private String accentColor;
    private LocalDateTime createdAt;

    public Subject() {
    }

    public Subject(long id, String name, String description, String accentColor, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.accentColor = accentColor;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
