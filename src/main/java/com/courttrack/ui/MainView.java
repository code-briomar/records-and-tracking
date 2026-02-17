package com.courttrack.ui;

import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private boolean sidebarCollapsed = false;
    private VBox sidebar;

    private static final double EXPANDED_WIDTH = 240;
    private static final double COLLAPSED_WIDTH = 64;

    // Elements that hide/show on collapse
    private final List<Label> navLabels = new ArrayList<>();
    private VBox titleBox;
    private VBox userInfo;
    private Button logoutBtn;
    private Label settingsLabel;

    public MainView(String username, Runnable onLogout) {
        this.username = username;
        this.onLogout = onLogout;
        this.root = new BorderPane();
        this.contentArea = new StackPane();
        buildUI();
        navigateTo("dashboard");
    }

    private void buildUI() {
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        contentArea.setPadding(new Insets(0));
        root.setCenter(contentArea);
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        double width = sidebarCollapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;
        sb.setPrefWidth(width);
        sb.setMinWidth(width);
        sb.setMaxWidth(width);
        sb.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-border-color: transparent %s transparent transparent;
            -fx-border-width: 0 1 0 0;
        """, tm.sidebarBg(), tm.sidebarSep()));

        // --- Toggle button ---
        Button toggleBtn = new Button();
        FontIcon menuIcon = new FontIcon(sidebarCollapsed ? Feather.CHEVRONS_RIGHT : Feather.CHEVRONS_LEFT);
        menuIcon.setIconSize(16);
        menuIcon.setIconColor(Color.web(tm.sidebarMuted()));
        toggleBtn.setGraphic(menuIcon);
        toggleBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 8;
        """);
        toggleBtn.setTooltip(new Tooltip(sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"));
        toggleBtn.setOnAction(e -> toggleSidebar());

        HBox toggleRow = new HBox(toggleBtn);
        toggleRow.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
        toggleRow.setPadding(new Insets(8, 8, 0, 8));

        // --- App header ---
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        logoRow.setPadding(sidebarCollapsed ? new Insets(12, 0, 16, 0) : new Insets(12, 20, 16, 20));
        if (sidebarCollapsed) logoRow.setAlignment(Pos.CENTER);

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

        titleBox = new VBox(0);
        Label appTitle = new Label("Court Records");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        appTitle.setTextFill(Color.web(tm.sidebarText()));
        Label appSubtitle = new Label("Tracking System");
        appSubtitle.setFont(Font.font("System", 11));
        appSubtitle.setTextFill(Color.web(tm.sidebarMuted()));
        titleBox.getChildren().addAll(appTitle, appSubtitle);

        logoRow.getChildren().add(logoIcon);
        if (!sidebarCollapsed) {
            logoRow.getChildren().add(titleBox);
        }

        // --- Navigation ---
        VBox navSection = new VBox(2);
        navSection.setPadding(new Insets(8, 8, 8, 8));

        navButtons.clear();
        navLabels.clear();
        navSection.getChildren().addAll(
            createNavButton("Dashboard", "dashboard", Feather.HOME),
            createNavButton("Cases", "cases", Feather.FOLDER),
            createNavButton("Persons", "offenders", Feather.USERS)
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // --- Bottom section ---
        VBox bottomBox = new VBox(8);
        bottomBox.setPadding(new Insets(12, 8, 16, 8));

        // Settings button
        Button settingsBtn = new Button();
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        settingsBtn.setPadding(new Insets(10, 14, 10, 14));

        FontIcon settingsIcon = new FontIcon(Feather.SETTINGS);
        settingsIcon.setIconSize(16);
        settingsIcon.setIconColor(Color.web(tm.sidebarMuted()));

        settingsLabel = new Label("Settings");
        settingsLabel.setFont(Font.font("System", 13));
        settingsLabel.setTextFill(Color.web(tm.sidebarMuted()));

        HBox settingsContent = new HBox(10, settingsIcon);
        settingsContent.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        if (!sidebarCollapsed) settingsContent.getChildren().add(settingsLabel);
        settingsBtn.setGraphic(settingsContent);
        settingsBtn.setText(null);
        settingsBtn.setStyle(navInactiveStyle());
        settingsBtn.setTooltip(new Tooltip("Settings"));
        settingsBtn.setOnAction(e -> showSettingsDialog());
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
        userRow.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);

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

        userInfo = new VBox(0);
        Label userName = new Label(username);
        userName.setFont(Font.font("System", FontWeight.MEDIUM, 13));
        userName.setTextFill(Color.web(tm.sidebarText()));
        Label userRole = new Label("Administrator");
        userRole.setFont(Font.font("System", 11));
        userRole.setTextFill(Color.web(tm.sidebarMuted()));
        userInfo.getChildren().addAll(userName, userRole);

        userRow.getChildren().add(userAvatar);
        if (!sidebarCollapsed) userRow.getChildren().add(userInfo);

        logoutBtn = new Button("Sign Out");
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

        userCard.getChildren().add(userRow);
        if (!sidebarCollapsed) userCard.getChildren().add(logoutBtn);

        if (sidebarCollapsed) {
            Tooltip.install(userAvatar, new Tooltip(username + "\nClick avatar to sign out"));
            userAvatar.setOnMouseClicked(e -> onLogout.run());
        }

        bottomBox.getChildren().addAll(settingsBtn, userCard);

        sb.getChildren().addAll(toggleRow, logoRow, navSection, spacer, bottomBox);
        return sb;
    }

    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        rebuildSidebar();
    }

    private void rebuildSidebar() {
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        // Restore active button state
        int idx = switch (currentPage) {
            case "dashboard" -> 0;
            case "cases" -> 1;
            case "offenders" -> 2;
            default -> 0;
        };
        if (idx < navButtons.size()) {
            activeButton = navButtons.get(idx);
            activeButton.setStyle(navActiveStyle());
        }
    }

    private void showSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText(null);
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(16);
        content.setPadding(new Insets(20, 28, 12, 28));
        content.setPrefWidth(400);

        // Header
        FontIcon settingsIcon = new FontIcon(Feather.SETTINGS);
        settingsIcon.setIconSize(18);
        settingsIcon.setIconColor(Color.web(tm.accentBlue()));
        Label titleLabel = new Label("Settings");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        HBox header = new HBox(10, settingsIcon, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();

        // Theme toggle
        FontIcon themeIcon = new FontIcon(tm.isDark() ? Feather.SUN : Feather.MOON);
        themeIcon.setIconSize(16);
        themeIcon.setIconColor(Color.web(tm.accentOrange()));

        Label themeLabel = new Label("Theme");
        themeLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        Label themeDesc = new Label(tm.isDark() ? "Currently using dark mode" : "Currently using light mode");
        themeDesc.setFont(Font.font("System", 12));
        themeDesc.getStyleClass().add("text-muted");

        VBox themeText = new VBox(2, themeLabel, themeDesc);
        HBox.setHgrow(themeText, Priority.ALWAYS);

        Button themeToggle = new Button(tm.isDark() ? "Switch to Light" : "Switch to Dark");
        themeToggle.getStyleClass().add("accent");
        themeToggle.setOnAction(e -> {
            tm.toggle();
            applyTheme();
            dialog.close();
        });

        HBox themeRow = new HBox(12, themeIcon, themeText, themeToggle);
        themeRow.setAlignment(Pos.CENTER_LEFT);
        themeRow.setPadding(new Insets(8, 0, 8, 0));

        // Sidebar toggle
        FontIcon sidebarIcon = new FontIcon(Feather.SIDEBAR);
        sidebarIcon.setIconSize(16);
        sidebarIcon.setIconColor(Color.web(tm.accentGreen()));

        Label sidebarLabel = new Label("Sidebar");
        sidebarLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        Label sidebarDesc = new Label(sidebarCollapsed ? "Currently collapsed" : "Currently expanded");
        sidebarDesc.setFont(Font.font("System", 12));
        sidebarDesc.getStyleClass().add("text-muted");

        VBox sidebarText = new VBox(2, sidebarLabel, sidebarDesc);
        HBox.setHgrow(sidebarText, Priority.ALWAYS);

        Button sidebarToggle = new Button(sidebarCollapsed ? "Expand" : "Collapse");
        sidebarToggle.setOnAction(e -> {
            toggleSidebar();
            dialog.close();
        });

        HBox sidebarRow = new HBox(12, sidebarIcon, sidebarText, sidebarToggle);
        sidebarRow.setAlignment(Pos.CENTER_LEFT);
        sidebarRow.setPadding(new Insets(8, 0, 8, 0));

        content.getChildren().addAll(header, sep, themeRow, new Separator(), sidebarRow);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void applyTheme() {
        rebuildSidebar();
        navigateTo(currentPage);
    }

    private Button createNavButton(String text, String page, Feather icon) {
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        btn.setPadding(new Insets(10, 14, 10, 14));

        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(16);
        fi.setIconColor(Color.web(tm.sidebarMuted()));

        Label label = new Label(text);
        label.setFont(Font.font("System", 13));
        label.setTextFill(Color.web(tm.sidebarMuted()));
        navLabels.add(label);

        HBox content = new HBox(10, fi);
        content.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        if (!sidebarCollapsed) content.getChildren().add(label);

        btn.setGraphic(content);
        btn.setText(null);
        btn.setStyle(navInactiveStyle());
        btn.setTooltip(new Tooltip(text));

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
            case "dashboard" -> new DashboardView(() -> navigateTo("cases"), () -> navigateTo("offenders"), this::showCaseDetail).getRoot();
            case "cases" -> new CaseListView(this::showCaseDetail).getRoot();
            case "offenders" -> new OffenderListView(this::showPersonDetail).getRoot();
            default -> new DashboardView(() -> navigateTo("cases"), () -> navigateTo("offenders"), this::showCaseDetail).getRoot();
        };
        contentArea.getChildren().add(view);
    }

    public void showCaseDetail(CourtCase courtCase) {
        contentArea.getChildren().clear();
        CaseDetailView detailView = new CaseDetailView(courtCase, () -> navigateTo("cases"));
        contentArea.getChildren().add(detailView.getRoot());
    }

    public void showPersonDetail(Person person) {
        contentArea.getChildren().clear();
        PersonDetailView detailView = new PersonDetailView(person, () -> navigateTo("offenders"));
        contentArea.getChildren().add(detailView.getRoot());
    }

    public Parent getRoot() {
        return root;
    }
}
