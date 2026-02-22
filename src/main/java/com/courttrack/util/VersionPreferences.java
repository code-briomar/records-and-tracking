package com.courttrack.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class VersionPreferences {
    private static final String PREFS_FILE = ".courttrack_version.properties";
    private static VersionPreferences instance;
    private final Properties props = new Properties();
    private final File prefsFile;

    private VersionPreferences() {
        String home = System.getProperty("user.home");
        prefsFile = new File(home, PREFS_FILE);
        load();
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
}
