package com.courttrack.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class CaseStageHistory {
    private String historyId;
    private String caseId;
    private String fromStatus;
    private String toStatus;
    private String changedByUserId;
    private String changedBy;
    private LocalDateTime changedAt;
    private String notes;
    private String courtId;

    public CaseStageHistory() {
        this.historyId = UUID.randomUUID().toString();
        this.changedAt = LocalDateTime.now();
    }

    public CaseStageHistory(String historyId) {
        this.historyId = historyId;
        this.changedAt = LocalDateTime.now();
    }

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getChangedByUserId() {
        return changedByUserId;
    }

    public void setChangedByUserId(String changedByUserId) {
        this.changedByUserId = changedByUserId;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCourtId() {
        return courtId;
    }

    public void setCourtId(String courtId) {
        this.courtId = courtId;
    }
}
