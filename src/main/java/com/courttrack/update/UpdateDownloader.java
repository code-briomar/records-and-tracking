package com.courttrack.update;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateDownloader {

    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);

    public DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Downloads the installer and returns the path to the downloaded file.
     * Updates progress property on the FX thread.
     */
    public Path download(UpdateInfo info) throws IOException {
        String downloadUrl = info.getDownloadUrl();
        String fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);

        Path tempDir = Files.createTempDirectory("courttrack-update");
        Path targetFile = tempDir.resolve(fileName);

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
     * Launches the downloaded installer and exits the app.
     */
    public void launchInstallerAndExit(Path installerPath) throws IOException {
        String osName = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder pb;

        if (osName.contains("linux")) {
            // Try xdg-open first (handles .deb via Software Center), fall back to dpkg
            pb = new ProcessBuilder("xdg-open", installerPath.toAbsolutePath().toString());
        } else if (osName.contains("win")) {
            pb = new ProcessBuilder("msiexec", "/i", installerPath.toAbsolutePath().toString());
        } else {
            throw new IOException("Unsupported OS for installer launch: " + osName);
        }

        pb.inheritIO();
        pb.start();
        Platform.exit();
    }
}
