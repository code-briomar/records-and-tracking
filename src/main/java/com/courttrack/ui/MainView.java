package com.courttrack.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import com.courttrack.repository.CaseRepository;
import com.courttrack.sync.SyncStatus;
import com.courttrack.update.UpdateInfo;
import com.courttrack.util.DialogUtil;
import com.courttrack.util.VersionPreferences;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.util.Duration;

public class MainView {

    private final BorderPane root;
    private final StackPane contentArea;
    private final String username;
    private final Runnable onLogout;
    private final List<Button> navButtons = new ArrayList<>();
    private Button activeButton;
    private String currentPage = "dashboard";
    private String previousPage = "dashboard";
    private boolean showingDetail = false;
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

    // Update notification
    private VBox centerWrapper;
    private UpdateNotificationBar updateBar;

    // Cached views - created once, reused forever
    private DashboardView cachedDashboard;
    private CaseListView cachedCases;
    private OffenderListView cachedOffenders;
    private ReportsView cachedReports;

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

        centerWrapper = new VBox();
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        centerWrapper.getChildren().add(contentArea);
        root.setCenter(centerWrapper);
    }

    /**
     * Called from App.java when an update is detected. Shows a notification bar
     * above the content area.
     */
    public void showUpdateNotification(UpdateInfo updateInfo) {
        if (updateBar != null) {
            return; // already showing

        }
        updateBar = new UpdateNotificationBar(updateInfo, () -> {
            centerWrapper.getChildren().remove(updateBar);
            updateBar = null;
        });
        centerWrapper.getChildren().add(0, updateBar);
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
        if (sidebarCollapsed) {
            logoRow.setAlignment(Pos.CENTER);
        }

        StackPane logoIcon = new StackPane();
        logoIcon.setMinSize(36, 36);
        logoIcon.setMaxSize(36, 36);
        logoIcon.setStyle(String.format("""
                    -fx-background-color: %s;
                    -fx-background-radius: 8;
                """, tm.accentBlue()));
        Label logoText = new Label("R&T");
        logoText.setFont(Font.font("System", FontWeight.BOLD, 14));
        logoText.setTextFill(Color.WHITE);
        logoIcon.getChildren().add(logoText);

        titleBox = new VBox(0);
        Label appTitle = new Label("Kilungu Law Courts");
        appTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        appTitle.setTextFill(Color.web(tm.sidebarText()));
        Label appSubtitle = new Label("Records & Tracking");
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
                createNavButton("Offenders", "offenders", Feather.USERS),
                createNavButton("Reports", "reports", Feather.BAR_CHART_2));

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
        if (!sidebarCollapsed) {
            settingsContent.getChildren().add(settingsLabel);
        }
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
        if (!sidebarCollapsed) {
            userRow.getChildren().add(userInfo);
        }

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
        logoutBtn.setOnAction(e -> {
            VersionPreferences.getInstance().clearSession();
            onLogout.run();
        });

        userCard.getChildren().add(userRow);
        if (!sidebarCollapsed) {
            userCard.getChildren().add(logoutBtn);
        }

        if (sidebarCollapsed) {
            Tooltip.install(userAvatar, new Tooltip(username + "\nClick avatar to sign out"));
            userAvatar.setOnMouseClicked(e -> {
                VersionPreferences.getInstance().clearSession();
                onLogout.run();
            });
        }

        // Sync status indicator
        HBox syncRow = buildSyncIndicator();
        bottomBox.getChildren().addAll(syncRow, settingsBtn, userCard);

        sb.getChildren().addAll(toggleRow, logoRow, navSection, spacer, bottomBox);
        return sb;
    }

    private HBox buildSyncIndicator() {
        SyncStatus sync = SyncStatus.getInstance();

        FontIcon icon = new FontIcon(Feather.CLOUD_OFF);
        icon.setIconSize(16);

        Label label = new Label();
        label.setFont(Font.font("System", 12));

        // Manual sync button with spin animation
        FontIcon syncBtnIcon = new FontIcon(Feather.REFRESH_CW);
        syncBtnIcon.setIconSize(14);
        syncBtnIcon.setIconColor(Color.web(tm.sidebarMuted()));

        RotateTransition spin = new RotateTransition(Duration.millis(800), syncBtnIcon);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);

        Button syncBtn = new Button();
        syncBtn.setGraphic(syncBtnIcon);
        syncBtn.setStyle("""
                    -fx-background-color: transparent;
                    -fx-cursor: hand;
                    -fx-padding: 4;
                    -fx-background-radius: 4;
                """);
        syncBtn.setTooltip(new Tooltip("Sync Now"));
        syncBtn.setOnAction(e -> {
            new Thread(() -> com.courttrack.sync.SyncCoordinator.getInstance().syncAll(true)).start();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, icon);
        row.setAlignment(sidebarCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 14, 8, 14));
        row.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 6;", tm.sidebarActive()));
        if (!sidebarCollapsed) {
            row.getChildren().addAll(label, spacer, syncBtn);
        }

        Runnable updateIndicator = () -> {
            SyncStatus.State state = sync.getState();
            syncBtn.setDisable(state == SyncStatus.State.SYNCING);
            if (state == SyncStatus.State.SYNCING) {
                spin.play();
                syncBtnIcon.setIconColor(Color.web(tm.accentBlue()));
            } else {
                spin.stop();
                syncBtnIcon.setRotate(0);
                syncBtnIcon.setIconColor(Color.web(tm.sidebarMuted()));
            }
            switch (state) {
                case SYNCED -> {
                    icon.setIconCode(Feather.CLOUD);
                    icon.setIconColor(Color.web(tm.accentGreen()));
                    label.setText(sync.messageProperty().get());
                    label.setTextFill(Color.web(tm.accentGreen()));
                }
                case SYNCING -> {
                    icon.setIconCode(Feather.REFRESH_CW);
                    icon.setIconColor(Color.web(tm.accentBlue()));
                    label.setText(sync.messageProperty().get());
                    label.setTextFill(Color.web(tm.accentBlue()));
                }
                case ERROR -> {
                    icon.setIconCode(Feather.CLOUD_OFF);
                    icon.setIconColor(Color.web(tm.accentRed()));
                    label.setText(sync.messageProperty().get());
                    label.setTextFill(Color.web(tm.accentRed()));
                }
                case OFFLINE -> {
                    icon.setIconCode(Feather.CLOUD_OFF);
                    icon.setIconColor(Color.web(tm.sidebarMuted()));
                    label.setText("Offline");
                    label.setTextFill(Color.web(tm.sidebarMuted()));
                }
            }
        };

        // Initial state
        updateIndicator.run();

        // Listen for changes
        sync.stateProperty().addListener((obs, oldVal, newVal) -> updateIndicator.run());
        sync.messageProperty().addListener((obs, oldVal, newVal) -> updateIndicator.run());

        Tooltip tip = new Tooltip();
        tip.textProperty().bind(sync.messageProperty());
        Tooltip.install(row, tip);

        return row;
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
            case "reports" -> 3;
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
        DialogUtil.applyIcon(dialog);

        String bg       = tm.isDark() ? "#161616" : "#f4f4f4";
        String cardBg   = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String cardBdr  = tm.isDark() ? "#383838" : "#dedede";
        String mutedClr = tm.isDark() ? "#8a8a8a" : "#5a5a5a";

        // ---- helper: section card ----
        // builds a titled card with a left accent bar
        java.util.function.BiFunction<String, Feather, VBox[]> makeCard = (title, icon) -> {
            VBox card = new VBox(0);
            card.setStyle(String.format("-fx-background-color: %s; -fx-border-color: %s;" +
                    "-fx-border-width:1; -fx-border-radius:8; -fx-background-radius:8;", cardBg, cardBdr));

            Region accent = new Region();
            accent.setPrefWidth(3); accent.setMinWidth(3);
            accent.setStyle("-fx-background-color: " + tm.accentBlue() + "; -fx-background-radius: 2 0 0 0;");

            FontIcon fi = new FontIcon(icon); fi.setIconSize(14);
            fi.setIconColor(Color.web(tm.accentBlue()));
            Label lbl = new Label(title);
            lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

            HBox inner = new HBox(8, fi, lbl);
            inner.setAlignment(Pos.CENTER_LEFT);
            inner.setPadding(new Insets(11, 14, 9, 12));
            HBox.setHgrow(inner, Priority.ALWAYS);

            HBox header = new HBox(0, accent, inner);
            header.setAlignment(Pos.CENTER_LEFT);
            header.setStyle(String.format("-fx-border-color: transparent transparent %s transparent;" +
                    "-fx-border-width:0 0 1 0; -fx-background-radius:8 8 0 0;", cardBdr));

            VBox body = new VBox(0);
            body.setPadding(new Insets(14, 18, 16, 18));
            card.getChildren().addAll(header, body);
            return new VBox[]{card, body};
        };

        // ---- helper: setting row ----
        java.util.function.Function<String[], HBox> makeRow = parts -> {
            Label name = new Label(parts[0]);
            name.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
            Label desc = new Label(parts[1]);
            desc.setFont(Font.font("System", 11));
            desc.setStyle("-fx-text-fill: " + mutedClr + ";");
            VBox text = new VBox(2, name, desc);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(12, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));
            return row;
        };

        // ================================================================
        // Section 1: Appearance
        // ================================================================
        VBox[] appearanceCard = makeCard.apply("Appearance", Feather.MONITOR);
        VBox appearanceBody = appearanceCard[1];

        // Theme row
        HBox themeRow = makeRow.apply(new String[]{
                "Theme",
                tm.isDark() ? "Dark mode is active" : "Light mode is active"
        });
        FontIcon themeIcon = new FontIcon(tm.isDark() ? Feather.SUN : Feather.MOON);
        themeIcon.setIconSize(15); themeIcon.setIconColor(Color.web(tm.accentOrange()));
        Button themeBtn = new Button(tm.isDark() ? "Switch to Light" : "Switch to Dark");
        themeBtn.getStyleClass().add("accent");
        themeBtn.setOnAction(e -> { tm.toggle(); applyTheme(); dialog.close(); });
        themeRow.getChildren().add(0, themeIcon);
        themeRow.getChildren().add(themeBtn);

        // Sidebar row
        HBox sidebarRow = makeRow.apply(new String[]{
                "Sidebar",
                sidebarCollapsed ? "Currently collapsed" : "Currently expanded"
        });
        FontIcon sbIcon = new FontIcon(Feather.SIDEBAR);
        sbIcon.setIconSize(15); sbIcon.setIconColor(Color.web(tm.accentGreen()));
        Button sidebarBtn = new Button(sidebarCollapsed ? "Expand" : "Collapse");
        sidebarBtn.setOnAction(e -> { toggleSidebar(); dialog.close(); });
        sidebarRow.getChildren().add(0, sbIcon);
        sidebarRow.getChildren().add(sidebarBtn);

        appearanceBody.getChildren().addAll(themeRow, new Separator(), sidebarRow);

        // ================================================================
        // Section 2: Session / Court
        // ================================================================
        VBox[] sessionCard = makeCard.apply("Session", Feather.USER);
        VBox sessionBody = sessionCard[1];

        com.courttrack.sync.CourtContext ctx = com.courttrack.sync.CourtContext.getInstance();
        sessionBody.getChildren().addAll(
                infoRow("Logged in as", username, mutedClr),
                infoRow("Email", ctx.isBound() ? ctx.getUserEmail() : "—", mutedClr),
                infoRow("Role", ctx.isBound() ? ctx.getUserRole() : "—", mutedClr),
                new Separator(),
                infoRow("Court", ctx.isBound() ? ctx.getCourtName() : "Not bound", mutedClr),
                infoRow("Court ID", ctx.isBound() ? ctx.getCourtId() : "—", mutedClr)
        );

        // ================================================================
        // Section 3: Sync
        // ================================================================
        VBox[] syncCard = makeCard.apply("Sync", Feather.REFRESH_CW);
        VBox syncBody = syncCard[1];

        SyncStatus syncStatus = SyncStatus.getInstance();

        // Status badge
        String stateText = switch (syncStatus.getState()) {
            case SYNCED  -> "Up to date";
            case SYNCING -> "Syncing…";
            case ERROR   -> "Sync error";
            case OFFLINE -> "Offline";
        };
        String stateDot = switch (syncStatus.getState()) {
            case SYNCED  -> tm.accentGreen();
            case SYNCING -> tm.accentBlue();
            case ERROR   -> tm.accentRed();
            case OFFLINE -> mutedClr;
        };

        Region dot = new Region();
        dot.setPrefSize(8, 8); dot.setMinSize(8, 8);
        dot.setStyle("-fx-background-color: " + stateDot + "; -fx-background-radius: 4;");
        Label stateLabel = new Label(stateText);
        stateLabel.setFont(Font.font("System", 12));
        Label msgLabel = new Label(syncStatus.messageProperty().get());
        msgLabel.setFont(Font.font("System", 11));
        msgLabel.setStyle("-fx-text-fill: " + mutedClr + ";");
        msgLabel.setWrapText(true);
        HBox.setHgrow(msgLabel, Priority.ALWAYS);

        // Force sync button
        FontIcon syncIcon = new FontIcon(Feather.UPLOAD_CLOUD);
        syncIcon.setIconSize(13); syncIcon.setIconColor(Color.web(tm.accentBlue()));
        Button syncNowBtn = new Button("Sync Now");
        syncNowBtn.setGraphic(syncIcon);
        syncNowBtn.setDisable(syncStatus.getState() == SyncStatus.State.SYNCING);
        syncNowBtn.setOnAction(e -> {
            syncNowBtn.setDisable(true);
            syncNowBtn.setText("Syncing…");
            new Thread(() -> com.courttrack.sync.SyncCoordinator.getInstance().syncAll(true)).start();
            dialog.close();
        });

        HBox statusRow = new HBox(8, dot, stateLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setPadding(new Insets(4, 0, 2, 0));

        HBox syncMsgRow = new HBox(12, msgLabel);
        HBox.setHgrow(syncMsgRow, Priority.ALWAYS);
        syncMsgRow.setAlignment(Pos.CENTER_LEFT);
        syncMsgRow.setPadding(new Insets(0, 0, 8, 0));

        HBox syncBtnRow = new HBox(syncNowBtn);
        syncBtnRow.setAlignment(Pos.CENTER_LEFT);
        syncBtnRow.setPadding(new Insets(4, 0, 4, 0));

        syncBody.getChildren().addAll(statusRow, syncMsgRow, syncBtnRow);

        // ================================================================
        // Section 4: About & Updates
        // ================================================================
        VBox[] aboutCard = makeCard.apply("About", Feather.INFO);
        VBox aboutBody = aboutCard[1];

        String version = com.courttrack.util.AppVersion.getVersion();
        aboutBody.getChildren().addAll(
                infoRow("Application", "CourtTrack", mutedClr),
                infoRow("Version", "v" + version, mutedClr),
                infoRow("Platform", System.getProperty("os.name"), mutedClr)
        );

        FontIcon updateIcon = new FontIcon(Feather.DOWNLOAD_CLOUD);
        updateIcon.setIconSize(13); updateIcon.setIconColor(Color.web(tm.accentBlue()));
        Button checkUpdateBtn = new Button("Check for Updates");
        checkUpdateBtn.setGraphic(updateIcon);
        checkUpdateBtn.setOnAction(e -> {
            checkUpdateBtn.setDisable(true);
            checkUpdateBtn.setText("Checking…");
            new Thread(() -> {
                com.courttrack.update.UpdateChecker checker = new com.courttrack.update.UpdateChecker();
                java.util.Optional<com.courttrack.update.UpdateInfo> info = checker.checkForUpdate();
                javafx.application.Platform.runLater(() -> {
                    if (info.isPresent()) {
                        showUpdateNotification(info.get());
                        dialog.close();
                    } else {
                        checkUpdateBtn.setDisable(false);
                        checkUpdateBtn.setText("Up to date");
                    }
                });
            }).start();
        });

        HBox updateRow = new HBox(checkUpdateBtn);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        updateRow.setPadding(new Insets(10, 0, 4, 0));
        aboutBody.getChildren().add(updateRow);

        // ================================================================
        // Section 5: Keyboard Shortcuts
        // ================================================================
        VBox[] kbCard = makeCard.apply("Keyboard Shortcuts", Feather.COMMAND);
        VBox kbBody = kbCard[1];

        GridPane shortcutsGrid = new GridPane();
        shortcutsGrid.setHgap(20); shortcutsGrid.setVgap(8);
        ColumnConstraints kc1 = new ColumnConstraints(); kc1.setMinWidth(160);
        ColumnConstraints kc2 = new ColumnConstraints(); kc2.setHgrow(Priority.ALWAYS);
        shortcutsGrid.getColumnConstraints().addAll(kc1, kc2);
        addShortcutRow(shortcutsGrid, 0, "Ctrl + N",         "New Case");
        addShortcutRow(shortcutsGrid, 1, "Ctrl + Shift + N", "New Person");
        addShortcutRow(shortcutsGrid, 2, "Ctrl + Enter",     "Save form (in dialogs)");
        addShortcutRow(shortcutsGrid, 3, "Ctrl + 1",         "Go to Dashboard");
        addShortcutRow(shortcutsGrid, 4, "Ctrl + 2",         "Go to Cases");
        addShortcutRow(shortcutsGrid, 5, "Ctrl + 3",         "Go to Persons");
        addShortcutRow(shortcutsGrid, 6, "Escape",           "Back / Close detail");
        kbBody.getChildren().add(shortcutsGrid);

        // ================================================================
        // Assemble scroll content
        // ================================================================
        VBox scrollContent = new VBox(10,
                appearanceCard[0], sessionCard[0], syncCard[0], aboutCard[0], kbCard[0]);
        scrollContent.setPadding(new Insets(16));
        scrollContent.setStyle("-fx-background-color: " + bg + ";");

        ScrollPane scroll = new ScrollPane(scrollContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(520);
        scroll.setPrefHeight(560);
        scroll.setMaxWidth(Double.MAX_VALUE);
        scroll.setStyle("-fx-background-color: " + bg + "; -fx-background: " + bg + ";");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("-fx-background-color: " + bg + "; -fx-padding: 0;");
        dialog.getDialogPane().lookup(".button-bar").setStyle("-fx-background-color: " + bg + "; -fx-padding: 8 16 12 16;");

        javafx.stage.Stage stage = (javafx.stage.Stage) dialog.getDialogPane().getScene().getWindow();
        stage.setMinWidth(540);
        stage.setMinHeight(500);

        // Style the scrollbar to match the dark background once the skin is applied
        dialog.setOnShown(e -> {
            String trackBg   = tm.isDark() ? "#1a1a1a" : "#e8e8e8";
            String thumbClr  = tm.isDark() ? "#3a3a3a" : "#b0b0b0";
            String arrowBg   = tm.isDark() ? "#1a1a1a" : "#e8e8e8";
            scroll.lookupAll(".scroll-bar").forEach(n ->
                    n.setStyle("-fx-background-color: " + trackBg + ";"));
            scroll.lookupAll(".track").forEach(n ->
                    n.setStyle("-fx-background-color: " + trackBg + "; -fx-background-radius: 0;"));
            scroll.lookupAll(".thumb").forEach(n ->
                    n.setStyle("-fx-background-color: " + thumbClr + "; -fx-background-radius: 3;"));
            scroll.lookupAll(".increment-button, .decrement-button").forEach(n ->
                    n.setStyle("-fx-background-color: " + arrowBg + "; -fx-padding: 2;"));
            scroll.lookupAll(".increment-arrow, .decrement-arrow").forEach(n ->
                    n.setStyle("-fx-background-color: transparent; -fx-padding: 0;"));
        });

        dialog.showAndWait();
    }

    /** Compact key–value display row used in settings cards. */
    private HBox infoRow(String key, String value, String mutedClr) {
        Label keyLbl = new Label(key);
        keyLbl.setFont(Font.font("System", 12));
        keyLbl.setStyle("-fx-text-fill: " + mutedClr + ";");
        keyLbl.setMinWidth(110);

        Label valLbl = new Label(value != null ? value : "—");
        valLbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        HBox.setHgrow(valLbl, Priority.ALWAYS);

        HBox row = new HBox(12, keyLbl, valLbl);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 0, 5, 0));
        return row;
    }

    private void addShortcutRow(GridPane grid, int row, String keys, String action) {
        String keyBg = tm.isDark() ? "#2a2a2a" : "#f0f0f0";
        String keyBorder = tm.isDark() ? "#484848" : "#c8c8c8";
        Label keyLabel = new Label(keys);
        keyLabel.setFont(Font.font("System Mono", FontWeight.NORMAL, 11));
        keyLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; " +
                        "-fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 8 2 8;",
                keyBg, keyBorder));

        Label actionLabel = new Label(action);
        actionLabel.setFont(Font.font("System", 12));
        actionLabel.getStyleClass().add("text-muted");

        grid.add(keyLabel, 0, row);
        grid.add(actionLabel, 1, row);
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
        if (!sidebarCollapsed) {
            content.getChildren().add(label);
        }

        btn.setGraphic(content);
        btn.setText(null);
        btn.setStyle(navInactiveStyle());
        btn.setTooltip(new Tooltip(text));

        btn.setOnMouseEntered(e -> {
            if (btn != activeButton) {
                btn.setStyle(navHoverStyle());
            }
        });
        btn.setOnMouseExited(e -> {
            if (btn != activeButton) {
                btn.setStyle(navInactiveStyle());
            }
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
        if (showingDetail) {
            exitDetailAndNavigate(page);
            return;
        }

        if (cachedDashboard == null) {
            cachedDashboard = new DashboardView(
                    () -> navigateTo("cases"),
                    () -> navigateTo("offenders"),
                    this::showCaseDetail);
            cachedCases = new CaseListView(this::showCaseDetail);
            cachedOffenders = new OffenderListView(this::showPersonDetail);
            cachedReports = new ReportsView();

            cachedDashboard.getRoot().setVisible(false);
            cachedDashboard.getRoot().setManaged(false);
            cachedCases.getRoot().setVisible(false);
            cachedCases.getRoot().setManaged(false);
            cachedOffenders.getRoot().setVisible(false);
            cachedOffenders.getRoot().setManaged(false);
            cachedReports.getRoot().setVisible(false);
            cachedReports.getRoot().setManaged(false);

            contentArea.getChildren().addAll(
                    cachedDashboard.getRoot(),
                    cachedCases.getRoot(),
                    cachedOffenders.getRoot(),
                    cachedReports.getRoot());
        }

        cachedDashboard.getRoot().setVisible(false);
        cachedDashboard.getRoot().setManaged(false);
        cachedCases.getRoot().setVisible(false);
        cachedCases.getRoot().setManaged(false);
        cachedOffenders.getRoot().setVisible(false);
        cachedOffenders.getRoot().setManaged(false);
        if (cachedReports != null) {
            cachedReports.getRoot().setVisible(false);
            cachedReports.getRoot().setManaged(false);
        }

        switch (page) {
            case "dashboard" -> {
                cachedDashboard.getRoot().setVisible(true);
                cachedDashboard.getRoot().setManaged(true);
                cachedDashboard.refresh();
            }
            case "cases" -> {
                cachedCases.getRoot().setVisible(true);
                cachedCases.getRoot().setManaged(true);
                cachedCases.refresh();
            }
            case "offenders" -> {
                cachedOffenders.getRoot().setVisible(true);
                cachedOffenders.getRoot().setManaged(true);
                cachedOffenders.refresh();
            }
            case "reports" -> {
                cachedReports.getRoot().setVisible(true);
                cachedReports.getRoot().setManaged(true);
                cachedReports.refresh();
            }
        }

        currentPage = page;
        previousPage = page;
        updateNavHighlight(page);
    }

    private void exitDetailAndNavigate(String targetPage) {
        if (!contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().remove(contentArea.getChildren().size() - 1);
        }
        showingDetail = false;
        previousPage = targetPage;
        navigateTo(targetPage);
    }

    private void hideCurrentCachedView() {
        if (cachedDashboard != null) {
            cachedDashboard.getRoot().setVisible(false);
            cachedDashboard.getRoot().setManaged(false);
        }
        if (cachedCases != null) {
            cachedCases.getRoot().setVisible(false);
            cachedCases.getRoot().setManaged(false);
        }
        if (cachedOffenders != null) {
            cachedOffenders.getRoot().setVisible(false);
            cachedOffenders.getRoot().setManaged(false);
        }
        if (cachedReports != null) {
            cachedReports.getRoot().setVisible(false);
            cachedReports.getRoot().setManaged(false);
        }
    }

    private void updateNavHighlight(String page) {
        for (Button btn : navButtons) {
            btn.setStyle(navInactiveStyle());
        }
        int idx = switch (page) {
            case "dashboard" -> 0;
            case "cases" -> 1;
            case "offenders" -> 2;
            case "reports" -> 3;
            default -> 0;
        };
        if (idx < navButtons.size()) {
            activeButton = navButtons.get(idx);
            activeButton.setStyle(navActiveStyle());
        }
    }

    public void showCaseDetail(CourtCase courtCase) {
        hideCurrentCachedView();
        showingDetail = true;
        CaseDetailView detailView = new CaseDetailView(courtCase, this::onBack, this::showPersonDetail);
        contentArea.getChildren().add(detailView.getRoot());
    }

    public void registerKeyShortcuts(javafx.scene.Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.isShiftDown() && e.getCode() == KeyCode.N) {
                openNewPersonDialog();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.N) {
                openNewCaseDialog();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE && showingDetail) {
                onBack();
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT1) {
                navigateTo("dashboard");
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT2) {
                navigateTo("cases");
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT3) {
                navigateTo("offenders");
                e.consume();
            } else if (e.isControlDown() && e.getCode() == KeyCode.DIGIT4) {
                navigateTo("reports");
                e.consume();
            }
        });
    }

    private void openNewCaseDialog() {
        CaseRepository caseRepo = CaseRepository.getInstance();
        CaseFormDialog dialog = new CaseFormDialog(null);
        while (true) {
            Optional<CourtCase> result = dialog.showAndWait();
            if (result.isEmpty())
                break;
            CourtCase c = result.get();
            List<CaseFormDialog.ParticipantEntry> participants = dialog.getParticipantsToCreate();
            caseRepo.save(c, null, () -> {
                com.courttrack.model.Charge charge = new com.courttrack.model.Charge();
                charge.setCaseId(c.getCaseId());
                charge.setParticulars(c.getChargeParticulars());
                charge.setPlea(c.getChargePlea());
                charge.setVerdict(c.getChargeVerdict());
                caseRepo.saveCharge(charge, () -> saveParticipantsInBackground(c, participants));
            });
            if (!dialog.isAddAnother())
                break;
            dialog = new CaseFormDialog(null);
        }
        if (cachedDashboard != null)
            cachedDashboard.refresh();
        if (cachedCases != null)
            cachedCases.refresh();
    }

    private void saveParticipantsInBackground(CourtCase c, List<CaseFormDialog.ParticipantEntry> entries) {
        if (entries.isEmpty())
            return;
        for (CaseFormDialog.ParticipantEntry entry : entries) {
            Person p = new Person();
            p.setFirstName(entry.firstName());
            p.setLastName(entry.lastName());
            if (!entry.nationalId().isBlank())
                p.setNationalId(entry.nationalId());
            new PersonDao().insert(p);
            CaseParticipant cp = new CaseParticipant();
            cp.setCaseId(c.getCaseId());
            cp.setPersonId(p.getPersonId());
            cp.setRoleType(entry.roleType());
            new CaseDao().addParticipant(cp);
        }
    }

    private void openNewPersonDialog() {
        OffenderFormDialog dialog = new OffenderFormDialog(null);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            PersonDao personDao = new PersonDao();
            personDao.insert(link.getPerson());
            if (link.getCourtCase() != null) {
                CaseParticipant cp = new CaseParticipant();
                cp.setCaseId(link.getCourtCase().getCaseId());
                cp.setPersonId(link.getPerson().getPersonId());
                cp.setRoleType("Accused");
                new CaseDao().addParticipant(cp);
            }
            if (cachedDashboard != null)
                cachedDashboard.refresh();
            if (cachedOffenders != null)
                cachedOffenders.refresh();
        });
    }

    public void showPersonDetail(Person person) {
        hideCurrentCachedView();
        showingDetail = true;
        PersonDetailView detailView = new PersonDetailView(person, this::onBack);
        contentArea.getChildren().add(detailView.getRoot());
    }

    private void onBack() {
        if (!contentArea.getChildren().isEmpty()) {
            contentArea.getChildren().remove(contentArea.getChildren().size() - 1);
        }
        showingDetail = false;
        navigateTo(previousPage);
    }

    public Parent getRoot() {
        return root;
    }
}
