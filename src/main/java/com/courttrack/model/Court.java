package com.courttrack.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Court {
    private String courtId;
    private String name;
    private String location;
    private String email;
    private boolean isActive;
    private LocalDateTime createdAt;
    private String createdBy;

    public Court() {
        this.courtId = UUID.randomUUID().toString();
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    public Court(String courtId) {
        this.courtId = courtId;
    }

    public String getCourtId() { return courtId; }
    public void setCourtId(String courtId) { this.courtId = courtId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
