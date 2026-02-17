package com.courttrack.sync;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FirestoreContext {
    private static final Logger LOGGER = Logger.getLogger(FirestoreContext.class.getName());
    private static final AtomicReference<String> currentCourtId = new AtomicReference<>();
    private static volatile boolean initialized = false;
    private static volatile Firestore firestore;

    private FirestoreContext() {}

    public static void initialize() {
        if (!initialized) {
            synchronized (FirestoreContext.class) {
                if (!initialized) {
                    try {
                        InputStream serviceAccount = FirestoreContext.class.getClassLoader()
                            .getResourceAsStream("firebase/firebase-service-account.json");
                        
                        if (serviceAccount == null) {
                            throw new RuntimeException("Firebase service account not found in resources/firebase/firebase-service-account.json");
                        }

                        FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                            .build();

                        FirebaseApp.initializeApp(options);
                        firestore = FirestoreClient.getFirestore();
                        initialized = true;
                        LOGGER.info("Firestore initialized successfully");
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Failed to initialize Firestore", e);
                        throw new RuntimeException("Failed to initialize Firestore", e);
                    }
                }
            }
        }
    }

    public static Firestore getFirestore() {
        if (!initialized) {
            initialize();
        }
        if (firestore == null) {
            throw new IllegalStateException("Firestore not initialized");
        }
        return firestore;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void setCurrentCourtId(String courtId) {
        currentCourtId.set(courtId);
    }

    public static Optional<String> getCurrentCourtId() {
        String id = currentCourtId.get();
        return (id != null && !id.isEmpty()) ? Optional.of(id) : Optional.empty();
    }

    public static boolean isAvailable() {
        boolean available = initialized && currentCourtId.get() != null;
        LOGGER.info("FirestoreContext.isAvailable() = " + available + " (initialized=" + initialized + ", courtId=" + currentCourtId.get() + ")");
        return available;
    }

    public static String getCurrentCourtIdOrThrow() {
        String id = currentCourtId.get();
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("Court context not bound");
        }
        return id;
    }

    public static CollectionReference offendersCollection() {
        return getFirestore().collection("courts")
            .document(getCurrentCourtIdOrThrow())
            .collection("offenders");
    }

    public static CollectionReference casesCollection() {
        return getFirestore().collection("courts")
            .document(getCurrentCourtIdOrThrow())
            .collection("cases");
    }

    public static DocumentReference offenderDoc(String offenderId) {
        return offendersCollection().document(offenderId);
    }

    public static DocumentReference caseDoc(String caseId) {
        return casesCollection().document(caseId);
    }

    public static ApiFuture<QuerySnapshot> getAllOffenders() {
        return offendersCollection().get();
    }

    public static ApiFuture<QuerySnapshot> getAllCases() {
        return casesCollection().get();
    }

    public static ApiFuture<QuerySnapshot> getOffendersModifiedSince(long timestamp) {
        return offendersCollection()
            .whereGreaterThan("updatedAt", timestamp)
            .get();
    }

    public static ApiFuture<QuerySnapshot> getCasesModifiedSince(long timestamp) {
        return casesCollection()
            .whereGreaterThan("updatedAt", timestamp)
            .get();
    }

    public static CollectionReference usersCollection(String courtId) {
        return getFirestore().collection("courts")
            .document(courtId)
            .collection("users");
    }
}
