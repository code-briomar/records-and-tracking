package com.courttrack.sync;

import java.util.Optional;

public final class FirestorePaths {
    private static final String COURTS_COLLECTION = "courts";
    private static final String OFFENDERS_COLLECTION = "offenders";
    private static final String CASES_COLLECTION = "cases";
    private static final String USERS_COLLECTION = "users";
    private static final String DOCUMENTS_COLLECTION = "documents";
    private static final String AUDIT_LOGS_COLLECTION = "audit_logs";

    private FirestorePaths() {
        throw new AssertionError("FirestorePaths is a utility class and cannot be instantiated");
    }

    public static String offenderPath(String offenderId) {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + OFFENDERS_COLLECTION + "/" + offenderId;
    }

    public static String casePath(String caseId) {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + CASES_COLLECTION + "/" + caseId;
    }

    public static String userPath(String userId) {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + USERS_COLLECTION + "/" + userId;
    }

    public static String userPathForCourt(String courtId, String userId) {
        return COURTS_COLLECTION + "/" + courtId + "/" + USERS_COLLECTION + "/" + userId;
    }

    public static String documentPath(String documentId) {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + DOCUMENTS_COLLECTION + "/" + documentId;
    }

    public static String auditLogPath(String logId) {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + AUDIT_LOGS_COLLECTION + "/" + logId;
    }

    public static String courtPath(String courtId) {
        return COURTS_COLLECTION + "/" + courtId;
    }

    public static String offendersCollectionPath() {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + OFFENDERS_COLLECTION;
    }

    public static String casesCollectionPath() {
        return COURTS_COLLECTION + "/" + requireCourtId() + "/" + CASES_COLLECTION;
    }

    private static String requireCourtId() {
        Optional<String> courtIdOpt = FirestoreContext.getCurrentCourtId();
        if (courtIdOpt.isEmpty()) {
            throw new IllegalStateException(
                "FirestorePaths: CourtContext not bound to a court. " +
                "User must be logged in and associated with a court before accessing Firestore."
            );
        }
        return courtIdOpt.get();
    }

    public static boolean isAvailable() {
        return FirestoreContext.isAvailable();
    }

    public static String getCurrentCourtIdForLogging() {
        return FirestoreContext.getCurrentCourtId().orElse("UNBOUND");
    }
}
