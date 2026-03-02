package com.courttrack.sync;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class FirestoreContext {
    private static final Logger LOGGER = Logger.getLogger(FirestoreContext.class.getName());
    private static final String PROJECT_COURTS = "courts";

    private static final AtomicReference<String> currentCourtId = new AtomicReference<>();

    private FirestoreContext() {}

    public static boolean isInitialized() {
        return FirebaseRestClient.getInstance().isAuthenticated();
    }

    public static boolean isAvailable() {
        boolean available = isInitialized() && currentCourtId.get() != null;
        LOGGER.info(() -> "FirestoreContext.isAvailable() = " + available
                + " (authenticated=" + isInitialized() + ", courtId=" + currentCourtId.get() + ")");
        return available;
    }

    public static void setCurrentCourtId(String courtId) {
        currentCourtId.set(courtId);
    }

    public static Optional<String> getCurrentCourtId() {
        String id = currentCourtId.get();
        return (id != null && !id.isEmpty()) ? Optional.of(id) : Optional.empty();
    }

    public static String getCurrentCourtIdOrThrow() {
        String id = currentCourtId.get();
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("Court context not bound");
        }
        return id;
    }

    // --- Offenders ---

    public static List<Map.Entry<String, Map<String, Object>>> getAllOffenders() throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        return FirebaseRestClient.getInstance().listDocuments(PROJECT_COURTS + "/" + courtId + "/offenders");
    }

    public static List<Map.Entry<String, Map<String, Object>>> getOffendersModifiedSince(long timestamp) throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        return FirebaseRestClient.getInstance().queryGreaterThan(
                PROJECT_COURTS + "/" + courtId, "offenders", "updatedAt", timestamp);
    }

    public static void pushOffender(String docId, Map<String, Object> data) throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        FirebaseRestClient.getInstance().upsertDocument(
                PROJECT_COURTS + "/" + courtId + "/offenders/" + docId, data);
    }

    // --- Cases ---

    public static List<Map.Entry<String, Map<String, Object>>> getAllCases() throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        return FirebaseRestClient.getInstance().listDocuments(PROJECT_COURTS + "/" + courtId + "/cases");
    }

    public static List<Map.Entry<String, Map<String, Object>>> getCasesModifiedSince(long timestamp) throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        return FirebaseRestClient.getInstance().queryGreaterThan(
                PROJECT_COURTS + "/" + courtId, "cases", "updatedAt", timestamp);
    }

    public static void pushCase(String docId, Map<String, Object> data) throws IOException {
        String courtId = getCurrentCourtIdOrThrow();
        FirebaseRestClient.getInstance().upsertDocument(
                PROJECT_COURTS + "/" + courtId + "/cases/" + docId, data);
    }

    // --- Users (for login) ---

    public static List<Map.Entry<String, Map<String, Object>>> getUsersByEmail(
            String courtId, String email) throws IOException {
        return FirebaseRestClient.getInstance().queryEqual(
                PROJECT_COURTS + "/" + courtId, "users", "email", email);
    }
}
