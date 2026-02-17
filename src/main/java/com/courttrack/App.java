package com.courttrack;

import com.courttrack.db.DatabaseManager;
import com.courttrack.sync.CourtContext;
import com.courttrack.sync.FirestoreContext;
import com.courttrack.sync.SyncCoordinator;
import com.courttrack.sync.SyncStatus;
import com.courttrack.ui.LoginView;
import com.courttrack.ui.MainView;
import com.courttrack.ui.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App extends Application {
    private Stage primaryStage;
    private LoginView loginView;
    private ScheduledExecutorService connectivityChecker;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Initialize database
        DatabaseManager.getInstance().initialize();

        // Initialize Firestore (will fail gracefully if no service account)
        try {
            FirestoreContext.initialize();
            System.out.println("Firestore initialized successfully");
        } catch (Exception e) {
            System.err.println("Firestore initialization failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Apply AtlantaFX theme before showing any UI
        ThemeManager.getInstance().applyTheme();

        showLogin();

        stage.setTitle("Records & Tracking System");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setWidth(1200);
        stage.setHeight(800);
        stage.show();
    }

    private void showLogin() {
        stopConnectivityChecker();
        SyncStatus.getInstance().set(SyncStatus.State.OFFLINE, "Offline");
        loginView = new LoginView(this::onLoginAttempt);
        Scene scene = new Scene(loginView.getRoot(), 1200, 800);
        addSupplementalCss(scene);
        primaryStage.setScene(scene);
    }

    private void onLoginAttempt(String courtId, String email, String password) {
        System.out.println("=== Login attempt: courtId=" + courtId + ", email=" + email);

        if (!FirestoreContext.isInitialized()) {
            Platform.runLater(() -> loginView.showError("Firestore not available. Cannot authenticate."));
            return;
        }

        new Thread(() -> {
            try {
                // Query Firestore for user by email in the given court
                var queryFuture = FirestoreContext.usersCollection(courtId)
                    .whereEqualTo("email", email)
                    .get();
                var querySnapshot = queryFuture.get();
                List<com.google.cloud.firestore.QueryDocumentSnapshot> docs = querySnapshot.getDocuments();

                if (docs.isEmpty()) {
                    Platform.runLater(() -> loginView.showError("Invalid email or Court ID"));
                    return;
                }

                var userDoc = docs.get(0);
                Map<String, Object> userData = userDoc.getData();

                String storedHash = (String) userData.get("passwordHash");
                String storedSalt = (String) userData.get("salt");

                if (storedHash == null || storedSalt == null) {
                    Platform.runLater(() -> loginView.showError("User account not properly configured"));
                    return;
                }

                if (!verifyPassword(password, storedHash, storedSalt)) {
                    Platform.runLater(() -> loginView.showError("Invalid password"));
                    return;
                }

                // Auth successful — extract user info
                String userId = userDoc.getId();
                String fullName = (String) userData.getOrDefault("fullName", "");
                String role = (String) userData.getOrDefault("role", "CLERK");
                String status = (String) userData.getOrDefault("status", "ACTIVE");
                String courtName = (String) userData.getOrDefault("courtName", courtId);

                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    Platform.runLater(() -> loginView.showError("Account is not active"));
                    return;
                }

                System.out.println("=== Login successful: " + fullName + " (" + role + ") at " + courtId);

                // Bind court context
                CourtContext.getInstance().bind(courtId, courtName, userId, email, role);
                System.out.println("CourtContext bound: " + courtId + " - " + courtName);

                // Upsert user into local database for offline reference
                upsertLocalUser(userId, email, fullName, courtId, role);
                SyncStatus.getInstance().set(SyncStatus.State.SYNCING, "Connected");

                // Switch to main view on UI thread
                Platform.runLater(() -> {
                    loginView.setLoading(false);
                    primaryStage.setTitle("Records & Tracking System - " + courtName);
                    MainView mainView = new MainView(fullName.isEmpty() ? email : fullName, this::showLogin);
                    Scene scene = new Scene(mainView.getRoot(), 1200, 800);
                    addSupplementalCss(scene);
                    primaryStage.setScene(scene);
                });

                // Start sync in background
                System.out.println("Starting sync in background...");
                Thread.sleep(2000);
                System.out.println("=== Calling syncAll() ===");
                SyncCoordinator.getInstance().syncAll();
                System.out.println("=== syncAll() completed ===");

                // Start periodic connectivity checker
                startConnectivityChecker();

            } catch (Exception e) {
                System.err.println("Login error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> loginView.showError("Login failed: " + e.getMessage()));
            }
        }).start();
    }

    private void startConnectivityChecker() {
        stopConnectivityChecker();
        connectivityChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "connectivity-checker");
            t.setDaemon(true);
            return t;
        });
        connectivityChecker.scheduleAtFixedRate(() -> {
            try {
                boolean online = checkOnline();
                SyncStatus.State current = SyncStatus.getInstance().getState();
                if (!online && current != SyncStatus.State.OFFLINE) {
                    SyncStatus.getInstance().set(SyncStatus.State.OFFLINE, "Offline");
                } else if (online && current == SyncStatus.State.OFFLINE) {
                    SyncStatus.getInstance().set(SyncStatus.State.SYNCED, "Connected");
                }
            } catch (Exception e) {
                // Ignore errors in connectivity check
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void stopConnectivityChecker() {
        if (connectivityChecker != null) {
            connectivityChecker.shutdownNow();
            connectivityChecker = null;
        }
    }

    private boolean checkOnline() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("firestore.googleapis.com", 443), 3000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyPassword(String inputPassword, String storedHash, String storedSalt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(storedSalt);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(saltBytes);
            byte[] hashedBytes = md.digest(inputPassword.getBytes(StandardCharsets.UTF_8));
            String computedHash = Base64.getEncoder().encodeToString(hashedBytes);
            return computedHash.equals(storedHash);
        } catch (Exception e) {
            System.err.println("Password verification error: " + e.getMessage());
            return false;
        }
    }

    private void upsertLocalUser(String userId, String email, String fullName, String courtId, String role) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            String sql = "INSERT INTO app_user (user_id, email, full_name, court_id, role, status) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE') " +
                        "ON CONFLICT(user_id) DO UPDATE SET email = ?, full_name = ?, court_id = ?, role = ?, " +
                        "last_login_date = datetime('now'), updated_at = datetime('now')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                ps.setString(2, email);
                ps.setString(3, fullName);
                ps.setString(4, courtId);
                ps.setString(5, role);
                ps.setString(6, email);
                ps.setString(7, fullName);
                ps.setString(8, courtId);
                ps.setString(9, role);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Failed to upsert local user: " + e.getMessage());
        }
    }

    private void addSupplementalCss(Scene scene) {
        ThemeManager tm = ThemeManager.getInstance();
        String css = getClass().getResource(tm.getSupplementalCssPath()).toExternalForm();
        scene.getStylesheets().add(css);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
