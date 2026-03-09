package com.courttrack.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class VersionPreferences {

    private static final String PREFS_FILE = "courttrack_version.properties";
    private static VersionPreferences instance;
    private final Properties props = new Properties();
    private final File prefsFile;

    private VersionPreferences() {
        prefsFile = getPrefsFile();
        System.out.println("[DEBUG] VersionPreferences: Using prefs file: " + prefsFile.getAbsolutePath());
        load();
    }

    private static File getPrefsFile() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                return new File(localAppData + "/CourtTrack", PREFS_FILE);
            }
        }
        return new File(System.getProperty("user.home"), "." + PREFS_FILE);
    }

    public static VersionPreferences getInstance() {
        if (instance == null) {
            instance = new VersionPreferences();
        }
        return instance;
    }

    private void load() {
        if (prefsFile.exists()) {
            try (FileReader reader = new FileReader(prefsFile)) {
                props.load(reader);
            } catch (IOException e) {
                System.err.println("Failed to load version preferences: " + e.getMessage());
            }
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(prefsFile)) {
            props.store(writer, "CourtTrack Version Preferences");
        } catch (IOException e) {
            System.err.println("Failed to save version preferences: " + e.getMessage());
        }
    }

    public String getLastVersion() {
        return props.getProperty("lastVersion", "0.0.0");
    }

    public void setLastVersion(String version) {
        props.setProperty("lastVersion", version);
        save();
    }

    public boolean isFirstRun() {
        return !prefsFile.exists() || getLastVersion().equals("0.0.0");
    }

    public String getLastReleaseNotes() {
        return props.getProperty("lastReleaseNotes", "");
    }

    public void setLastReleaseNotes(String notes) {
        props.setProperty("lastReleaseNotes", notes);
        save();
    }

    public String getLastCourtId() {
        return props.getProperty("lastCourtId", "");
    }

    public void setLastCourtId(String courtId) {
        props.setProperty("lastCourtId", courtId);
        save();
    }

    public String getLastEmail() {
        return props.getProperty("lastEmail", "");
    }

    public void setLastEmail(String email) {
        props.setProperty("lastEmail", email);
        save();
    }

    public String getLastUserId() {
        return props.getProperty("lastUserId", "");
    }

    public void setLastUserId(String userId) {
        props.setProperty("lastUserId", userId);
        save();
    }

    public String getLastFullName() {
        return props.getProperty("lastFullName", "");
    }

    public void setLastFullName(String fullName) {
        props.setProperty("lastFullName", fullName);
        save();
    }

    public boolean hasSession() {
        return !getLastCourtId().isEmpty() && !getLastEmail().isEmpty();
    }

    public void clearSession() {
        props.remove("lastCourtId");
        props.remove("lastEmail");
        props.remove("lastUserId");
        props.remove("lastFullName");
        save();
    }
}
