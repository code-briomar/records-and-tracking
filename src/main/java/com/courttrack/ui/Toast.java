package com.courttrack.ui;
// import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Screen;
public class Toast {
    private static Popup popup;
    public static void showError(String message) {
        show(message, "#e74c3c", "#fff");
    }
    public static void showSuccess(String message) {
        show(message, "#2ecc71", "#fff");
    }
    public static void showInfo(String message) {
        show(message, "#3498db", "#fff");
    }
    private static void show(String message, String bgColor, String textColor) {
        Label label = new Label(message);
        label.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 15 25; " +
            "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 8;",
            bgColor, textColor
        ));
        popup = new Popup();
        popup.getContent().add(label);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        var primaryScreen = Screen.getPrimary();
        double boundsMaxX = primaryScreen.getVisualBounds().getMaxX();
        double boundsMaxY = primaryScreen.getVisualBounds().getMaxY();
        popup.show(javafx.stage.Window.getWindows().get(0), boundsMaxX - 380, boundsMaxY - 80);
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                javafx.application.Platform.runLater(popup::hide);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}