package com.courttrack;

import com.courttrack.db.DatabaseManager;
import com.courttrack.ui.LoginView;
import com.courttrack.ui.MainView;
import com.courttrack.ui.ThemeManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        DatabaseManager.getInstance().initialize();

        // Apply AtlantaFX theme before showing any UI
        ThemeManager.getInstance().applyTheme();

        showLogin();

        stage.setTitle("Court Records & Tracking System - Judiciary of Kenya");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setWidth(1200);
        stage.setHeight(800);
        stage.show();
    }

    private void showLogin() {
        LoginView loginView = new LoginView(this::onLoginSuccess);
        Scene scene = new Scene(loginView.getRoot(), 1200, 800);
        addSupplementalCss(scene);
        primaryStage.setScene(scene);
    }

    private void onLoginSuccess(String username) {
        MainView mainView = new MainView(username, this::showLogin);
        Scene scene = new Scene(mainView.getRoot(), 1200, 800);
        addSupplementalCss(scene);
        primaryStage.setScene(scene);
    }

    private void addSupplementalCss(Scene scene) {
        ThemeManager tm = ThemeManager.getInstance();
        String css = getClass().getResource(tm.getSupplementalCssPath()).toExternalForm();
        scene.getStylesheets().add(css);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
