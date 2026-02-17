package com.courttrack.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class AppUser {
    private String userId;
    private String email;
    private String fullName;
    private String username;
    private String passwordHash;
    private String salt;
    private String courtId;
    private String title;
    private String professionalTitle;
    private String role;
    private String status;
    private int failedLoginAttempts;
    private LocalDateTime passwordExpiryDate;
    private LocalDateTime accountLockTimestamp;
    private boolean requirePasswordChange;
    private LocalDateTime lastLoginDate;
    private String language;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AppUser() {
        this.userId = UUID.randomUUID().toString();
        this.status = "ACTIVE";
        this.failedLoginAttempts = 0;
        this.requirePasswordChange = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public AppUser(String userId) {
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public String getCourtId() { return courtId; }
    public void setCourtId(String courtId) { this.courtId = courtId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getProfessionalTitle() { return professionalTitle; }
    public void setProfessionalTitle(String professionalTitle) { this.professionalTitle = professionalTitle; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }

    public LocalDateTime getPasswordExpiryDate() { return passwordExpiryDate; }
    public void setPasswordExpiryDate(LocalDateTime passwordExpiryDate) { this.passwordExpiryDate = passwordExpiryDate; }

    public LocalDateTime getAccountLockTimestamp() { return accountLockTimestamp; }
    public void setAccountLockTimestamp(LocalDateTime accountLockTimestamp) { this.accountLockTimestamp = accountLockTimestamp; }

    public boolean isRequirePasswordChange() { return requirePasswordChange; }
    public void setRequirePasswordChange(boolean requirePasswordChange) { this.requirePasswordChange = requirePasswordChange; }

    public LocalDateTime getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(LocalDateTime lastLoginDate) { this.lastLoginDate = lastLoginDate; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
