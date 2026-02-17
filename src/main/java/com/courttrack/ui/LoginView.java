package com.courttrack.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.function.Consumer;

public class LoginView {
    private final StackPane root;
    private final Consumer<String> onLoginSuccess;
    private final ThemeManager tm = ThemeManager.getInstance();

    public LoginView(Consumer<String> onLoginSuccess) {
        this.onLoginSuccess = onLoginSuccess;
        this.root = new StackPane();
        buildUI();
    }

    private void buildUI() {
        VBox card = new VBox(20);
        card.setMaxWidth(380);
        card.setMaxHeight(460);
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

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        Label errorLabel = new Label();
        errorLabel.setTextFill(Color.web(tm.accentRed()));
        errorLabel.setFont(Font.font("System", 12));
        errorLabel.setVisible(false);

        Button loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setDefaultButton(true);
        loginBtn.getStyleClass().add("accent");

        Label hint = new Label("Prototype \u2014 use any credentials to sign in");
        hint.setFont(Font.font("System", 11));
        hint.getStyleClass().add("text-muted");

        Runnable doLogin = () -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText().trim();
            if (username.isEmpty() || password.isEmpty()) {
                errorLabel.setText("Please enter username and password");
                errorLabel.setVisible(true);
                return;
            }
            onLoginSuccess.accept(username);
        };

        loginBtn.setOnAction(e -> doLogin.run());
        passwordField.setOnAction(e -> doLogin.run());

        VBox headerBox = new VBox(4, icon, title, subtitle);
        headerBox.setAlignment(Pos.CENTER);

        Region spacer = new Region();
        spacer.setPrefHeight(8);

        card.getChildren().addAll(headerBox, spacer, usernameField, passwordField, errorLabel, loginBtn, hint);

        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    public Parent getRoot() {
        return root;
    }
}
