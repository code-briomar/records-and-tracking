package com.courttrack.update;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UpdateDownloader {

    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);

    public DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Downloads the installer ZIP and returns the path to the downloaded file.
     * Updates progress property on the FX thread.
     */
    public Path download(UpdateInfo info) throws IOException {
        String downloadUrl = info.getDownloadUrl();
        String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

        Path downloadDir = Path.of(System.getProperty("user.home"), ".courttrack", "downloads");
        Files.createDirectories(downloadDir);
        Path targetFile = downloadDir.resolve(fileName);

        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);

        long totalSize = info.getFileSize() > 0 ? info.getFileSize() : conn.getContentLengthLong();

        try (InputStream in = conn.getInputStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile.toFile()))) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                if (totalSize > 0) {
                    double pct = (double) downloaded / totalSize;
                    Platform.runLater(() -> progress.set(pct));
                }
            }
        }

        Platform.runLater(() -> progress.set(1.0));
        return targetFile;
    }

    /**
     * Downloads the new release, replaces the currently-running JAR in-place,
     * relaunches from the same path, and exits. Also saves release notes so
     * the next startup can display "What's New".
     *
     * @param installerPath path to the downloaded file (.zip or .jar)
     * @param info          metadata about the new release (version, release notes)
     */
    public void launchInstallerAndExit(Path installerPath, UpdateInfo info) throws IOException {
        // Persist release notes so the restarted app can display them.
        savePendingReleaseNotes(info);

        String fileName = installerPath.getFileName().toString().toLowerCase();
        Path newJar;

        if (fileName.endsWith(".jar")) {
            // The downloaded file IS the new fat JAR — use it directly.
            newJar = installerPath;
        } else {
            // ZIP containing the fat JAR — extract it first.
            Path installDir = Path.of(System.getProperty("user.home"), ".courttrack", "updates");
            extractZip(installerPath, installDir);
            newJar = findJarInDir(installDir);
        }

        // Determine where to launch from.
        // Best case: overwrite the JAR the user normally launches so future
        // launches automatically use the new version.
        Path currentJar = getCurrentJarPath();
        Path launchJar;

        if (newJar != null && currentJar != null) {
            replaceJar(newJar, currentJar);
            launchJar = currentJar;
        } else if (newJar != null) {
            // Running from IDE/classpath — can't determine the on-disk JAR.
            // Just launch the new JAR from where it already is.
            launchJar = newJar;
        } else {
            throw new IOException("Could not locate the new JAR to launch");
        }

        String java = resolveJavaw();
        ProcessBuilder pb = new ProcessBuilder(java, "-jar", launchJar.toAbsolutePath().toString());
        pb.directory(launchJar.getParent().toFile());

        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            Path logDir = Path.of(System.getProperty("user.home"), ".courttrack", "logs");
            Files.createDirectories(logDir);
            pb.redirectErrorStream(true);
            pb.redirectOutput(logDir.resolve("update-relaunch.log").toFile());
        } else {
            pb.inheritIO();
        }

        pb.start();
        Platform.exit();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the path of the JAR currently running this code, or null when
     * running from an exploded classpath (IDE / Maven exec).
     */
    private Path getCurrentJarPath() {
        try {
            URL location = UpdateDownloader.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            Path p = Path.of(location.toURI());
            if (p.toString().toLowerCase().endsWith(".jar") && Files.isRegularFile(p)) {
                return p;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Copies {@code newJar} over {@code target}.
     * On Windows the running JAR is locked; rename it away first (NTFS allows
     * renaming open files), then copy the new one into place.
     */
    private void replaceJar(Path newJar, Path target) throws IOException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            Path backup = target.resolveSibling(target.getFileName() + ".old");
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(newJar, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Saves version + release notes to ~/.courttrack/pending-release-notes.properties
     * so the next app startup can display the "What's New" dialog for the correct
     * version without hitting the network again.
     */
    public void savePendingReleaseNotes(UpdateInfo info) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".courttrack");
            Files.createDirectories(dir);
            Path file = dir.resolve("pending-release-notes.properties");
            Properties props = new Properties();
            props.setProperty("version", info.getVersion());
            props.setProperty("notes", info.getReleaseNotes() != null ? info.getReleaseNotes() : "");
            try (OutputStream os = new FileOutputStream(file.toFile())) {
                props.store(os, null);
            }
        } catch (IOException e) {
            System.err.println("Failed to save pending release notes: " + e.getMessage());
        }
    }

    /** Extracts a ZIP to {@code destDir}, wiping it clean first. */
    private void extractZip(Path zipPath, Path destDir) throws IOException {
        if (Files.exists(destDir)) {
            try (var stream = Files.walk(destDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .filter(p -> !p.equals(destDir))
                      .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(destDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destDir.resolve(entry.getName()).normalize();
                if (!outPath.startsWith(destDir)) { zis.closeEntry(); continue; }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /** Finds the first {@code .jar} file under {@code dir} (recursive). */
    private Path findJarInDir(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar") && Files.isRegularFile(p))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the absolute path to javaw.exe (Windows) or java (Unix) for the
     * JRE that is currently running this application. Uses java.home which is
     * always set by the JVM — no PATH lookups, no process handle parsing.
     */
    private String resolveJavaw() {
        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isEmpty()) {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            String exeName = isWindows ? "javaw.exe" : "java";
            Path candidate = Path.of(javaHome, "bin", exeName);
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        // Absolute fallback — should never be reached in practice
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "javaw" : "java";
    }
}
