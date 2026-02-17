package com.courttrack.sync;

import java.time.LocalDateTime;

public class SyncQueueItem {
    private Long queueId;
    private String entityType;
    private String entityId;
    private String operation;
    private String status;
    private String dependsOnEntityType;
    private String dependsOnEntityId;
    private LocalDateTime timestamp;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int retryCount;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private String courtId;

    public SyncQueueItem() {
        this.timestamp = LocalDateTime.now();
        this.status = "QUEUED";
        this.retryCount = 0;
    }

    public SyncQueueItem(String entityType, String entityId, String operation) {
        this();
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
    }

    public Long getQueueId() { return queueId; }
    public void setQueueId(Long queueId) { this.queueId = queueId; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDependsOnEntityType() { return dependsOnEntityType; }
    public void setDependsOnEntityType(String dependsOnEntityType) { this.dependsOnEntityType = dependsOnEntityType; }

    public String getDependsOnEntityId() { return dependsOnEntityId; }
    public void setDependsOnEntityId(String dependsOnEntityId) { this.dependsOnEntityId = dependsOnEntityId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getCourtId() { return courtId; }
    public void setCourtId(String courtId) { this.courtId = courtId; }

    public boolean isBlocked() {
        return "BLOCKED".equals(status);
    }

    public boolean isQueued() {
        return "QUEUED".equals(status);
    }

    public boolean isInProgress() {
        return "IN_PROGRESS".equals(status);
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isReadyForRetry() {
        return isFailed() && nextRetryAt != null && LocalDateTime.now().isAfter(nextRetryAt);
    }
}
