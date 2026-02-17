package com.courttrack;

/**
 * Non-Application launcher class required for shaded JAR with JavaFX.
 * jpackage needs a main class that does not extend Application.
 */
public class AppLauncher {
    public static void main(String[] args) {
        App.main(args);
    }
}
