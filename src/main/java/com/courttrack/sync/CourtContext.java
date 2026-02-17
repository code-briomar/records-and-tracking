package com.courttrack.sync;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CourtContext {
    private static volatile CourtContext instance;
    private static volatile boolean initialized = false;

    private String courtId;
    private String courtName;
    private String userId;
    private String userEmail;
    private String userRole;
    private LocalDateTime boundAt;
    private boolean bound;

    private static final Map<String, Object> CONTEXT_LOCKS = new ConcurrentHashMap<>();

    private CourtContext() {}

    public static CourtContext getInstance() {
        if (instance == null) {
            synchronized (CourtContext.class) {
                if (instance == null) {
                    instance = new CourtContext();
                    initialized = true;
                }
            }
        }
        return instance;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public void bind(String courtId, String courtName, String userId, String userEmail, String userRole) {
        synchronized (getLock(courtId)) {
            this.courtId = courtId;
            this.courtName = courtName;
            this.userId = userId;
            this.userEmail = userEmail;
            this.userRole = userRole;
            this.boundAt = LocalDateTime.now();
            this.bound = true;
            FirestoreContext.setCurrentCourtId(courtId);
        }
    }

    public void unbind() {
        String currentCourtId = this.courtId;
        if (currentCourtId != null) {
            synchronized (getLock(currentCourtId)) {
                this.courtId = null;
                this.courtName = null;
                this.userId = null;
                this.userEmail = null;
                this.userRole = null;
                this.boundAt = null;
                this.bound = false;
                FirestoreContext.setCurrentCourtId(null);
            }
        }
    }

    public boolean isBound() {
        return bound && courtId != null && !courtId.isEmpty();
    }

    public String getCourtId() {
        return courtId;
    }

    public String getCourtName() {
        return courtName;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserRole() {
        return userRole;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public boolean isOperationAllowed(String operation) {
        return isBound();
    }

    private static Object getLock(String courtId) {
        return CONTEXT_LOCKS.computeIfAbsent(courtId, k -> new Object());
    }

    public String getDebugInfo() {
        return String.format("CourtContext[courtId=%s, courtName=%s, userId=%s, userEmail=%s, bound=%s]",
            courtId, courtName, userId, userEmail, bound);
    }
}
