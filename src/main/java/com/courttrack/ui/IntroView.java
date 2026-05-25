package com.courttrack.ui;

import java.io.InputStream;

import com.courttrack.util.AppVersion;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class IntroView {
    private final HBox root;
    private final Runnable onGetStarted;

    public IntroView(Runnable onGetStarted) {
        this.onGetStarted = onGetStarted;
        this.root = new HBox();
        buildUI();
    }

    private void buildUI() {
        // ── Left branded panel ──────────────────────────────────────────────
        VBox leftPanel = new VBox();
        leftPanel.setAlignment(Pos.CENTER_LEFT);
        leftPanel.setPadding(new Insets(52, 56, 52, 56));
        leftPanel.setStyle("-fx-background-color: linear-gradient(to bottom right, #0a0a0f, #111122);");
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        leftPanel.setMinWidth(480);
        leftPanel.setMaxWidth(580);

        // Logo + app label row
        ImageView logo = new ImageView();
        logo.setFitWidth(36);
        logo.setFitHeight(36);
        logo.setPreserveRatio(true);
        InputStream iconIs = IntroView.class.getResourceAsStream("/icons/app.png");
        if (iconIs != null) {
            logo.setImage(new Image(iconIs, 36, 36, true, true));
        }

        Label appLabel = new Label("Records & Tracking");
        appLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        appLabel.setTextFill(Color.web("#ffffff45"));

        HBox logoRow = new HBox(10, logo, appLabel);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        // Flexible spacer pushes headline toward center
        Region topSpacer = new Region();
        VBox.setVgrow(topSpacer, Priority.ALWAYS);

        // Hero headline
        Label headline = new Label("Court Records,\nSimplified.");
        headline.setFont(Font.font("System", FontWeight.BOLD, 42));
        headline.setTextFill(Color.WHITE);
        headline.setLineSpacing(5);

        Label subheadline = new Label(
                "A dedicated system for Kilungu Law Courts, manage cases,\ntrack offenders, and stay in sync.");
        subheadline.setFont(Font.font("System", FontWeight.NORMAL, 14));
        subheadline.setTextFill(Color.web("#ffffff58"));
        subheadline.setLineSpacing(4);

        Region featureGap = new Region();
        featureGap.setPrefHeight(44);

        // Features with colored left-bar indicators
        VBox featuresList = new VBox(18,
                featureItem("#2eaadc", "Case management",
                        "Criminal, civil, traffic, succession organised in one view."),
                featureItem("#10b981", "Offender records",
                        "Detailed profiles linked directly to their active cases."),
                featureItem("#8b5cf6", "Sync & offline access",
                        "Works offline; syncs automatically when the device is connected."));

        // Bottom spacer + version badge
        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);

        Label versionBadge = new Label("v" + AppVersion.getVersion());
        versionBadge.setFont(Font.font("System", 10));
        versionBadge.setTextFill(Color.web("#ffffff28"));
        versionBadge.setStyle("-fx-background-color: #ffffff0a; -fx-background-radius: 4; -fx-padding: 3 8 3 8;");

        leftPanel.getChildren().addAll(
                logoRow, topSpacer, headline, subheadline, featureGap, featuresList, bottomSpacer, versionBadge);

        // ── Right panel ──────────────────────────────────────────────────────
        VBox rightPanel = new VBox();
        rightPanel.setAlignment(Pos.CENTER);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        VBox centerContent = new VBox(0);
        centerContent.setAlignment(Pos.CENTER_LEFT);
        centerContent.setMaxWidth(340);

        Label heading = new Label("Welcome");
        heading.setFont(Font.font("System", FontWeight.BOLD, 32));

        Region headingGap = new Region();
        headingGap.setPrefHeight(10);

        Label desc = new Label(
                "Sign in with your court-assigned credentials to access cases, records, and real-time updates.");
        desc.setFont(Font.font("System", FontWeight.NORMAL, 14));
        desc.getStyleClass().add("text-muted");
        desc.setWrapText(true);
        desc.setLineSpacing(3);

        Region btnGap = new Region();
        btnGap.setPrefHeight(28);

        Button signInBtn = new Button("Sign In  →");
        signInBtn.getStyleClass().add("accent");
        signInBtn.setPrefWidth(200);
        signInBtn.setPrefHeight(44);
        signInBtn.setFont(Font.font("System", FontWeight.BOLD, 13));
        signInBtn.setDefaultButton(true);
        signInBtn.setOnAction(e -> onGetStarted.run());

        Region helpGap = new Region();
        helpGap.setPrefHeight(16);

        Label helpText = new Label("Contact your administrator if you need access.");
        helpText.setFont(Font.font("System", 12));
        helpText.getStyleClass().add("text-muted");
        helpText.setWrapText(true);

        centerContent.getChildren().addAll(
                heading, headingGap, desc, btnGap, signInBtn, helpGap, helpText);

        rightPanel.getChildren().add(centerContent);

        root.getChildren().addAll(leftPanel, rightPanel);
    }

    private HBox featureItem(String accentColor, String title, String description) {
        Rectangle bar = new Rectangle(3, 38);
        bar.setFill(Color.web(accentColor));
        bar.setArcWidth(3);
        bar.setArcHeight(3);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.web("#ffffffcf"));

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
        descLabel.setTextFill(Color.web("#ffffff50"));
        descLabel.setWrapText(true);
        descLabel.setLineSpacing(2);

        VBox textBox = new VBox(3, titleLabel, descLabel);

        HBox item = new HBox(14, bar, textBox);
        item.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        return item;
    }

    public Parent getRoot() {
        return root;
    }
}
