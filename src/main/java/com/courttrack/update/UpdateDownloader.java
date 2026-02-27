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
import java.util.Optional;
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

        if (newJar == null) throw new IOException("Could not locate the new JAR to launch");

        Path currentJar = getCurrentJarPath();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows && currentJar != null) {
            // On Windows the JVM holds a read lock on the running JAR — neither rename
            // nor overwrite works. Delegate the swap + relaunch to a batch script that
            // runs after we exit and the lock is released.
            relaunchViaScript(newJar, currentJar);
            Platform.exit();
            return;
        }

        // Linux/macOS: in-place copy is safe even for a running JAR.
        Path launchJar = (currentJar != null) ? currentJar : newJar;
        if (currentJar != null) {
            Files.copy(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
        }
        new ProcessBuilder(resolveJavaw(), "-jar", launchJar.toAbsolutePath().toString())
                .directory(launchJar.getParent().toFile())
                .inheritIO()
                .start();
        Platform.exit();
    }

    /**
     * Windows trampoline: stages the new JAR next to the original, writes a .bat
     * that waits for the lock to clear then swaps the files and relaunches, and
     * starts the bat detached so it survives this process exiting.
     */
    private void relaunchViaScript(Path newJar, Path target) throws IOException {
        // Stage next to the target (same drive → instant copy, no cross-device move needed)
        Path staged = target.resolveSibling(target.getFileName() + ".update");
        Files.copy(newJar, staged, StandardCopyOption.REPLACE_EXISTING);

        Path logDir = Path.of(System.getProperty("user.home"), ".courttrack", "logs");
        Files.createDirectories(logDir);
        Path log = logDir.resolve("update-relaunch.log");
        Path bat = logDir.resolve("do-update.bat");

        // Prefer relaunching via the jpackage native EXE if that's how we were started.
        String nativeLauncher = getNativeLauncher();
        String launchLine = (nativeLauncher != null)
                ? "start \"CourtTrack\" \"" + nativeLauncher + "\""
                : "start \"CourtTrack\" \"" + resolveJavaw() + "\" -jar \""
                        + target.toAbsolutePath() + "\"";

        String nl = "\r\n";
        String script =
            "@echo off" + nl +
            // ~3 s — enough for the JVM to fully shut down and release the file lock
            "ping -n 4 127.0.0.1 >nul" + nl +
            "copy /y \"" + staged.toAbsolutePath() + "\" \""
                    + target.toAbsolutePath() + "\" >>\"" + log.toAbsolutePath() + "\" 2>&1" + nl +
            "if errorlevel 1 (" + nl +
            "  echo [ERROR] copy failed >>\"" + log.toAbsolutePath() + "\"" + nl +
            "  exit /b 1" + nl +
            ")" + nl +
            "del \"" + staged.toAbsolutePath() + "\" >nul 2>&1" + nl +
            launchLine + nl;

        Files.writeString(bat, script);

        // `start "" /min <bat>` opens the script in a detached minimised console window
        // so it continues running after this JVM exits.
        new ProcessBuilder("cmd", "/c", "start", "", "/min", bat.toAbsolutePath().toString())
                .start();
    }

    /**
     * Returns the path to the native launcher EXE if the app was started via
     * a jpackage-generated executable, or null if started directly with java/javaw.
     */
    private String getNativeLauncher() {
        try {
            Optional<String> cmd = ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                String c = cmd.get();
                String name = Path.of(c).getFileName().toString().toLowerCase();
                if (!name.equals("java.exe") && !name.equals("javaw.exe") && name.endsWith(".exe")) {
                    return c;
                }
            }
        } catch (Exception ignored) {}
        return null;
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
