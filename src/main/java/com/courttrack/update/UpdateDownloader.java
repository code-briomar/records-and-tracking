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
     * Extracts the downloaded ZIP to ~/.courttrack/updates/, finds the appropriate
     * launcher for the current OS, starts it, and exits the app.
     */
    public void launchInstallerAndExit(Path installerPath) throws IOException {
        Path installDir = Path.of(System.getProperty("user.home"), ".courttrack", "updates");

        // Wipe the directory before extracting so stale files from previous update
        // runs (e.g. generated .bat scripts, old JARs) can never be picked up as launchers.
        if (Files.exists(installDir)) {
            try (var stream = Files.walk(installDir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .filter(p -> !p.equals(installDir))
                      .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }
        Files.createDirectories(installDir);

        // Extract ZIP fresh
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(installerPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = installDir.resolve(entry.getName()).normalize();
                // Guard against path traversal attacks (e.g. ../../malicious)
                if (!outPath.startsWith(installDir)) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }

        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean isWindows = osName.contains("win");
        ProcessBuilder pb = isWindows
                ? findWindowsLauncher(installDir)
                : findUnixLauncher(installDir);

        pb.directory(installDir.toFile());
        if (isWindows) {
            // Don't inherit parent's I/O handles — the parent is about to die and
            // JavaFX needs clean handles to initialise its graphics pipeline.
            // Redirect to a log file so crashes are visible instead of silent.
            Path logDir = Path.of(System.getProperty("user.home"), ".courttrack", "logs");
            Files.createDirectories(logDir);
            File logFile = logDir.resolve("update-relaunch.log").toFile();
            pb.redirectErrorStream(true);
            pb.redirectOutput(logFile);
        } else {
            pb.inheritIO();
        }
        pb.start();

        Platform.exit();
    }

    private ProcessBuilder findWindowsLauncher(Path installDir) throws IOException {
        // Walk recursively — handles ZIPs that extract into a subdirectory
        try (var stream = Files.walk(installDir)) {
            var batFiles = stream
                    .filter(p -> {
                        String s = p.toString().toLowerCase();
                        return s.endsWith(".bat") || s.endsWith(".cmd");
                    })
                    .toList();
            if (!batFiles.isEmpty()) {
                return new ProcessBuilder("cmd", "/c", batFiles.get(0).toAbsolutePath().toString());
            }
        }
        try (var stream = Files.walk(installDir)) {
            var exeFiles = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".exe"))
                    .toList();
            if (!exeFiles.isEmpty()) {
                return new ProcessBuilder(exeFiles.get(0).toAbsolutePath().toString());
            }
        }
        // Fallback: fat JAR.
        // Use javaw.exe located via java.home — this system property is always set by
        // the JVM and points to the exact JRE running this app, so no PATH dependency.
        // javaw.exe is the GUI/windowless launcher; java.exe is a console-subsystem
        // binary that prevents JavaFX from initialising its graphics pipeline on Windows.
        try (var stream = Files.walk(installDir)) {
            var jarFiles = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .toList();
            if (!jarFiles.isEmpty()) {
                String javaw = resolveJavaw();
                return new ProcessBuilder(javaw, "-jar", jarFiles.get(0).toAbsolutePath().toString());
            }
        }
        throw new IOException("No launcher (.bat, .cmd, .exe, or .jar) found in extracted ZIP");
    }

    private ProcessBuilder findUnixLauncher(Path installDir) throws IOException {
        // jpackage: look for an executable in any bin/ subdirectory
        try (var stream = Files.walk(installDir)) {
            var binaries = stream
                    .filter(p -> p.getParent() != null
                            && p.getParent().getFileName() != null
                            && p.getParent().getFileName().toString().equals("bin")
                            && !p.getFileName().toString().contains(".")
                            && Files.isRegularFile(p))
                    .toList();
            if (!binaries.isEmpty()) {
                Path launcher = binaries.get(0);
                launcher.toFile().setExecutable(true);
                return new ProcessBuilder(launcher.toAbsolutePath().toString());
            }
        }

        // Try well-known shell script name first
        Path namedScript = installDir.resolve("records-and-tracking.sh");
        if (Files.exists(namedScript)) {
            namedScript.toFile().setExecutable(true);
            return new ProcessBuilder("/bin/bash", namedScript.toAbsolutePath().toString());
        }

        // Walk recursively for any .sh
        try (var stream = Files.walk(installDir)) {
            var shFiles = stream.filter(p -> p.toString().endsWith(".sh")).toList();
            if (!shFiles.isEmpty()) {
                shFiles.get(0).toFile().setExecutable(true);
                return new ProcessBuilder("/bin/bash", shFiles.get(0).toAbsolutePath().toString());
            }
        }

        // Fallback: run JAR directly with JavaFX on the module path
        try (var stream = Files.walk(installDir)) {
            var jarFiles = stream.filter(p -> p.toString().endsWith(".jar")).toList();
            if (!jarFiles.isEmpty()) {
                String javafxPath = findJavaFXPath();
                if (javafxPath != null) {
                    return new ProcessBuilder(
                            "java", "--module-path", javafxPath,
                            "--add-modules", "javafx.controls,javafx.graphics",
                            "-jar", jarFiles.get(0).toAbsolutePath().toString()
                    );
                }
                throw new IOException("JavaFX not found. Please install OpenJFX.");
            }
        }

        throw new IOException("No launcher found in extracted ZIP");
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

    private String findJavaFXPath() {
        return findInMavenRepo(System.getProperty("user.home") + "/.m2/repository/org/openjfx");
    }

    private String findInMavenRepo(String basePath) {
        try {
            Path base = Path.of(basePath);
            if (!Files.exists(base)) return null;

            String osName = System.getProperty("os.name", "").toLowerCase();
            String classifier = osName.contains("win") ? "-win.jar" : "-linux.jar";
            String pathSeparator = osName.contains("win") ? ";" : ":";

            String[] artifacts = {"javafx-controls", "javafx-graphics", "javafx-base"};
            StringBuilder sb = new StringBuilder();

            for (String artifact : artifacts) {
                try (var stream = Files.walk(base, 3)) {
                    var matches = stream
                            .filter(p -> p.toString().contains(artifact) && p.toString().endsWith(classifier))
                            .toList();
                    if (!matches.isEmpty()) {
                        if (sb.length() > 0) sb.append(pathSeparator);
                        sb.append(matches.get(0).toAbsolutePath());
                    } else {
                        return null;
                    }
                }
            }

            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
