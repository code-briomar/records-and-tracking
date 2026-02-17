package com.courttrack.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppVersion implements Comparable<AppVersion> {
    private static final Properties props = new Properties();
    private static AppVersion current;

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    static {
        try (InputStream is = AppVersion.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            System.err.println("Failed to load app.properties: " + e.getMessage());
        }
    }

    public AppVersion(String version) {
        this.raw = version.startsWith("v") ? version.substring(1) : version;
        String[] parts = this.raw.split("\\.");
        this.major = parts.length > 0 ? parseIntSafe(parts[0]) : 0;
        this.minor = parts.length > 1 ? parseIntSafe(parts[1]) : 0;
        this.patch = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static AppVersion getCurrent() {
        if (current == null) {
            current = new AppVersion(getVersion());
        }
        return current;
    }

    public static String getVersion() {
        return props.getProperty("app.version", "0.0.0");
    }

    public static String getGitHubOwner() {
        return props.getProperty("app.github.owner", "");
    }

    public static String getGitHubRepo() {
        return props.getProperty("app.github.repo", "");
    }

    @Override
    public int compareTo(AppVersion other) {
        if (this.major != other.major) return Integer.compare(this.major, other.major);
        if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return raw;
    }
}
