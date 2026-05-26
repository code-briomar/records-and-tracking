package com.courttrack.util;

import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Utility to load and apply the application logo/icon to dialogs and alerts.
 */
public class DialogUtil {
    private static Image appIcon = null;

    /**
     * Retrieves the cached application icon, or loads it from resources if not cached.
     *
     * @return the Image resource of the app icon, or null if it cannot be loaded.
     */
    public static Image getAppIcon() {
        if (appIcon == null) {
            try {
                appIcon = new Image(DialogUtil.class.getResourceAsStream("/icons/app.png"));
            } catch (Exception e) {
                System.err.println("Could not load app icon for dialogs: " + e.getMessage());
            }
        }
        return appIcon;
    }

    /**
     * Applies the application logo/icon to the given Dialog or Alert.
     *
     * @param dialog the JavaFX Dialog or Alert instance
     */
    public static void applyIcon(Dialog<?> dialog) {
        if (dialog == null) return;
        try {
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            if (stage != null) {
                Image icon = getAppIcon();
                if (icon != null) {
                    stage.getIcons().add(icon);
                }
            }
        } catch (Exception e) {
            System.err.println("Could not set dialog icon: " + e.getMessage());
        }
    }
}
