package com.courttrack.ui;

import com.courttrack.update.UpdateDownloader;
import com.courttrack.update.UpdateInfo;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;

public class UpdateNotificationBar extends HBox {

    private final UpdateInfo updateInfo;
    private final ThemeManager tm = ThemeManager.getInstance();
    private final UpdateDownloader downloader = new UpdateDownloader();

    private Label messageLabel;
    private Button downloadBtn;
    private Button dismissBtn;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Button restartBtn;
    private Button laterBtn;

    private Path downloadedInstaller;

    public UpdateNotificationBar(UpdateInfo updateInfo, Runnable onDismiss) {
        this.updateInfo = updateInfo;
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setPadding(new Insets(10, 16, 10, 16));
        setStyle(String.format("""
            -fx-background-color: %s;
            -fx-border-color: transparent transparent %s transparent;
            -fx-border-width: 0 0 1 0;
        """, tm.accentBlue() + "22", tm.accentBlue() + "44"));

        // Icon
        FontIcon icon = new FontIcon(Feather.DOWNLOAD_CLOUD);
        icon.setIconSize(18);
        icon.setIconColor(Color.web(tm.accentBlue()));

        // Message
        messageLabel = new Label("Update v" + updateInfo.getVersion() + " available");
        messageLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Download button
        downloadBtn = new Button("Download & Install");
        downloadBtn.getStyleClass().add("accent");
        downloadBtn.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-text-fill: white;
            -fx-background-radius: 6;
            -fx-padding: 6 14;
            -fx-font-size: 12px;
            -fx-cursor: hand;
        """, tm.accentBlue()));
        downloadBtn.setOnAction(e -> startDownload());

        // Dismiss link
        dismissBtn = new Button("Dismiss");
        dismissBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #888;
            -fx-cursor: hand;
            -fx-font-size: 12px;
            -fx-underline: true;
        """);
        dismissBtn.setOnAction(e -> onDismiss.run());

        // Progress bar (hidden initially)
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        progressLabel = new Label("");
        progressLabel.setFont(Font.font("System", 12));
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);

        // Restart / Later buttons (hidden initially)
        restartBtn = new Button("Restart Now");
        restartBtn.getStyleClass().add("accent");
        restartBtn.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-text-fill: white;
            -fx-background-radius: 6;
            -fx-padding: 6 14;
            -fx-font-size: 12px;
            -fx-cursor: hand;
        """, tm.accentGreen()));
        restartBtn.setVisible(false);
        restartBtn.setManaged(false);
        restartBtn.setOnAction(e -> launchInstaller());

        laterBtn = new Button("Later");
        laterBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #888;
            -fx-cursor: hand;
            -fx-font-size: 12px;
            -fx-underline: true;
        """);
        laterBtn.setVisible(false);
        laterBtn.setManaged(false);
        laterBtn.setOnAction(e -> onDismiss.run());

        getChildren().addAll(icon, messageLabel, spacer,
                progressBar, progressLabel,
                downloadBtn, dismissBtn,
                restartBtn, laterBtn);
    }

    private void startDownload() {
        // Switch to progress mode
        downloadBtn.setVisible(false);
        downloadBtn.setManaged(false);
        dismissBtn.setVisible(false);
        dismissBtn.setManaged(false);

        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressLabel.setVisible(true);
        progressLabel.setManaged(true);
        progressLabel.setText("Downloading...");

        progressBar.progressProperty().bind(downloader.progressProperty());

        new Thread(() -> {
            try {
                downloadedInstaller = downloader.download(updateInfo);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    progressLabel.setText("Download complete. Restart to install?");

                    restartBtn.setVisible(true);
                    restartBtn.setManaged(true);
                    laterBtn.setVisible(true);
                    laterBtn.setManaged(true);
                });
            } catch (Exception ex) {
                System.err.println("Download failed: " + ex.getMessage());
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                    progressLabel.setText("Download failed: " + ex.getMessage());

                    downloadBtn.setText("Retry");
                    downloadBtn.setVisible(true);
                    downloadBtn.setManaged(true);
                    dismissBtn.setVisible(true);
                    dismissBtn.setManaged(true);
                });
            }
        }, "update-downloader").start();
    }

    private void launchInstaller() {
        if (downloadedInstaller != null) {
            try {
                downloader.launchInstallerAndExit(downloadedInstaller, updateInfo);
            } catch (Exception ex) {
                System.err.println("Failed to launch installer: " + ex.getMessage());
                messageLabel.setText("Failed to launch installer: " + ex.getMessage());
            }
        }
    }
}
