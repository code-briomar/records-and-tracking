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
        Files.createDirectories(installDir);

        // Extract ZIP, overwriting existing files from previous updates
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
        ProcessBuilder pb = osName.contains("win")
                ? findWindowsLauncher(installDir)
                : findUnixLauncher(installDir);

        pb.inheritIO();
        pb.directory(installDir.toFile());
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
        // Fallback: fat JAR — user already has Java installed to be running this
        try (var stream = Files.walk(installDir)) {
            var jarFiles = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .toList();
            if (!jarFiles.isEmpty()) {
                String java = ProcessHandle.current().info().command()
                        .orElse("java");
                return new ProcessBuilder(java, "-jar", jarFiles.get(0).toAbsolutePath().toString());
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
