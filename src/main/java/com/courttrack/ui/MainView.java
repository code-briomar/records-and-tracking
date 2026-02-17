package com.courttrack.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

public class MainView {
    private final BorderPane root;
    private final StackPane contentArea;
    private final String username;
    private final Runnable onLogout;
    private final List<Button> navButtons = new ArrayList<>();
    private Button activeButton;
    private String currentPage = "dashboard";
    private final ThemeManager tm = ThemeManager.getInstance();

    public MainView(String username, Runnable onLogout) {
        this.username = username;
        this.onLogout = onLogout;
        this.root = new BorderPane();
        this.contentArea = new StackPane();
        buildUI();
        navigateTo("dashboard");
    }

    private void buildUI() {
        root.setLeft(buildSidebar());
        contentArea.setPadding(new Insets(0));
        root.setCenter(contentArea);
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-border-color: transparent %s transparent transparent;
            -fx-border-width: 0 1 0 0;
        """, tm.sidebarBg(), tm.sidebarSep()));

        // App header
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        logoRow.setPadding(new Insets(24, 20, 20, 20));

        StackPane logoIcon = new StackPane();
        logoIcon.setMinSize(36, 36);
        logoIcon.setMaxSize(36, 36);
        logoIcon.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 8;
        """, tm.accentBlue()));
        Label logoText = new Label("CR");
        logoText.setFont(Font.font("System", FontWeight.BOLD, 14));
        logoText.setTextFill(Color.WHITE);
        logoIcon.getChildren().add(logoText);

        VBox titleBox = new VBox(0);
        Label appTitle = new Label("Court Records");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        appTitle.setTextFill(Color.web(tm.sidebarText()));
        Label appSubtitle = new Label("Tracking System");
        appSubtitle.setFont(Font.font("System", 11));
        appSubtitle.setTextFill(Color.web(tm.sidebarMuted()));
        titleBox.getChildren().addAll(appTitle, appSubtitle);

        logoRow.getChildren().addAll(logoIcon, titleBox);

        // Navigation
        VBox navSection = new VBox(2);
        navSection.setPadding(new Insets(8, 12, 8, 12));

        navButtons.clear();
        Button btnDashboard = createNavButton("Dashboard", "dashboard");
        Button btnCases = createNavButton("Cases", "cases");
        Button btnOffenders = createNavButton("Persons", "offenders");

        navSection.getChildren().addAll(btnDashboard, btnCases, btnOffenders);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Bottom section
        VBox bottomBox = new VBox(8);
        bottomBox.setPadding(new Insets(12, 12, 16, 12));

        // Settings
        Button settingsBtn = createNavButton("Settings", null);
        settingsBtn.setOnAction(e -> showSettingsMenu(settingsBtn));
        settingsBtn.setOnMouseEntered(e -> settingsBtn.setStyle(navHoverStyle()));
        settingsBtn.setOnMouseExited(e -> settingsBtn.setStyle(navInactiveStyle()));

        // User card
        VBox userCard = new VBox(10);
        userCard.setPadding(new Insets(12));
        userCard.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 8;
        """, tm.sidebarActive()));

        HBox userRow = new HBox(10);
        userRow.setAlignment(Pos.CENTER_LEFT);

        StackPane userAvatar = new StackPane();
        userAvatar.setMinSize(32, 32);
        userAvatar.setMaxSize(32, 32);
        userAvatar.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 16;
        """, tm.accentPurple()));
        Label avatarInitials = new Label(username.substring(0, Math.min(2, username.length())).toUpperCase());
        avatarInitials.setFont(Font.font("System", FontWeight.BOLD, 12));
        avatarInitials.setTextFill(Color.WHITE);
        userAvatar.getChildren().add(avatarInitials);

        VBox userInfo = new VBox(0);
        Label userName = new Label(username);
        userName.setFont(Font.font("System", FontWeight.MEDIUM, 13));
        userName.setTextFill(Color.web(tm.sidebarText()));
        Label userRole = new Label("Administrator");
        userRole.setFont(Font.font("System", 11));
        userRole.setTextFill(Color.web(tm.sidebarMuted()));
        userInfo.getChildren().addAll(userName, userRole);

        userRow.getChildren().addAll(userAvatar, userInfo);

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setStyle(String.format("""
            -fx-background-color: transparent;
            -fx-text-fill: %s;
            -fx-border-color: %s;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 6 12;
            -fx-font-size: 12px;
            -fx-cursor: hand;
        """, tm.logoutText(), tm.sidebarSep()));
        logoutBtn.setOnAction(e -> onLogout.run());

        userCard.getChildren().addAll(userRow, logoutBtn);
        bottomBox.getChildren().addAll(settingsBtn, userCard);

        sidebar.getChildren().addAll(logoRow, navSection, spacer, bottomBox);
        return sidebar;
    }

    private void showSettingsMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();
        String currentLabel = tm.isDark() ? "Switch to Light Mode" : "Switch to Dark Mode";
        MenuItem themeItem = new MenuItem(currentLabel);
        themeItem.setOnAction(e -> {
            tm.toggle();
            applyTheme();
        });
        menu.getItems().add(themeItem);
        menu.show(anchor, Side.RIGHT, 0, 0);
    }

    private void applyTheme() {
        root.setLeft(buildSidebar());
        navigateTo(currentPage);
    }

    private Button createNavButton(String text, String page) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setPadding(new Insets(10, 14, 10, 14));
        btn.setFont(Font.font("System", 13));
        btn.setStyle(navInactiveStyle());
        btn.setOnMouseEntered(e -> {
            if (btn != activeButton) btn.setStyle(navHoverStyle());
        });
        btn.setOnMouseExited(e -> {
            if (btn != activeButton) btn.setStyle(navInactiveStyle());
        });
        if (page != null) {
            btn.setOnAction(e -> navigateTo(page));
            navButtons.add(btn);
        }
        return btn;
    }

    private String navActiveStyle() {
        return String.format("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 13px;
            -fx-font-weight: bold;
            -fx-cursor: hand;
            -fx-background-radius: 6;
        """, tm.sidebarActive(), tm.sidebarText());
    }

    private String navHoverStyle() {
        return String.format("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 13px;
            -fx-cursor: hand;
            -fx-background-radius: 6;
        """, tm.sidebarHover(), tm.sidebarText());
    }

    private String navInactiveStyle() {
        return String.format("""
            -fx-background-color: transparent;
            -fx-text-fill: %s;
            -fx-font-size: 13px;
            -fx-cursor: hand;
            -fx-background-radius: 6;
        """, tm.sidebarMuted());
    }

    private void navigateTo(String page) {
        currentPage = page;

        for (Button btn : navButtons) {
            btn.setStyle(navInactiveStyle());
            btn.setOnMouseEntered(e -> {
                if (btn != activeButton) btn.setStyle(navHoverStyle());
            });
            btn.setOnMouseExited(e -> {
                if (btn != activeButton) btn.setStyle(navInactiveStyle());
            });
        }

        int idx = switch (page) {
            case "dashboard" -> 0;
            case "cases" -> 1;
            case "offenders" -> 2;
            default -> 0;
        };
        if (idx < navButtons.size()) {
            activeButton = navButtons.get(idx);
            activeButton.setStyle(navActiveStyle());
        }

        contentArea.getChildren().clear();
        Parent view = switch (page) {
            case "dashboard" -> new DashboardView().getRoot();
            case "cases" -> new CaseListView().getRoot();
            case "offenders" -> new OffenderListView().getRoot();
            default -> new DashboardView().getRoot();
        };
        contentArea.getChildren().add(view);
    }

    public Parent getRoot() {
        return root;
    }
}
