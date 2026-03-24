package com.courttrack.ui;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import javafx.application.Application;
import javafx.beans.property.SimpleBooleanProperty;

public class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();
    private final SimpleBooleanProperty darkMode = new SimpleBooleanProperty(true);

    private ThemeManager() {}

    public static ThemeManager getInstance() { return INSTANCE; }

    public boolean isDark() { return darkMode.get(); }
    public SimpleBooleanProperty darkModeProperty() { return darkMode; }
    public void setDarkMode(boolean dark) { darkMode.set(dark); }

    public void toggle() {
        darkMode.set(!darkMode.get());
        applyTheme();
    }

    /** Sets the AtlantaFX user-agent stylesheet based on current mode. */
    public void applyTheme() {
        if (isDark()) {
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
        }
    }

    // --- Sidebar (app-specific, not covered by AtlantaFX) ---
    public String sidebarBg() { return isDark() ? "#191919" : "#f7f6f3"; }
    public String sidebarText() { return isDark() ? "#ffffffcf" : "#37352f"; }
    public String sidebarMuted() { return isDark() ? "#ffffff71" : "#91918e"; }
    public String sidebarSep() { return isDark() ? "#ffffff14" : "#e3e2de"; }
    public String sidebarHover() { return isDark() ? "#ffffff14" : "#ebebea"; }
    public String sidebarActive() { return isDark() ? "#ffffff14" : "#efefef"; }

    // --- Accents (shared) ---
    public String accentBlue() { return "#2eaadc"; }
    public String accentGreen() { return "#0f7b6c"; }
    public String accentOrange() { return "#d9730d"; }
    public String accentRed() { return "#e03e3e"; }
    public String accentPurple() { return "#9065b0"; }

    // --- Badge backgrounds (app-specific semantic colors) ---
    public String badgeOpenBg() { return isDark() ? "#0f7b6c33" : "#dbeddb"; }
    public String badgeOpenText() { return isDark() ? "#4dab9a" : "#0f7b6c"; }
    public String badgeClosedBg() { return isDark() ? "#d9730d33" : "#fdecc8"; }
    public String badgeClosedText() { return isDark() ? "#e8a54b" : "#d9730d"; }
    public String badgeCriminalBg() { return isDark() ? "#e03e3e33" : "#fbe4e4"; }
    public String badgeCriminalText() { return isDark() ? "#eb7272" : "#e03e3e"; }
    public String badgeTrafficBg() { return isDark() ? "#d9730d33" : "#fdecc8"; }
    public String badgeTrafficText() { return isDark() ? "#e8a54b" : "#d9730d"; }
    public String badgeCivilBg() { return isDark() ? "#2eaadc33" : "#ddebf1"; }
    public String badgeCivilText() { return isDark() ? "#68c4e8" : "#2eaadc"; }
    public String badgeSuccessionBg() { return isDark() ? "#9065b033" : "#ede9f5"; }
    public String badgeSuccessionText() { return isDark() ? "#b899d4" : "#9065b0"; }
    public String badgeChildrenBg() { return isDark() ? "#d55e8a33" : "#fce8f0"; }
    public String badgeChildrenText() { return isDark() ? "#e896b8" : "#d55e8a"; }
    public String badgeOtherBg() { return isDark() ? "#66666633" : "#ebebeb"; }
    public String badgeOtherText() { return isDark() ? "#aaaaaa" : "#555555"; }

    // --- Misc ---
    public String logoutText() { return "#eb5757"; }

    /** Path to supplemental app CSS (loaded on top of AtlantaFX). */
    public String getSupplementalCssPath() {
        return "/styles/app.css";
    }
}
