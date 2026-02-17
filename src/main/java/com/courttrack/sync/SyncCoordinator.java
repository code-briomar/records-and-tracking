package com.courttrack.sync;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SyncCoordinator {
    private static final Logger LOGGER = Logger.getLogger(SyncCoordinator.class.getName());
    private static final int BATCH_SIZE = 500;
    private static final int MAX_RETRIES = 5;

    private static volatile SyncCoordinator instance;
    private volatile boolean isSyncing = false;
    private long currentSyncId = -1;

    private SyncCoordinator() {}

    public static SyncCoordinator getInstance() {
        if (instance == null) {
            synchronized (SyncCoordinator.class) {
                if (instance == null) {
                    instance = new SyncCoordinator();
                }
            }
        }
        return instance;
    }

    public void queuePersonSync(String personId, String operation) {
        new Thread(() -> {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                SyncQueueItem existing = findSyncQueueItem(conn, "person", personId);
                if (existing != null && !"FAILED".equals(existing.getStatus())) {
                    return;
                }
                SyncQueueItem item = new SyncQueueItem("person", personId, operation);
                item.setCourtId(CourtContext.getInstance().getCourtId());
                insertSyncQueueItem(conn, item);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to queue person " + personId, e);
            }
        }).start();
    }

    public void queueCaseSync(String caseId, String operation, String dependsOnPersonId) {
        new Thread(() -> {
            try (Connection conn = DatabaseManager.getInstance().getConnection()) {
                SyncQueueItem existing = findSyncQueueItem(conn, "case", caseId);
                if (existing != null && !"FAILED".equals(existing.getStatus())) {
                    return;
                }
                SyncQueueItem item = new SyncQueueItem("case", caseId, operation);
                item.setCourtId(CourtContext.getInstance().getCourtId());
                if (dependsOnPersonId != null) {
                    item.setDependsOnEntityType("person");
                    item.setDependsOnEntityId(dependsOnPersonId);
                    Person person = getPersonById(conn, dependsOnPersonId);
                    if (person != null && (person.isNew() || person.hasChanges() || person.isDeleted())) {
                        item.setStatus("BLOCKED");
                    }
                }
                insertSyncQueueItem(conn, item);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to queue case " + caseId, e);
            }
        }).start();
    }

    public synchronized void syncAll() {
        if (!CourtContext.getInstance().isOperationAllowed("SyncCoordinator.syncAll")) {
            LOGGER.severe("SYNC BLOCKED: No court bound");
            return;
        }
        if (isSyncing) {
            LOGGER.info("Sync already in progress");
            return;
        }

        // Offline check
        if (!isOnline()) {
            LOGGER.warning("No internet connection — sync skipped");
            SyncStatus.getInstance().set(SyncStatus.State.OFFLINE, "Offline");
            return;
        }

        // Dead sync prevention: skip if nothing to sync
        if (!hasPendingWork()) {
            LOGGER.info("Nothing to sync — skipping");
            SyncStatus.getInstance().set(SyncStatus.State.SYNCED, "Synced");
            return;
        }

        isSyncing = true;
        String courtId = CourtContext.getInstance().getCourtId();
        LOGGER.info("========== Starting sync for court: " + courtId + " ==========");
        SyncStatus.getInstance().set(SyncStatus.State.SYNCING, "Syncing...");
        try {
            long syncId = startSync("manual", "bidirectional", System.getProperty("os.name"));
            currentSyncId = syncId;
            SyncStatus.getInstance().set(SyncStatus.State.SYNCING, "Pushing local changes...");
            queueUnsyncedEntities();
            syncPersons();
            unblockDependentCases();
            syncCases();
            retryFailedItems();
            cleanupOldItems();
            completeSyncSuccess(syncId);
            SyncStatus.getInstance().set(SyncStatus.State.SYNCING, "Pulling remote changes...");
            pullRemoteChanges();
            SyncStatus.getInstance().set(SyncStatus.State.SYNCED, "Synced");
            LOGGER.info("========== Sync completed ==========");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Sync failed", e);
            SyncStatus.getInstance().set(SyncStatus.State.ERROR, "Sync failed");
            if (currentSyncId > 0) {
                try { completeSyncFailed(currentSyncId, e.getMessage(), e.getClass().getSimpleName()); }
                catch (SQLException ex) { LOGGER.log(Level.SEVERE, "Failed to record sync failure", ex); }
            }
        } finally {
            isSyncing = false;
            currentSyncId = -1;
        }
    }

    private boolean isOnline() {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("firestore.googleapis.com", 443), 3000);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasPendingWork() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Check unsynced local entities
            int unsyncedPersons = countQuery(conn, "SELECT COUNT(*) FROM person WHERE is_new = 1 OR has_changes = 1 OR is_deleted = 1");
            int unsyncedCases = countQuery(conn, "SELECT COUNT(*) FROM court_case WHERE is_new = 1 OR has_changes = 1 OR is_deleted = 1");
            int pendingQueue = countQuery(conn, "SELECT COUNT(*) FROM sync_queue WHERE status IN ('QUEUED', 'BLOCKED', 'FAILED')");

            if (unsyncedPersons > 0 || unsyncedCases > 0 || pendingQueue > 0) {
                LOGGER.info("Pending local work: persons=" + unsyncedPersons + ", cases=" + unsyncedCases + ", queue=" + pendingQueue);
                return true;
            }

            // Check remote changes since last sync
            long lastSyncTime = getLastSuccessfulSyncTime();
            if (lastSyncTime > 0 && FirestoreContext.isAvailable()) {
                var casesSnapshot = FirestoreContext.getCasesModifiedSince(lastSyncTime).get();
                var offendersSnapshot = FirestoreContext.getOffendersModifiedSince(lastSyncTime).get();
                int remoteCases = casesSnapshot.size();
                int remoteOffenders = offendersSnapshot.size();
                if (remoteCases > 0 || remoteOffenders > 0) {
                    LOGGER.info("Remote changes found: cases=" + remoteCases + ", offenders=" + remoteOffenders);
                    return true;
                }
                LOGGER.info("No local or remote changes detected");
                return false;
            }

            // No last sync time — always sync (first sync)
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check pending work, proceeding with sync", e);
            return true;
        }
    }

    private int countQuery(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void queueUnsyncedEntities() {
        LOGGER.info("Queueing unsynced entities...");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<Person> unsyncedPersons = getUnsyncedPersons(conn);
            for (Person person : unsyncedPersons) {
                if (!person.isNew() && !person.hasChanges() && !person.isDeleted()) continue;
                SyncQueueItem existing = findSyncQueueItem(conn, "person", person.getPersonId());
                String operation = person.isDeleted() ? "DELETE" : (person.isNew() ? "CREATE" : "UPDATE");
                if (existing == null) {
                    SyncQueueItem item = new SyncQueueItem("person", person.getPersonId(), operation);
                    item.setCourtId(person.getPersonId());
                    insertSyncQueueItem(conn, item);
                } else if ("COMPLETED".equals(existing.getStatus()) || "FAILED".equals(existing.getStatus())) {
                    existing.setStatus("QUEUED");
                    existing.setOperation(operation);
                    existing.setTimestamp(LocalDateTime.now());
                    existing.setRetryCount(0);
                    existing.setLastError(null);
                    existing.setNextRetryAt(null);
                    updateSyncQueueItem(conn, existing);
                }
            }
            List<CourtCase> unsyncedCases = getUnsyncedCases(conn);
            for (CourtCase caseItem : unsyncedCases) {
                if (!caseItem.isNew() && !caseItem.hasChanges() && !caseItem.isDeleted()) continue;
                if (!shouldRetryNow(caseItem.getNextRetryAt())) continue;
                SyncQueueItem existing = findSyncQueueItem(conn, "case", caseItem.getCaseId());
                String operation = caseItem.isDeleted() ? "DELETE" : (caseItem.isNew() ? "CREATE" : "UPDATE");
                if (existing == null) {
                    SyncQueueItem item = new SyncQueueItem("case", caseItem.getCaseId(), operation);
                    item.setCourtId(caseItem.getCourtId());
                    insertSyncQueueItem(conn, item);
                } else if ("COMPLETED".equals(existing.getStatus()) || "FAILED".equals(existing.getStatus())) {
                    existing.setStatus("QUEUED");
                    existing.setOperation(operation);
                    existing.setTimestamp(LocalDateTime.now());
                    existing.setRetryCount(0);
                    existing.setLastError(null);
                    existing.setNextRetryAt(null);
                    updateSyncQueueItem(conn, existing);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to queue entities", e);
        }
    }

    private void syncPersons() {
        LOGGER.info("--- Syncing persons (push to Firestore) ---");
        
        if (!FirestoreContext.isAvailable()) {
            LOGGER.warning("Firestore not available, skipping person push");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<Person> unsyncedPersons = getUnsyncedPersons(conn);
            LOGGER.info("Found " + unsyncedPersons.size() + " unsynced persons");
            
            if (unsyncedPersons.isEmpty()) {
                LOGGER.info("No persons to sync");
                return;
            }

            for (Person person : unsyncedPersons) {
                try {
                    LOGGER.info("Pushing person to Firestore: " + person.getPersonId() + " - " + person.getFullName());
                    
                    Map<String, Object> data = buildPersonMap(person);
                    
                    // Push to Firestore
                    com.google.cloud.firestore.DocumentReference docRef = FirestoreContext.offenderDoc(person.getPersonId());
                    docRef.set(data).get();
                    
                    // Update local sync flags
                    person.setNew(false);
                    person.setHasChanges(false);
                    person.setVersion(person.getVersion() + 1);
                    person.setLastSyncedAt(LocalDateTime.now());
                    updatePerson(conn, person);
                    
                    LOGGER.info("Successfully pushed person: " + person.getPersonId());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to push person: " + person.getPersonId(), e);
                }
            }
            
            LOGGER.info("Pushed " + unsyncedPersons.size() + " persons to Firestore");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to sync persons", e);
        }
    }

    private void syncCases() {
        LOGGER.info("--- Syncing cases (push to Firestore) ---");
        
        if (!FirestoreContext.isAvailable()) {
            LOGGER.warning("Firestore not available, skipping case push");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<CourtCase> unsyncedCases = getUnsyncedCases(conn);
            LOGGER.info("Found " + unsyncedCases.size() + " unsynced cases");
            
            if (unsyncedCases.isEmpty()) {
                LOGGER.info("No cases to sync");
                return;
            }

            for (CourtCase caseItem : unsyncedCases) {
                try {
                    LOGGER.info("Pushing case to Firestore: " + caseItem.getCaseId() + " - " + caseItem.getCaseNumber());
                    
                    Map<String, Object> data = buildCaseMap(caseItem);
                    
                    // Push to Firestore using caseId as document ID (caseNumber may contain invalid chars like /)
                    String docId = caseItem.getCaseId();
                    com.google.cloud.firestore.DocumentReference docRef = FirestoreContext.caseDoc(docId);
                    docRef.set(data).get();
                    
                    // Update local sync flags
                    caseItem.setNew(false);
                    caseItem.setHasChanges(false);
                    caseItem.setVersion(caseItem.getVersion() + 1);
                    caseItem.setLastSyncedAt(LocalDateTime.now());
                    updateCase(conn, caseItem);
                    
                    LOGGER.info("Successfully pushed case: " + caseItem.getCaseId());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to push case: " + caseItem.getCaseId(), e);
                }
            }
            
            LOGGER.info("Pushed " + unsyncedCases.size() + " cases to Firestore");
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to sync cases", e);
        }
    }

    private void unblockDependentCases() {
        LOGGER.info("Unblocking dependent cases...");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<SyncQueueItem> queuedItems = getQueuedItems(conn);
            int unblockedCount = 0;
            for (SyncQueueItem item : queuedItems) {
                if ("BLOCKED".equals(item.getStatus()) && "case".equals(item.getEntityType())) {
                    if (item.getDependsOnEntityType() != null && item.getDependsOnEntityId() != null) {
                        SyncQueueItem dependency = findSyncQueueItem(conn, item.getDependsOnEntityType(), item.getDependsOnEntityId());
                        if (dependency == null || "COMPLETED".equals(dependency.getStatus())) {
                            item.setStatus("QUEUED");
                            updateSyncQueueItem(conn, item);
                            unblockedCount++;
                        }
                    }
                }
            }
            LOGGER.info("Unblocked " + unblockedCount + " cases");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to unblock cases", e);
        }
    }

    private void retryFailedItems() {
        LOGGER.info("Checking for items to retry...");
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            List<SyncQueueItem> retryItems = getItemsReadyForRetry(conn);
            for (SyncQueueItem item : retryItems) {
                if (item.getRetryCount() < MAX_RETRIES) {
                    item.setStatus("QUEUED");
                    item.setRetryCount(item.getRetryCount() + 1);
                    updateSyncQueueItem(conn, item);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retry items", e);
        }
    }

    private void cleanupOldItems() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
            String sql = "DELETE FROM sync_queue WHERE status = 'COMPLETED' AND timestamp <= ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cutoffTime.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed cleanup", e);
        }
    }

    private void pullRemoteChanges() {
        LOGGER.info("Pulling remote changes from Firestore...");
        try {
            pullPersonsFromFirestore();
            pullCasesFromFirestore();
            LOGGER.info("Remote pull completed");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to pull remote changes", e);
        }
    }

    private void pullPersonsFromFirestore() {
        LOGGER.info("--- Pulling persons from Firestore (fetch all) ---");
        
        if (!FirestoreContext.isAvailable()) {
            LOGGER.warning("Firestore not available, skipping pull");
            return;
        }

        try {
            com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> queryFuture = FirestoreContext.getAllOffenders();
            com.google.cloud.firestore.QuerySnapshot querySnapshot = queryFuture.get();
            List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
            
            LOGGER.info("=== Found " + documents.size() + " persons in Firestore ===");
            
            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : documents) {
                String personId = doc.getId();
                Map<String, Object> data = doc.getData();
                
                System.out.println("--- Person: " + personId + " ---");
                String name = getStr(data, "fullName");
                if (name == null) name = getStr(data, "firstName", "") + " " + getStr(data, "lastName", "");
                System.out.println("  Name: " + name);
                System.out.println("  National ID: " + getStr(data, "nationalId", "nationalID"));
                System.out.println("  Gender: " + data.get("gender"));
                System.out.println("  Phone: " + data.get("phoneNumber"));
                System.out.println("  Email: " + data.get("email"));
                System.out.println("  Risk Level: " + data.get("riskLevel"));
                System.out.println("  Status: " + data.get("status"));
                System.out.println("  Version: " + data.get("version"));
                System.out.println("  Updated: " + (data.get("updatedAt") != null ? data.get("updatedAt") : data.get("UpdatedAt")));
                
                processRemotePerson(data, personId);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to pull persons from Firestore", e);
        }
    }

    private void pullCasesFromFirestore() {
        LOGGER.info("--- Pulling cases from Firestore (fetch all) ---");
        
        if (!FirestoreContext.isAvailable()) {
            LOGGER.warning("Firestore not available, skipping pull");
            return;
        }

        try {
            com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> queryFuture = FirestoreContext.getAllCases();
            com.google.cloud.firestore.QuerySnapshot querySnapshot = queryFuture.get();
            List<com.google.cloud.firestore.QueryDocumentSnapshot> documents = querySnapshot.getDocuments();
            
            LOGGER.info("=== Found " + documents.size() + " cases in Firestore ===");
            
            for (com.google.cloud.firestore.QueryDocumentSnapshot doc : documents) {
                String caseId = doc.getId();
                Map<String, Object> data = doc.getData();
                
                System.out.println("--- Case: " + caseId + " ---");
                System.out.println("  Case Number: " + getStr(data, "caseNumber", "CaseNumber"));
                System.out.println("  Case Title: " + getStr(data, "caseTitle", "Title", "title"));
                System.out.println("  Court: " + getStr(data, "courtName", "CourtName"));
                System.out.println("  Status: " + getStr(data, "caseStatus", "Status", "status"));
                System.out.println("  Category: " + getStr(data, "caseCategory", "CaseType", "caseType"));
                System.out.println("  Priority: " + getStr(data, "priority", "Priority"));
                System.out.println("  Filing Date: " + (data.get("filingDate") != null ? data.get("filingDate") : data.get("DateFiled")));
                System.out.println("  Version: " + data.get("version"));
                System.out.println("  Updated: " + (data.get("updatedAt") != null ? data.get("updatedAt") : data.get("UpdatedAt")));
                
                processRemoteCase(data, caseId);
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to pull cases from Firestore", e);
        }
    }

    private long getLastSuccessfulSyncTime() {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "SELECT MAX(started_at) as last_sync FROM sync_stats WHERE status = 'SUCCESS'";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String lastSync = rs.getString("last_sync");
                    if (lastSync != null) {
                        java.time.LocalDateTime ldt = parseSqliteDateTime(lastSync);
                        if (ldt != null) {
                            return ldt.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to get last sync time", e);
        }
        return 0;
    }

    /**
     * Process a remote person record - compare with local and update if newer
     */
    public void processRemotePerson(Map<String, Object> remoteData, String personId) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            Person localPerson = getPersonById(conn, personId);

            Object updatedAtVal = remoteData.get("updatedAt") != null ? remoteData.get("updatedAt") : remoteData.get("UpdatedAt");
            Long remoteUpdatedAt = extractUpdatedAt(updatedAtVal);
            Long localUpdatedAt = localPerson != null && localPerson.getUpdatedAt() != null 
                ? localPerson.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000 
                : null;
            
            boolean isDeleted = Boolean.TRUE.equals(remoteData.get("isDeleted"));
            
            LOGGER.info("Processing person " + personId + ": localPerson=" + (localPerson != null) + ", remoteUpdatedAt=" + remoteUpdatedAt + ", localUpdatedAt=" + localUpdatedAt);
            
            if (localPerson == null) {
                // New person from remote - insert
                if (!isDeleted) {
                    Person newPerson = mapRemoteDataToPerson(remoteData, personId);
                    insertPerson(conn, newPerson);
                    LOGGER.info("Inserted new person from remote: " + personId);
                }
            } else if (remoteUpdatedAt != null && (localUpdatedAt == null || remoteUpdatedAt >= localUpdatedAt)) {
                // Remote is newer or equal, or local has no timestamp - update local
                if (isDeleted) {
                    deletePerson(conn, personId);
                    LOGGER.info("Deleted person from remote: " + personId);
                } else {
                    Person updatedPerson = mapRemoteDataToPerson(remoteData, personId);
                    updatedPerson.setNew(false);
                    updatedPerson.setHasChanges(false);
                    updatePersonFull(conn, updatedPerson);
                    LOGGER.info("Updated person from remote: " + personId);
                }
            } else {
                // Local is newer - will be pushed to Firestore
                LOGGER.info("Person " + personId + " local is newer, will push to Firestore");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to process remote person: " + personId, e);
        }
    }

    /**
     * Process a remote case record - compare with local and update if newer
     */
    public void processRemoteCase(Map<String, Object> remoteData, String caseId) {
        // Skip cases missing required fields
        String caseNumber = getStr(remoteData, "caseNumber", "CaseNumber");
        if (caseNumber == null) {
            LOGGER.info("Skipping case " + caseId + ": missing case number");
            return;
        }

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            CourtCase localCase = getCaseById(conn, caseId);

            Object updatedAtVal = remoteData.get("updatedAt") != null ? remoteData.get("updatedAt") : remoteData.get("UpdatedAt");
            Long remoteUpdatedAt = extractUpdatedAt(updatedAtVal);
            Long localUpdatedAt = localCase != null && localCase.getUpdatedAt() != null 
                ? localCase.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000 
                : null;
            
            boolean isDeleted = Boolean.TRUE.equals(remoteData.get("isDeleted"));
            
            if (localCase == null) {
                // New case from remote - insert
                if (!isDeleted) {
                    CourtCase newCase = mapRemoteDataToCase(remoteData, caseId);
                    insertCase(conn, newCase);
                    LOGGER.info("Inserted new case from remote: " + caseId);
                }
            } else if (remoteUpdatedAt != null && (localUpdatedAt == null || remoteUpdatedAt >= localUpdatedAt)) {
                // Remote is newer or equal, or local has no timestamp - update local
                if (isDeleted) {
                    deleteCase(conn, caseId);
                    LOGGER.info("Deleted case from remote: " + caseId);
                } else {
                    CourtCase updatedCase = mapRemoteDataToCase(remoteData, caseId);
                    updatedCase.setNew(false);
                    updatedCase.setHasChanges(false);
                    updateCaseFull(conn, updatedCase);
                    LOGGER.info("Updated case from remote: " + caseId);
                }
            } else {
                // Local is newer - will be pushed to Firestore
                LOGGER.info("Case " + caseId + " local is newer, will push to Firestore");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to process remote case: " + caseId, e);
        }
    }

    private Long extractUpdatedAt(Object updatedAt) {
        if (updatedAt == null) return null;
        if (updatedAt instanceof Number) return ((Number) updatedAt).longValue();
        if (updatedAt instanceof String) {
            try {
                return Long.parseLong((String) updatedAt);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // Handle Firestore Timestamp objects
        if (updatedAt instanceof com.google.cloud.Timestamp) {
            return ((com.google.cloud.Timestamp) updatedAt).toDate().getTime();
        }
        return null;
    }

    /**
     * Get a string value trying multiple field name variants (camelCase and PascalCase)
     */
    private static String getStr(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object val = data.get(key);
            if (val instanceof String s && !s.isEmpty()) return s;
        }
        return null;
    }

    private Map<String, Object> buildPersonMap(Person person) {
        Map<String, Object> map = new HashMap<>();
        map.put("personId", person.getPersonId());
        map.put("nationalId", person.getNationalId());
        map.put("firstName", person.getFirstName());
        map.put("lastName", person.getLastName());
        map.put("otherNames", person.getOtherNames());
        map.put("gender", person.getGender());
        map.put("dob", person.getDob() != null ? person.getDob().toString() : null);
        map.put("phoneNumber", person.getPhoneNumber());
        map.put("email", person.getEmail());
        map.put("alias", person.getAlias());
        map.put("nationality", person.getNationality());
        map.put("maritalStatus", person.getMaritalStatus());
        map.put("occupation", person.getOccupation());
        map.put("address", person.getAddress());
        map.put("firstOffender", person.isFirstOffender());
        map.put("criminalHistory", person.getCriminalHistory());
        map.put("knownAssociates", person.getKnownAssociates());
        map.put("arrestDate", person.getArrestDate() != null ? person.getArrestDate().toString() : null);
        map.put("arrestingOfficer", person.getArrestingOfficer());
        map.put("placeOfArrest", person.getPlaceOfArrest());
        map.put("penalty", person.getPenalty());
        map.put("notes", person.getNotes());
        map.put("eyeColor", person.getEyeColor());
        map.put("hairColor", person.getHairColor());
        map.put("emergencyContactName", person.getEmergencyContactName());
        map.put("emergencyContactPhone", person.getEmergencyContactPhone());
        map.put("emergencyContactRelationship", person.getEmergencyContactRelationship());
        map.put("legalRepresentation", person.getLegalRepresentation());
        map.put("medicalConditions", person.getMedicalConditions());
        map.put("riskLevel", person.getRiskLevel());
        map.put("distinguishingMarks", person.getDistinguishingMarks());
        map.put("type", person.getType());
        map.put("status", person.getStatus());
        map.put("facility", person.getFacility());
        map.put("offenseType", person.getOffenseType());
        map.put("isDeleted", person.isDeleted());
        map.put("version", person.getVersion());
        map.put("updatedAt", System.currentTimeMillis());
        return map;
    }

    private Map<String, Object> buildCaseMap(CourtCase courtCase) {
        Map<String, Object> map = new HashMap<>();
        map.put("caseId", courtCase.getCaseId());
        map.put("caseNumber", courtCase.getCaseNumber());
        map.put("caseTitle", courtCase.getCaseTitle());
        map.put("courtId", courtCase.getCourtId());
        map.put("courtName", courtCase.getCourtName());
        map.put("filingDate", courtCase.getFilingDate() != null ? courtCase.getFilingDate().toString() : null);
        map.put("caseStatus", courtCase.getCaseStatus());
        map.put("caseCategory", courtCase.getCaseCategory());
        map.put("caseType", courtCase.getCaseType());
        map.put("priority", courtCase.getPriority());
        map.put("description", courtCase.getDescription());
        map.put("dateOfJudgment", courtCase.getDateOfJudgment() != null ? courtCase.getDateOfJudgment().toString() : null);
        map.put("sentence", courtCase.getSentence());
        map.put("mitigationNotes", courtCase.getMitigationNotes());
        map.put("prosecutionCounsel", courtCase.getProsecutionCounsel());
        map.put("appealStatus", courtCase.getAppealStatus());
        map.put("locationOfOffence", courtCase.getLocationOfOffence());
        map.put("evidenceSummary", courtCase.getEvidenceSummary());
        map.put("hearingDates", courtCase.getHearingDates());
        map.put("courtAssistant", courtCase.getCourtAssistant());
        map.put("isDeleted", courtCase.isDeleted());
        map.put("version", courtCase.getVersion());
        map.put("updatedAt", System.currentTimeMillis());
        return map;
    }

    private void updatePerson(Connection conn, Person person) throws SQLException {
        String sql = "UPDATE person SET is_new = ?, has_changes = ?, version = ?, last_synced_at = ?, sync_retry_count = ?, next_retry_at = ?, last_sync_error = ?, updated_at = datetime('now') WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, person.isNew());
            ps.setBoolean(2, person.hasChanges());
            ps.setInt(3, person.getVersion());
            ps.setString(4, person.getLastSyncedAt() != null ? person.getLastSyncedAt().toString() : null);
            ps.setInt(5, person.getSyncRetryCount());
            ps.setString(6, person.getNextRetryAt() != null ? person.getNextRetryAt().toString() : null);
            ps.setString(7, person.getLastSyncError());
            ps.setString(8, person.getPersonId());
            ps.executeUpdate();
        }
    }

    private void updateCase(Connection conn, CourtCase courtCase) throws SQLException {
        String sql = "UPDATE court_case SET is_new = ?, has_changes = ?, version = ?, last_synced_at = ?, sync_retry_count = ?, next_retry_at = ?, last_sync_error = ?, updated_at = datetime('now') WHERE case_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, courtCase.isNew());
            ps.setBoolean(2, courtCase.hasChanges());
            ps.setInt(3, courtCase.getVersion());
            ps.setString(4, courtCase.getLastSyncedAt() != null ? courtCase.getLastSyncedAt().toString() : null);
            ps.setInt(5, courtCase.getSyncRetryCount());
            ps.setString(6, courtCase.getNextRetryAt() != null ? courtCase.getNextRetryAt().toString() : null);
            ps.setString(7, courtCase.getLastSyncError());
            ps.setString(8, courtCase.getCaseId());
            ps.executeUpdate();
        }
    }

    private Person mapRemoteDataToPerson(Map<String, Object> data, String personId) {
        Person person = new Person();
        person.setPersonId(personId);
        person.setNationalId(getStr(data, "nationalId", "nationalID"));
        // Handle fullName (mobile app) vs firstName/lastName (desktop)
        String firstName = getStr(data, "firstName");
        String lastName = getStr(data, "lastName");
        if (firstName == null && lastName == null) {
            String fullName = getStr(data, "fullName");
            if (fullName != null) {
                String[] parts = fullName.split("\\s+", 2);
                firstName = parts[0];
                lastName = parts.length > 1 ? parts[1] : "";
            }
        }
        person.setFirstName(firstName != null ? firstName : "");
        person.setLastName(lastName != null ? lastName : "");
        person.setOtherNames(getStr(data, "otherNames"));
        person.setGender(getStr(data, "gender"));
        person.setPhoneNumber(getStr(data, "phoneNumber"));
        person.setEmail(getStr(data, "email"));
        person.setAlias(getStr(data, "alias"));
        person.setNationality(getStr(data, "nationality"));
        person.setMaritalStatus(getStr(data, "maritalStatus"));
        person.setOccupation(getStr(data, "occupation"));
        person.setAddress(getStr(data, "address"));
        person.setFirstOffender(Boolean.TRUE.equals(data.get("firstOffender")));
        person.setCriminalHistory(getStr(data, "criminalHistory"));
        person.setKnownAssociates(getStr(data, "knownAssociates"));
        person.setArrestingOfficer(getStr(data, "arrestingOfficer"));
        person.setPlaceOfArrest(getStr(data, "placeOfArrest"));
        person.setPenalty(getStr(data, "penalty"));
        person.setNotes(getStr(data, "notes"));
        person.setEyeColor(getStr(data, "eyeColor"));
        person.setHairColor(getStr(data, "hairColor"));
        person.setEmergencyContactName(getStr(data, "emergencyContactName"));
        person.setEmergencyContactPhone(getStr(data, "emergencyContactPhone"));
        person.setEmergencyContactRelationship(getStr(data, "emergencyContactRelationship"));
        person.setLegalRepresentation(getStr(data, "legalRepresentation"));
        person.setMedicalConditions(getStr(data, "medicalConditions"));
        person.setRiskLevel(getStr(data, "riskLevel"));
        person.setDistinguishingMarks(getStr(data, "distinguishingMarks"));
        person.setType(getStr(data, "type"));
        person.setStatus(getStr(data, "status"));
        person.setFacility(getStr(data, "facility"));
        person.setOffenseType(getStr(data, "offenseType"));
        person.setDeleted(Boolean.TRUE.equals(data.get("isDeleted")));
        person.setNew(false);
        person.setHasChanges(false);
        person.setVersion(data.get("version") instanceof Number ? ((Number) data.get("version")).intValue() : 1);
        person.setCreatedAt(LocalDateTime.now());
        person.setUpdatedAt(LocalDateTime.now());
        return person;
    }

    private CourtCase mapRemoteDataToCase(Map<String, Object> data, String caseId) {
        CourtCase courtCase = new CourtCase();
        courtCase.setCaseId(caseId);
        courtCase.setCaseNumber(getStr(data, "caseNumber", "CaseNumber"));
        courtCase.setCaseTitle(getStr(data, "caseTitle", "Title", "title"));
        courtCase.setCourtId(getStr(data, "courtId"));
        courtCase.setCourtName(getStr(data, "courtName", "CourtName"));
        String status = getStr(data, "caseStatus", "Status", "status");
        courtCase.setCaseStatus(status != null ? status : "OPEN");
        String category = getStr(data, "caseCategory", "CaseType", "caseType");
        courtCase.setCaseCategory(category != null ? category : "General");
        courtCase.setCaseType(getStr(data, "caseType", "CaseType"));
        courtCase.setPriority(getStr(data, "priority", "Priority"));
        Object filingDate = data.get("filingDate") != null ? data.get("filingDate") : data.get("DateFiled");
        LocalDate parsedDate = convertToLocalDate(filingDate);
        courtCase.setFilingDate(parsedDate != null ? parsedDate : LocalDate.now());
        courtCase.setDescription(getStr(data, "description", "chargeDescription", "ChargeDescription"));
        courtCase.setSentence(getStr(data, "sentence", "Sentence"));
        courtCase.setMitigationNotes(getStr(data, "mitigationNotes", "MitigationNotes"));
        courtCase.setProsecutionCounsel(getStr(data, "prosecutionCounsel", "ProsecutionCounsel"));
        courtCase.setAppealStatus(getStr(data, "appealStatus", "AppealStatus"));
        courtCase.setLocationOfOffence(getStr(data, "locationOfOffence", "LocationOfOffence"));
        courtCase.setEvidenceSummary(getStr(data, "evidenceSummary", "EvidenceSummary"));
        courtCase.setHearingDates(getStr(data, "hearingDates", "HearingDates"));
        courtCase.setCourtAssistant(getStr(data, "courtAssistant", "CourtAssistant"));
        courtCase.setDeleted(Boolean.TRUE.equals(data.get("isDeleted")));
        courtCase.setNew(false);
        courtCase.setHasChanges(false);
        Object ver = data.get("version");
        courtCase.setVersion(ver instanceof Number ? ((Number) ver).intValue() : 1);
        courtCase.setCreatedAt(LocalDateTime.now());
        courtCase.setUpdatedAt(LocalDateTime.now());
        return courtCase;
    }
    
    private LocalDate convertToLocalDate(Object dateValue) {
        if (dateValue == null) return null;
        if (dateValue instanceof String) {
            try {
                return LocalDate.parse((String) dateValue);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private void insertPerson(Connection conn, Person person) throws SQLException {
        String sql = "INSERT INTO person (person_id, national_id, first_name, last_name, other_names, gender, phone_number, email, alias, nationality, marital_status, occupation, address, first_offender, criminal_history, known_associates, arresting_officer, place_of_arrest, penalty, notes, eye_color, hair_color, emergency_contact_name, emergency_contact_phone, emergency_contact_relationship, legal_representation, medical_conditions, risk_level, distinguishing_marks, type, status, facility, offense_type, is_deleted, is_new, has_changes, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, person.getPersonId());
            ps.setString(2, person.getNationalId());
            ps.setString(3, person.getFirstName());
            ps.setString(4, person.getLastName());
            ps.setString(5, person.getOtherNames());
            ps.setString(6, person.getGender());
            ps.setString(7, person.getPhoneNumber());
            ps.setString(8, person.getEmail());
            ps.setString(9, person.getAlias());
            ps.setString(10, person.getNationality());
            ps.setString(11, person.getMaritalStatus());
            ps.setString(12, person.getOccupation());
            ps.setString(13, person.getAddress());
            ps.setBoolean(14, person.isFirstOffender());
            ps.setString(15, person.getCriminalHistory());
            ps.setString(16, person.getKnownAssociates());
            ps.setString(17, person.getArrestingOfficer());
            ps.setString(18, person.getPlaceOfArrest());
            ps.setString(19, person.getPenalty());
            ps.setString(20, person.getNotes());
            ps.setString(21, person.getEyeColor());
            ps.setString(22, person.getHairColor());
            ps.setString(23, person.getEmergencyContactName());
            ps.setString(24, person.getEmergencyContactPhone());
            ps.setString(25, person.getEmergencyContactRelationship());
            ps.setString(26, person.getLegalRepresentation());
            ps.setString(27, person.getMedicalConditions());
            ps.setString(28, person.getRiskLevel());
            ps.setString(29, person.getDistinguishingMarks());
            ps.setString(30, person.getType());
            ps.setString(31, person.getStatus());
            ps.setString(32, person.getFacility());
            ps.setString(33, person.getOffenseType());
            ps.setBoolean(34, person.isDeleted());
            ps.setBoolean(35, person.isNew());
            ps.setBoolean(36, person.hasChanges());
            ps.setInt(37, person.getVersion());
            ps.executeUpdate();
        }
    }

    private void updatePersonFull(Connection conn, Person person) throws SQLException {
        String sql = "UPDATE person SET national_id = ?, first_name = ?, last_name = ?, other_names = ?, gender = ?, phone_number = ?, email = ?, alias = ?, nationality = ?, marital_status = ?, occupation = ?, address = ?, first_offender = ?, criminal_history = ?, known_associates = ?, arresting_officer = ?, place_of_arrest = ?, penalty = ?, notes = ?, eye_color = ?, hair_color = ?, emergency_contact_name = ?, emergency_contact_phone = ?, emergency_contact_relationship = ?, legal_representation = ?, medical_conditions = ?, risk_level = ?, distinguishing_marks = ?, type = ?, status = ?, facility = ?, offense_type = ?, is_deleted = ?, is_new = ?, has_changes = ?, version = ?, updated_at = datetime('now') WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, person.getNationalId());
            ps.setString(2, person.getFirstName());
            ps.setString(3, person.getLastName());
            ps.setString(4, person.getOtherNames());
            ps.setString(5, person.getGender());
            ps.setString(6, person.getPhoneNumber());
            ps.setString(7, person.getEmail());
            ps.setString(8, person.getAlias());
            ps.setString(9, person.getNationality());
            ps.setString(10, person.getMaritalStatus());
            ps.setString(11, person.getOccupation());
            ps.setString(12, person.getAddress());
            ps.setBoolean(13, person.isFirstOffender());
            ps.setString(14, person.getCriminalHistory());
            ps.setString(15, person.getKnownAssociates());
            ps.setString(16, person.getArrestingOfficer());
            ps.setString(17, person.getPlaceOfArrest());
            ps.setString(18, person.getPenalty());
            ps.setString(19, person.getNotes());
            ps.setString(20, person.getEyeColor());
            ps.setString(21, person.getHairColor());
            ps.setString(22, person.getEmergencyContactName());
            ps.setString(23, person.getEmergencyContactPhone());
            ps.setString(24, person.getEmergencyContactRelationship());
            ps.setString(25, person.getLegalRepresentation());
            ps.setString(26, person.getMedicalConditions());
            ps.setString(27, person.getRiskLevel());
            ps.setString(28, person.getDistinguishingMarks());
            ps.setString(29, person.getType());
            ps.setString(30, person.getStatus());
            ps.setString(31, person.getFacility());
            ps.setString(32, person.getOffenseType());
            ps.setBoolean(33, person.isDeleted());
            ps.setBoolean(34, person.isNew());
            ps.setBoolean(35, person.hasChanges());
            ps.setInt(36, person.getVersion());
            ps.setString(37, person.getPersonId());
            ps.executeUpdate();
        }
    }

    private void deletePerson(Connection conn, String personId) throws SQLException {
        String sql = "DELETE FROM person WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, personId);
            ps.executeUpdate();
        }
    }

    private void insertCase(Connection conn, CourtCase courtCase) throws SQLException {
        String sql = "INSERT INTO court_case (case_id, case_number, case_title, court_id, court_name, filing_date, case_status, case_category, case_type, priority, description, sentence, mitigation_notes, prosecution_counsel, appeal_status, location_of_offence, evidence_summary, hearing_dates, court_assistant, is_deleted, is_new, has_changes, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courtCase.getCaseId());
            ps.setString(2, courtCase.getCaseNumber());
            ps.setString(3, courtCase.getCaseTitle());
            ps.setString(4, courtCase.getCourtId());
            ps.setString(5, courtCase.getCourtName());
            ps.setString(6, courtCase.getFilingDate() != null ? courtCase.getFilingDate().toString() : null);
            ps.setString(7, courtCase.getCaseStatus());
            ps.setString(8, courtCase.getCaseCategory());
            ps.setString(9, courtCase.getCaseType());
            ps.setString(10, courtCase.getPriority());
            ps.setString(11, courtCase.getDescription());
            ps.setString(12, courtCase.getSentence());
            ps.setString(13, courtCase.getMitigationNotes());
            ps.setString(14, courtCase.getProsecutionCounsel());
            ps.setString(15, courtCase.getAppealStatus());
            ps.setString(16, courtCase.getLocationOfOffence());
            ps.setString(17, courtCase.getEvidenceSummary());
            ps.setString(18, courtCase.getHearingDates());
            ps.setString(19, courtCase.getCourtAssistant());
            ps.setBoolean(20, courtCase.isDeleted());
            ps.setBoolean(21, courtCase.isNew());
            ps.setBoolean(22, courtCase.hasChanges());
            ps.setInt(23, courtCase.getVersion());
            ps.executeUpdate();
        }
    }

    private void updateCaseFull(Connection conn, CourtCase courtCase) throws SQLException {
        String sql = "UPDATE court_case SET case_number = ?, case_title = ?, court_id = ?, court_name = ?, case_status = ?, case_category = ?, case_type = ?, priority = ?, description = ?, sentence = ?, mitigation_notes = ?, prosecution_counsel = ?, appeal_status = ?, location_of_offence = ?, evidence_summary = ?, hearing_dates = ?, court_assistant = ?, is_deleted = ?, is_new = ?, has_changes = ?, version = ?, updated_at = datetime('now') WHERE case_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courtCase.getCaseNumber());
            ps.setString(2, courtCase.getCaseTitle());
            ps.setString(3, courtCase.getCourtId());
            ps.setString(4, courtCase.getCourtName());
            ps.setString(5, courtCase.getCaseStatus());
            ps.setString(6, courtCase.getCaseCategory());
            ps.setString(7, courtCase.getCaseType());
            ps.setString(8, courtCase.getPriority());
            ps.setString(9, courtCase.getDescription());
            ps.setString(10, courtCase.getSentence());
            ps.setString(11, courtCase.getMitigationNotes());
            ps.setString(12, courtCase.getProsecutionCounsel());
            ps.setString(13, courtCase.getAppealStatus());
            ps.setString(14, courtCase.getLocationOfOffence());
            ps.setString(15, courtCase.getEvidenceSummary());
            ps.setString(16, courtCase.getHearingDates());
            ps.setString(17, courtCase.getCourtAssistant());
            ps.setBoolean(18, courtCase.isDeleted());
            ps.setBoolean(19, courtCase.isNew());
            ps.setBoolean(20, courtCase.hasChanges());
            ps.setInt(21, courtCase.getVersion());
            ps.setString(22, courtCase.getCaseId());
            ps.executeUpdate();
        }
    }

    private void deleteCase(Connection conn, String caseId) throws SQLException {
        String sql = "DELETE FROM court_case WHERE case_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.executeUpdate();
        }
    }

    private boolean shouldRetryNow(LocalDateTime nextRetryAt) {
        return nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt);
    }

    private String calculateNextRetryTime(int retryCount) {
        long delayMinutes = (long) Math.pow(2, retryCount) * 5;
        return LocalDateTime.now().plusMinutes(delayMinutes).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime parseRetryTime(String retryTime) {
        if (retryTime == null) return null;
        try {
            return LocalDateTime.parse(retryTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private long startSync(String syncType, String direction, String userInfo) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO sync_stats (sync_type, direction, user_info) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, syncType);
                ps.setString(2, direction);
                ps.setString(3, userInfo);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        }
        return -1;
    }

    private void completeSyncSuccess(long syncId) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "UPDATE sync_stats SET status = 'SUCCESS', completed_at = datetime('now') WHERE sync_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, syncId);
                ps.executeUpdate();
            }
        }
    }

    private void completeSyncFailed(long syncId, String message, String errorClass) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "UPDATE sync_stats SET status = 'FAILED', completed_at = datetime('now'), error_message = ?, error_class = ? WHERE sync_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, message);
                ps.setString(2, errorClass);
                ps.setLong(3, syncId);
                ps.executeUpdate();
            }
        }
    }

    private SyncQueueItem findSyncQueueItem(Connection conn, String entityType, String entityId) throws SQLException {
        String sql = "SELECT * FROM sync_queue WHERE entity_type = ? AND entity_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityType);
            ps.setString(2, entityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToSyncQueueItem(rs);
            }
        }
        return null;
    }

    private void insertSyncQueueItem(Connection conn, SyncQueueItem item) throws SQLException {
        String sql = "INSERT INTO sync_queue (entity_type, entity_id, operation, status, depends_on_entity_type, depends_on_entity_id, court_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getEntityType());
            ps.setString(2, item.getEntityId());
            ps.setString(3, item.getOperation());
            ps.setString(4, item.getStatus());
            ps.setString(5, item.getDependsOnEntityType());
            ps.setString(6, item.getDependsOnEntityId());
            ps.setString(7, item.getCourtId());
            ps.executeUpdate();
        }
    }

    private void updateSyncQueueItem(Connection conn, SyncQueueItem item) throws SQLException {
        String sql = "UPDATE sync_queue SET status = ?, operation = ?, timestamp = datetime('now'), started_at = ?, completed_at = ?, retry_count = ?, last_error = ?, next_retry_at = ? WHERE queue_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getStatus());
            ps.setString(2, item.getOperation());
            ps.setString(3, item.getStartedAt() != null ? item.getStartedAt().toString() : null);
            ps.setString(4, item.getCompletedAt() != null ? item.getCompletedAt().toString() : null);
            ps.setInt(5, item.getRetryCount());
            ps.setString(6, item.getLastError());
            ps.setString(7, item.getNextRetryAt() != null ? item.getNextRetryAt().toString() : null);
            ps.setLong(8, item.getQueueId());
            ps.executeUpdate();
        }
    }

    private List<SyncQueueItem> getQueuedItems(Connection conn) throws SQLException {
        List<SyncQueueItem> items = new ArrayList<>();
        String sql = "SELECT * FROM sync_queue WHERE status IN ('QUEUED', 'BLOCKED', 'IN_PROGRESS')";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) items.add(mapResultSetToSyncQueueItem(rs));
        }
        return items;
    }

    private List<SyncQueueItem> getItemsReadyForRetry(Connection conn) throws SQLException {
        List<SyncQueueItem> items = new ArrayList<>();
        String sql = "SELECT * FROM sync_queue WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= datetime('now')";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) items.add(mapResultSetToSyncQueueItem(rs));
        }
        return items;
    }

    private SyncQueueItem mapResultSetToSyncQueueItem(ResultSet rs) throws SQLException {
        SyncQueueItem item = new SyncQueueItem();
        item.setQueueId(rs.getLong("queue_id"));
        item.setEntityType(rs.getString("entity_type"));
        item.setEntityId(rs.getString("entity_id"));
        item.setOperation(rs.getString("operation"));
        item.setStatus(rs.getString("status"));
        item.setDependsOnEntityType(rs.getString("depends_on_entity_type"));
        item.setDependsOnEntityId(rs.getString("depends_on_entity_id"));
        item.setRetryCount(rs.getInt("retry_count"));
        item.setLastError(rs.getString("last_error"));
        item.setCourtId(rs.getString("court_id"));
        String ts = rs.getString("timestamp");
        if (ts != null) item.setTimestamp(parseSqliteDateTime(ts));
        String sa = rs.getString("started_at");
        if (sa != null) item.setStartedAt(parseSqliteDateTime(sa));
        String ca = rs.getString("completed_at");
        if (ca != null) item.setCompletedAt(parseSqliteDateTime(ca));
        String nra = rs.getString("next_retry_at");
        if (nra != null) item.setNextRetryAt(parseSqliteDateTime(nra));
        return item;
    }

    private LocalDateTime parseSqliteDateTime(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            // SQLite uses "yyyy-MM-dd HH:mm:ss" format
            return LocalDateTime.parse(dateStr.replace(" ", "T"));
        } catch (Exception e) {
            LOGGER.warning("Failed to parse datetime: " + dateStr);
            return null;
        }
    }

    private List<Person> getUnsyncedPersons(Connection conn) throws SQLException {
        List<Person> persons = new ArrayList<>();
        String sql = "SELECT * FROM person WHERE is_new = 1 OR has_changes = 1 OR is_deleted = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) persons.add(mapResultSetToPerson(rs));
        }
        return persons;
    }

    private List<CourtCase> getUnsyncedCases(Connection conn) throws SQLException {
        List<CourtCase> cases = new ArrayList<>();
        String sql = "SELECT * FROM court_case WHERE is_new = 1 OR has_changes = 1 OR is_deleted = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) cases.add(mapResultSetToCourtCase(rs));
        }
        return cases;
    }

    private Person getPersonById(Connection conn, String personId) throws SQLException {
        String sql = "SELECT * FROM person WHERE person_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToPerson(rs);
            }
        }
        return null;
    }

    private CourtCase getCaseById(Connection conn, String caseId) throws SQLException {
        String sql = "SELECT * FROM court_case WHERE case_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToCourtCase(rs);
            }
        }
        return null;
    }

    private Person mapResultSetToPerson(ResultSet rs) throws SQLException {
        Person person = new Person();
        person.setPersonId(rs.getString("person_id"));
        person.setNationalId(rs.getString("national_id"));
        person.setFirstName(rs.getString("first_name"));
        person.setLastName(rs.getString("last_name"));
        person.setOtherNames(rs.getString("other_names"));
        person.setGender(rs.getString("gender"));
        String dob = rs.getString("dob");
        if (dob != null) person.setDob(java.time.LocalDate.parse(dob));
        person.setPhoneNumber(rs.getString("phone_number"));
        person.setEmail(rs.getString("email"));
        person.setDeleted(rs.getBoolean("is_deleted"));
        person.setAlias(rs.getString("alias"));
        person.setNationality(rs.getString("nationality"));
        person.setMaritalStatus(rs.getString("marital_status"));
        person.setOccupation(rs.getString("occupation"));
        person.setAddress(rs.getString("address"));
        person.setFirstOffender(rs.getBoolean("first_offender"));
        person.setCriminalHistory(rs.getString("criminal_history"));
        person.setKnownAssociates(rs.getString("known_associates"));
        String arrestDate = rs.getString("arrest_date");
        if (arrestDate != null) person.setArrestDate(java.time.LocalDate.parse(arrestDate));
        person.setArrestingOfficer(rs.getString("arresting_officer"));
        person.setPlaceOfArrest(rs.getString("place_of_arrest"));
        person.setPenalty(rs.getString("penalty"));
        person.setNotes(rs.getString("notes"));
        person.setEyeColor(rs.getString("eye_color"));
        person.setHairColor(rs.getString("hair_color"));
        person.setEmergencyContactName(rs.getString("emergency_contact_name"));
        person.setEmergencyContactPhone(rs.getString("emergency_contact_phone"));
        person.setEmergencyContactRelationship(rs.getString("emergency_contact_relationship"));
        person.setLegalRepresentation(rs.getString("legal_representation"));
        person.setMedicalConditions(rs.getString("medical_conditions"));
        person.setRiskLevel(rs.getString("risk_level"));
        person.setDistinguishingMarks(rs.getString("distinguishing_marks"));
        person.setType(rs.getString("type"));
        person.setStatus(rs.getString("status"));
        person.setFacility(rs.getString("facility"));
        person.setOffenseType(rs.getString("offense_type"));
        person.setNew(rs.getBoolean("is_new"));
        person.setHasChanges(rs.getBoolean("has_changes"));
        person.setVersion(rs.getInt("version"));
        String lastSynced = rs.getString("last_synced_at");
        if (lastSynced != null) person.setLastSyncedAt(parseSqliteDateTime(lastSynced));
        person.setSyncRetryCount(rs.getInt("sync_retry_count"));
        String nextRetry = rs.getString("next_retry_at");
        if (nextRetry != null) person.setNextRetryAt(parseSqliteDateTime(nextRetry));
        person.setLastSyncError(rs.getString("last_sync_error"));
        return person;
    }

    private CourtCase mapResultSetToCourtCase(ResultSet rs) throws SQLException {
        CourtCase courtCase = new CourtCase();
        courtCase.setCaseId(rs.getString("case_id"));
        courtCase.setCaseNumber(rs.getString("case_number"));
        courtCase.setCaseTitle(rs.getString("case_title"));
        courtCase.setCourtId(rs.getString("court_id"));
        courtCase.setCourtName(rs.getString("court_name"));
        String filingDate = rs.getString("filing_date");
        if (filingDate != null) courtCase.setFilingDate(java.time.LocalDate.parse(filingDate));
        courtCase.setCaseStatus(rs.getString("case_status"));
        courtCase.setCaseCategory(rs.getString("case_category"));
        courtCase.setCaseType(rs.getString("case_type"));
        courtCase.setPriority(rs.getString("priority"));
        courtCase.setDescription(rs.getString("description"));
        String judgmentDate = rs.getString("date_of_judgment");
        if (judgmentDate != null) courtCase.setDateOfJudgment(java.time.LocalDate.parse(judgmentDate));
        courtCase.setSentence(rs.getString("sentence"));
        courtCase.setMitigationNotes(rs.getString("mitigation_notes"));
        courtCase.setProsecutionCounsel(rs.getString("prosecution_counsel"));
        courtCase.setAppealStatus(rs.getString("appeal_status"));
        courtCase.setLocationOfOffence(rs.getString("location_of_offence"));
        courtCase.setEvidenceSummary(rs.getString("evidence_summary"));
        courtCase.setHearingDates(rs.getString("hearing_dates"));
        courtCase.setCourtAssistant(rs.getString("court_assistant"));
        courtCase.setDeleted(rs.getBoolean("is_deleted"));
        courtCase.setNew(rs.getBoolean("is_new"));
        courtCase.setHasChanges(rs.getBoolean("has_changes"));
        courtCase.setVersion(rs.getInt("version"));
        String lastSynced = rs.getString("last_synced_at");
        if (lastSynced != null) courtCase.setLastSyncedAt(parseSqliteDateTime(lastSynced));
        courtCase.setSyncRetryCount(rs.getInt("sync_retry_count"));
        String nextRetry = rs.getString("next_retry_at");
        if (nextRetry != null) courtCase.setNextRetryAt(parseSqliteDateTime(nextRetry));
        courtCase.setLastSyncError(rs.getString("last_sync_error"));
        return courtCase;
    }
}
