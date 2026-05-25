package com.courttrack.ui;

import java.io.InputStream;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LoginView {
    private final StackPane root;
    private final LoginCallback onLoginAttempt;
    private final Runnable onBack;
    private final ThemeManager tm = ThemeManager.getInstance();
    private Label errorLabel;
    private Button loginBtn;

    private record CourtEntry(String name, String id) {
        @Override
        public String toString() {
            return name;
        }
    }

    @FunctionalInterface
    public interface LoginCallback {
        void onLogin(String courtId, String email, String password);
    }

    public LoginView(LoginCallback onLoginAttempt, Runnable onBack) {
        this.onLoginAttempt = onLoginAttempt;
        this.onBack = onBack;
        this.root = new StackPane();
        buildUI();
    }

    private void buildUI() {
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0a0a0f, #111122);");

        VBox form = new VBox(0);
        form.setMaxWidth(360);
        form.setAlignment(Pos.CENTER_LEFT);

        // ── Icon + heading ────────────────────────────────────────────────────
        ImageView icon = new ImageView();
        icon.setFitWidth(48);
        icon.setFitHeight(48);
        icon.setPreserveRatio(true);
        InputStream iconIs = LoginView.class.getResourceAsStream("/icons/app.png");
        if (iconIs != null) {
            icon.setImage(new Image(iconIs, 48, 48, true, true));
        }

        Label title = new Label("Sign In");
        title.setFont(Font.font("System", FontWeight.BOLD, 30));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("Kilungu Law Courts");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 13));
        subtitle.setTextFill(Color.web("#ffffff45"));

        VBox heading = new VBox(6, icon, title, subtitle);
        heading.setAlignment(Pos.CENTER_LEFT);

        Region afterHeading = new Region();
        afterHeading.setPrefHeight(36);

        // ── Court dropdown ────────────────────────────────────────────────────
        ComboBox<CourtEntry> courtDropdown = new ComboBox<>();
        courtDropdown.getItems().add(new CourtEntry("Kilungu Law Courts", "KILUN6U2026"));
        courtDropdown.getSelectionModel().selectFirst();
        courtDropdown.setMaxWidth(Double.MAX_VALUE);

        VBox courtGroup = fieldGroup("Court", courtDropdown, null);

        // ── Email ─────────────────────────────────────────────────────────────
        TextField emailField = new TextField();
        emailField.setPromptText("you@example.com");
        VBox emailGroup = fieldGroup("Email address", emailField, null);

        // ── Password ──────────────────────────────────────────────────────────
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("••••••••");
        VBox passwordGroup = fieldGroup("Password", passwordField, null);

        // ── Error label ───────────────────────────────────────────────────────
        errorLabel = new Label();
        errorLabel.setTextFill(Color.web(tm.accentRed()));
        errorLabel.setFont(Font.font("System", 12));
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Region beforeBtn = new Region();
        beforeBtn.setPrefHeight(8);

        // ── Sign In button ────────────────────────────────────────────────────
        loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        loginBtn.getStyleClass().add("accent");
        loginBtn.setPrefHeight(46);
        loginBtn.setFont(Font.font("System", FontWeight.BOLD, 14));

        Runnable doLogin = () -> {
            CourtEntry selected = courtDropdown.getValue();
            String courtId = selected != null ? selected.id() : "";
            String email = emailField.getText().trim();
            String password = passwordField.getText().trim();
            if (courtId.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showError("Please select a court, enter your email, and password.");
                return;
            }
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            setLoading(true);
            onLoginAttempt.onLogin(courtId, email, password);
        };

        loginBtn.setOnAction(e -> doLogin.run());
        passwordField.setOnAction(e -> doLogin.run());
        emailField.setOnAction(e -> passwordField.requestFocus());

        Region afterBtn = new Region();
        afterBtn.setPrefHeight(20);

        // ── Back link ─────────────────────────────────────────────────────────
        HBox backRow = new HBox();
        backRow.setAlignment(Pos.CENTER_LEFT);

        Hyperlink backLink = new Hyperlink("← Back");
        backLink.setFont(Font.font("System", 12));
        backLink.setTextFill(Color.web("#ffffff40"));
        backLink.setBorder(Border.EMPTY);
        backLink.setOnAction(e -> onBack.run());
        backLink.setPadding(new Insets(0));

        backRow.getChildren().add(backLink);

        form.getChildren().addAll(
                heading, afterHeading,
                courtGroup, emailGroup, passwordGroup,
                errorLabel, beforeBtn,
                loginBtn, afterBtn,
                backRow);

        root.getChildren().add(form);
        StackPane.setAlignment(form, Pos.CENTER);
    }

    private VBox fieldGroup(String labelText, Control field, String helperText) {
        Label label = new Label(labelText);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setTextFill(Color.web("#ffffffaa"));

        VBox group = new VBox(5, label, field);

        if (helperText != null) {
            Label helper = new Label(helperText);
            helper.setFont(Font.font("System", 11));
            helper.getStyleClass().add("text-muted");
            helper.setWrapText(true);
            group.getChildren().add(helper);
        }

        group.setPadding(new Insets(0, 0, 16, 0));
        return group;
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        setLoading(false);
    }

    public void setLoading(boolean loading) {
        loginBtn.setDisable(loading);
        loginBtn.setText(loading ? "Signing in..." : "Sign In");
    }

    public Parent getRoot() {
        return root;
    }
}
