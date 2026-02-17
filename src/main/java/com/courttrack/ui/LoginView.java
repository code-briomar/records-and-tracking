package com.courttrack.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LoginView {
    private final StackPane root;
    private final LoginCallback onLoginAttempt;
    private final ThemeManager tm = ThemeManager.getInstance();
    private Label errorLabel;
    private Button loginBtn;

    @FunctionalInterface
    public interface LoginCallback {
        void onLogin(String courtId, String email, String password);
    }

    public LoginView(LoginCallback onLoginAttempt) {
        this.onLoginAttempt = onLoginAttempt;
        this.root = new StackPane();
        buildUI();
    }

    private void buildUI() {
        VBox card = new VBox(20);
        card.setMaxWidth(380);
        card.setMaxHeight(520);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));
        card.setStyle("""
            -fx-background-radius: 8;
            -fx-effect: dropshadow(gaussian, rgba(15,15,15,0.1), 12, 0, 0, 2);
        """);
        card.getStyleClass().add("bordered");

        Label icon = new Label("\u2696");
        icon.setFont(Font.font("System", FontWeight.NORMAL, 44));

        Label title = new Label("Court Records & Tracking");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));

        Label subtitle = new Label("Judiciary of Kenya");
        subtitle.setFont(Font.font("System", FontWeight.NORMAL, 13));
        subtitle.getStyleClass().add("text-muted");

        TextField courtIdField = new TextField();
        courtIdField.setPromptText("Court ID");

        TextField emailField = new TextField();
        emailField.setPromptText("Email");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        errorLabel = new Label();
        errorLabel.setTextFill(Color.web(tm.accentRed()));
        errorLabel.setFont(Font.font("System", 12));
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);

        loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        loginBtn.getStyleClass().add("accent");

        Runnable doLogin = () -> {
            String courtId = courtIdField.getText().trim();
            String email = emailField.getText().trim();
            String password = passwordField.getText().trim();
            if (courtId.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showError("Please enter Court ID, email and password");
                return;
            }
            errorLabel.setVisible(false);
            setLoading(true);
            onLoginAttempt.onLogin(courtId, email, password);
        };

        loginBtn.setOnAction(e -> doLogin.run());
        passwordField.setOnAction(e -> doLogin.run());

        VBox headerBox = new VBox(4, icon, title, subtitle);
        headerBox.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        spacer.setPrefHeight(8);

        card.getChildren().addAll(headerBox, spacer, courtIdField, emailField, passwordField, errorLabel, loginBtn);

        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
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
