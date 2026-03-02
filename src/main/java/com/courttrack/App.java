package com.courttrack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.courttrack.db.DatabaseManager;
import com.courttrack.sync.CourtContext;
import com.courttrack.sync.FirestoreContext;
import com.courttrack.sync.SyncCoordinator;
import com.courttrack.sync.SyncStatus;
import com.courttrack.ui.LoginView;
import com.courttrack.ui.MainView;
import com.courttrack.ui.ReleaseNotesDialog;
import com.courttrack.ui.ThemeManager;
import com.courttrack.ui.Toast;
import com.courttrack.update.UpdateChecker;
import com.courttrack.util.AppVersion;
import com.courttrack.util.VersionPreferences;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application {

    private Stage primaryStage;
    private LoginView loginView;
    private MainView mainView;
    private ScheduledExecutorService connectivityChecker;
    private VersionPreferences prefs;

    private ScheduledExecutorService updateChecker;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Set window/taskbar icon
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/app.ico"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Could not load app icon: " + e.getMessage());
        }

        // Initialize database
        DatabaseManager.getInstance().initialize();

        prefs = VersionPreferences.getInstance();
        // Try auto-login with saved session
        if (prefs.hasSession()) {
            if (tryAutoLogin()) {
                primaryStage.show();
                return; // Already showed main view
            }
        }

        // Apply AtlantaFX theme before showing any UI
        ThemeManager.getInstance().applyTheme();

        showLogin();

        stage.setTitle("Records & Tracking System");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setWidth(1200);
        stage.setHeight(800);
        stage.centerOnScreen();
        stage.show();
    }

    private void showLogin() {
        CourtContext.getInstance().unbind();
        stopConnectivityChecker();
        SyncStatus.getInstance().set(SyncStatus.State.OFFLINE, "Offline");
        loginView = new LoginView(this::onLoginAttempt);
        Scene scene = new Scene(loginView.getRoot(), 1200, 800);
        addSupplementalCss(scene);
        primaryStage.setScene(scene);
    }

    private void onLoginAttempt(String courtId, String email, String password) {
        System.out.println("=== Login attempt: courtId=" + courtId + ", email=" + email);

        new Thread(() -> {
            boolean isOnline = checkOnline();

            Map<String, Object> userData;

            try {
                // 1. Try offline login first (localDB)
                userData = findLocalUser(courtId, email);

                // 2. If not found locally, try online
                if (userData == null && isOnline) {
                    var docs = FirestoreContext.getUsersByEmail(courtId, email);
                    if (!docs.isEmpty()) {
                        userData = docs.get(0).getValue();
                        // Save to local for future offline use
                        Object userIdObj = userData.get("id");
                        String userId = userIdObj != null ? userIdObj.toString() : null;
                        userData.put("userId", userId);
                        String fullName = (String) userData.getOrDefault("fullName", "");
                        String role = (String) userData.getOrDefault("role", "CLERK");
                        String courtIdLocal = (String) userData.get("courtId");
                        String courtNameLocal = (String) userData.getOrDefault("courtName", courtIdLocal);
                        String passwordHash = (String) userData.get("passwordHash");
                        String salt = (String) userData.get("salt");
                        upsertLocalUser(userId, email, fullName, courtIdLocal, courtNameLocal, role, passwordHash, salt);
                    }
                }

                // 3. If still no user data, fail
                if (userData == null) {
                    Platform.runLater(() -> Toast.showError("Invalid email or COURT ID"));
                    return;
                }

                //4. Verify password
                String storedHash = (String) userData.get("passwordHash");
                String storedSalt = (String) userData.get("salt");

                if (storedHash == null || storedSalt == null) {
                    Platform.runLater(() -> Toast.showError("Account not set up. Contact Admin from the help screen"));
                    return;
                }

                if (!verifyPassword(password, storedHash, storedSalt)) {
                    Platform.runLater(() -> Toast.showError("Invalid password."));
                    return;
                }

                // 5. Extract and validate user info
                String userId = (String) userData.get("userId");
                String fullName = (String) userData.getOrDefault("fullName", "");
                String role = (String) userData.get("role");
                String status = (String) userData.getOrDefault("status", "ACTIVE");
                String courtName = (String) userData.getOrDefault("courtName", courtId);

                // Save session for auto-login
                prefs.setLastCourtId(courtId);
                prefs.setLastEmail(email);
                prefs.setLastUserId(userId);
                prefs.setLastFullName(fullName);
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    Platform.runLater(() -> Toast.showError("Account is not active"));
                    return;
                }

                // 6. Success - bind context and show main view
                CourtContext.getInstance().bind(courtId, courtName, userId, email, role);
                SyncStatus.getInstance().set(SyncStatus.State.SYNCING, isOnline ? "Connected" : "Offline");

                Platform.runLater(() -> {
                    loginView.setLoading(false);
                    mainView = new MainView(fullName.isEmpty() ? email : fullName, this::showLogin);
                    Scene scene = new Scene(mainView.getRoot(), 1200, 800);
                    addSupplementalCss(scene);
                    primaryStage.setScene(scene);
                    if (isOnline) {
                        checkAndShowReleaseNotes();
                        scheduleUpdateCheck();
                    }
                });

                // 7. Sync if online
                if (isOnline) {
                    Thread.sleep(2000);
                    SyncCoordinator.getInstance().syncAll();
                }

                startConnectivityChecker();
            } catch (IOException | InterruptedException e) {
                Platform.runLater(() -> Toast.showError("Login failed: " + e.getMessage()));
            }
        }).start();
    }

    private boolean tryAutoLogin() {
        String courtId = prefs.getLastCourtId();
        String email = prefs.getLastEmail();

        if (courtId.isEmpty() || email.isEmpty()) {
            return false;
        }

        // Apply theme before showing UI
        ThemeManager.getInstance().applyTheme();

        Map<String, Object> userData = findLocalUser(courtId, email);
        if (userData == null) {
            return false;
        }

        String userId = (String) userData.get("userId");
        String fullName = (String) userData.getOrDefault("fullName", "");
        String role = (String) userData.get("role");
        String courtName = (String) userData.getOrDefault("courtName", courtId);
        String status = (String) userData.getOrDefault("status", "ACTIVE");

        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return false;
        }

        CourtContext.getInstance().bind(courtId, courtName, userId, email, role);
        boolean isOnline = checkOnline();
        SyncStatus.getInstance().set(SyncStatus.State.SYNCING, isOnline ? "Connected" : "Offline");

        mainView = new MainView(fullName.isEmpty() ? email : fullName, this::showLogin);
        Scene scene = new Scene(mainView.getRoot(), 1200, 800);
        addSupplementalCss(scene);
        primaryStage.setScene(scene);
        if (isOnline) {
            checkAndShowReleaseNotes();
            scheduleUpdateCheck();
        }

        startConnectivityChecker();
        return true;
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
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Password verification error: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Object> findLocalUser(String courtId, String email) {
        String sql = "SELECT * FROM app_user WHERE court_id = ? AND LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseManager.getInstance().getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, courtId);
            ps.setString(2, email);

            var rs = ps.executeQuery();

            if (rs.next()) {
                Map<String, Object> userData = new java.util.HashMap<>();
                userData.put("userId", rs.getString("user_id"));
                userData.put("email", rs.getString("email"));
                userData.put("fullName", rs.getString("full_name"));
                userData.put("role", rs.getString("role"));
                userData.put("status", rs.getString("status"));
                userData.put("passwordHash", rs.getString("password_hash"));
                userData.put("salt", rs.getString("salt"));
                userData.put("courtName", rs.getString("court_id")); //store courtId temporarily
                return userData;
            }
        } catch (Exception e) {
            System.err.println("Error finding local user: " + e.getMessage());
        }
        return null;
    }

    private void upsertLocalUser(String userId, String email, String fullName, String courtId, String courtName, String role, String passwordHash, String salt) {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            // Ensure court exists locally first (satisfies the FK constraint on app_user)
            String courtSql = "MERGE INTO court (court_id, name, is_active) KEY(court_id) VALUES (?, ?, TRUE)";
            try (PreparedStatement ps = conn.prepareStatement(courtSql)) {
                ps.setString(1, courtId);
                ps.setString(2, courtName);
                ps.executeUpdate();
            }
            String userSql = "MERGE INTO app_user (user_id, email, full_name, court_id, role, status, password_hash, salt, last_login_date, updated_at) "
                    + "KEY(user_id) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setString(1, userId);
                ps.setString(2, email);
                ps.setString(3, fullName);
                ps.setString(4, courtId);
                ps.setString(5, role);
                ps.setString(6, passwordHash);
                ps.setString(7, salt);
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

    private void checkAndShowReleaseNotes() {
        VersionPreferences prefs = VersionPreferences.getInstance();
        String currentVersion = AppVersion.getVersion();
        String lastVersion = prefs.getLastVersion();

        // Always update the stored version so future runs know what was last run.
        if (!currentVersion.equals(lastVersion)) {
            prefs.setLastVersion(currentVersion);
        }

        // Only show the "What's New" dialog when the updater left a pending file,
        // meaning the user actually just went through an in-app update.
        // (Skip on fresh install where no pending file exists.)
        Path pendingFile = Path.of(System.getProperty("user.home"), ".courttrack",
                "pending-release-notes.properties");
        if (!Files.exists(pendingFile)) {
            return;
        }

        try {
            Properties props = new Properties();
            try (var reader = new java.io.FileReader(pendingFile.toFile())) {
                props.load(reader);
            }
            String pendingVersion = props.getProperty("version", "");
            String notes = props.getProperty("notes", "");

            // Consume the file — dialog is shown at most once.
            Files.deleteIfExists(pendingFile);

            if (pendingVersion.equals(currentVersion)) {
                System.out.println("Showing release notes for " + currentVersion);
                Platform.runLater(()
                        -> new ReleaseNotesDialog(currentVersion, notes).showAndWait());
            }
        } catch (IOException e) {
            System.err.println("Failed to read pending release notes: " + e.getMessage());
        }
    }

    private void scheduleUpdateCheck() {
        if (updateChecker != null) {
            updateChecker.shutdownNow();
        }
        updateChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "update-checker");
            t.setDaemon(true);
            return t;
        });
        updateChecker.schedule(() -> {
            try {
                UpdateChecker checker = new UpdateChecker();
                checker.checkForUpdate().ifPresent(updateInfo -> {
                    Platform.runLater(() -> {
                        if (mainView != null) {
                            mainView.showUpdateNotification(updateInfo);
                        }
                    });
                });
            } catch (Exception e) {
                System.err.println("Update check failed: " + e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        stopConnectivityChecker();
        if (updateChecker != null) {
            updateChecker.shutdownNow();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
