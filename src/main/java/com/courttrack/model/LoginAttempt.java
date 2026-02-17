package com.courttrack.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class LoginAttempt {
    private String attemptId;
    private String username;
    private String userId;
    private String email;
    private String status;
    private String failureReason;
    private LocalDateTime timestamp;
    private String ipAddress;
    private String deviceInfo;

    public LoginAttempt() {
        this.attemptId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public LoginAttempt(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getDeviceInfo() { return deviceInfo; }
    public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }
}
