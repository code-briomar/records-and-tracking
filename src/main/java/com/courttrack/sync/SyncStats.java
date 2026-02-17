package com.courttrack.sync;

import java.time.LocalDateTime;

public class SyncStats {
    private Long syncId;
    private String syncType;
    private String direction;
    private String userInfo;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status;
    private int offendersSynced;
    private int casesSynced;
    private int offendersFailed;
    private int casesFailed;
    private long dataSizeBytes;
    private String errorMessage;
    private String errorClass;

    public SyncStats() {
        this.startedAt = LocalDateTime.now();
        this.status = "IN_PROGRESS";
    }

    public SyncStats(String syncType, String direction, String userInfo) {
        this();
        this.syncType = syncType;
        this.direction = direction;
        this.userInfo = userInfo;
    }

    public Long getSyncId() { return syncId; }
    public void setSyncId(Long syncId) { this.syncId = syncId; }

    public String getSyncType() { return syncType; }
    public void setSyncType(String syncType) { this.syncType = syncType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getUserInfo() { return userInfo; }
    public void setUserInfo(String userInfo) { this.userInfo = userInfo; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getOffendersSynced() { return offendersSynced; }
    public void setOffendersSynced(int offendersSynced) { this.offendersSynced = offendersSynced; }

    public int getCasesSynced() { return casesSynced; }
    public void setCasesSynced(int casesSynced) { this.casesSynced = casesSynced; }

    public int getOffendersFailed() { return offendersFailed; }
    public void setOffendersFailed(int offendersFailed) { this.offendersFailed = offendersFailed; }

    public int getCasesFailed() { return casesFailed; }
    public void setCasesFailed(int casesFailed) { this.casesFailed = casesFailed; }

    public long getDataSizeBytes() { return dataSizeBytes; }
    public void setDataSizeBytes(long dataSizeBytes) { this.dataSizeBytes = dataSizeBytes; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getErrorClass() { return errorClass; }
    public void setErrorClass(String errorClass) { this.errorClass = errorClass; }

    public void markSuccess() {
        this.status = "SUCCESS";
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String message, String errorClass) {
        this.status = "FAILED";
        this.completedAt = LocalDateTime.now();
        this.errorMessage = message;
        this.errorClass = errorClass;
    }

    public int getTotalSynced() {
        return offendersSynced + casesSynced;
    }

    public int getTotalFailed() {
        return offendersFailed + casesFailed;
    }
}
