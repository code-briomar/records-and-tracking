package com.courttrack.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import com.courttrack.repository.CaseRepository;
import javafx.stage.Stage;
import com.courttrack.util.DialogUtil;

public class ReportsView {
    private final VBox root;
    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();

    private final StackPane tabContentArea;
    private final Button delayReportTabBtn;
    private final Button officerActivityTabBtn;

    // Delay Report Elements
    private Label delay30Value;
    private Label delay60Value;
    private Label delay90Value;
    private VBox courtAgeList;
    private VBox stageAgeList;

    // Officer Activity Elements
    private VBox leaderboardList;
    private TableView<Map<String, Object>> auditTable;
    private ObservableList<Map<String, Object>> auditList;
    private ToggleGroup rangeGroup;
    private RadioButton weeklyRadio;
    private RadioButton monthlyRadio;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ReportsView() {
        root = new VBox(0);
        root.setPadding(new Insets(24, 40, 24, 40));
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        tabContentArea = new StackPane();
        VBox.setVgrow(tabContentArea, Priority.ALWAYS);

        delayReportTabBtn = new Button("Delay Reports");
        officerActivityTabBtn = new Button("Officer Activity Log");

        buildUI();
        showDelayReportTab();
    }

    private void buildUI() {
        // --- Header Section ---
        Label pageTitle = new Label("Reports & Analytics");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 40));
        Label pageSubtitle = new Label("System accountability, delay metrics, and audit tracking.");
        pageSubtitle.getStyleClass().add("text-muted");
        pageSubtitle.setFont(Font.font("System", 14));
        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);
        VBox.setMargin(titleBox, new Insets(0, 0, 20, 0));

        // --- Tab Bar ---
        HBox tabBar = new HBox(12);
        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.setPadding(new Insets(0, 0, 16, 0));

        delayReportTabBtn.setOnAction(e -> showDelayReportTab());
        officerActivityTabBtn.setOnAction(e -> showOfficerActivityTab());

        tabBar.getChildren().addAll(delayReportTabBtn, officerActivityTabBtn);

        // Add to root
        root.getChildren().addAll(titleBox, tabBar, tabContentArea);
    }

    private void updateTabButtonStyles(boolean isDelayActive) {
        boolean dark = tm.isDark();
        String activeStyle = String.format(
            "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;",
            tm.accentBlue()
        );
        String inactiveStyle = String.format(
            "-fx-background-color: transparent; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-radius: 6; -fx-padding: 8 16; -fx-background-radius: 6; -fx-cursor: hand;",
            tm.sidebarText(), tm.sidebarSep()
        );

        delayReportTabBtn.setStyle(isDelayActive ? activeStyle : inactiveStyle);
        officerActivityTabBtn.setStyle(!isDelayActive ? activeStyle : inactiveStyle);
    }

    public void refresh() {
        if (delayReportTabBtn.getStyle().contains(tm.accentBlue())) {
            loadDelayReportData();
        } else {
            loadOfficerActivityData();
        }
    }

    // ================================================================
    // DELAY REPORT TAB
    // ================================================================

    private void showDelayReportTab() {
        updateTabButtonStyles(true);
        tabContentArea.getChildren().clear();

        // 1. Stat cards row
        delay30Value = new Label("—");
        delay30Value.setFont(Font.font("System", FontWeight.BOLD, 30));
        delay60Value = new Label("—");
        delay60Value.setFont(Font.font("System", FontWeight.BOLD, 30));
        delay90Value = new Label("—");
        delay90Value.setFont(Font.font("System", FontWeight.BOLD, 30));

        HBox statsRow = new HBox(14,
            makeStatCard("DELAYED 30-60 DAYS", delay30Value, Feather.ALERT_CIRCLE, tm.accentBlue(), true),
            makeStatCard("DELAYED 60-90 DAYS", delay60Value, Feather.ALERT_TRIANGLE, tm.accentOrange(), true),
            makeStatCard("DELAYED 90+ DAYS (BACKLOG)", delay90Value, Feather.OCTAGON, tm.accentRed(), true)
        );
        VBox.setMargin(statsRow, new Insets(0, 0, 20, 0));

        // 2. Bottom two-column metrics
        courtAgeList = new VBox(10);
        courtAgeList.setPadding(new Insets(10, 0, 10, 0));
        VBox courtCard = buildCard("Average Case Age by Court (Pending)", courtAgeList);

        stageAgeList = new VBox(10);
        stageAgeList.setPadding(new Insets(10, 0, 10, 0));
        VBox stageCard = buildCard("Stage-wise Duration (Average Days stuck)", stageAgeList);

        HBox.setHgrow(courtCard, Priority.ALWAYS);
        HBox.setHgrow(stageCard, Priority.ALWAYS);
        courtCard.setMaxWidth(Double.MAX_VALUE);
        stageCard.setMaxWidth(Double.MAX_VALUE);

        HBox chartsRow = new HBox(16, courtCard, stageCard);
        VBox.setVgrow(chartsRow, Priority.ALWAYS);

        VBox delayLayout = new VBox(0, statsRow, chartsRow);
        VBox.setVgrow(delayLayout, Priority.ALWAYS);
        tabContentArea.getChildren().add(delayLayout);

        loadDelayReportData();
    }

    private void loadDelayReportData() {
        caseRepo.getDelayCounts(counts -> Platform.runLater(() -> {
            delay30Value.setText(String.valueOf(counts.getOrDefault("30-60", 0)));
            delay60Value.setText(String.valueOf(counts.getOrDefault("60-90", 0)));
            delay90Value.setText(String.valueOf(counts.getOrDefault("90+", 0)));
        }));

        caseRepo.getAverageAgeByCourt(data -> Platform.runLater(() -> populateBarMetrics(data, courtAgeList, "court", "avg_age", " days", tm.accentBlue())));
        caseRepo.getStageWiseAging(data -> Platform.runLater(() -> populateBarMetrics(data, stageAgeList, "stage", "avg_days", " days", tm.accentOrange())));
    }

    private void populateBarMetrics(List<Map<String, Object>> data, VBox container, String keyName, String valName, String unit, String colorHex) {
        container.getChildren().clear();
        if (data.isEmpty()) {
            Label empty = new Label("No data available.");
            empty.setFont(Font.font("System", 13));
            empty.getStyleClass().add("text-muted");
            empty.setPadding(new Insets(20));
            container.getChildren().add(empty);
            return;
        }

        // Find max to scale progress bars
        double max = 0.001;
        for (Map<String, Object> map : data) {
            Number val = (Number) map.get(valName);
            if (val != null && val.doubleValue() > max) {
                max = val.doubleValue();
            }
        }

        boolean dark = tm.isDark();
        String trackBg = dark ? "#ffffff0f" : "#0000000f";
        String mutedClr = dark ? "#8a8a8a" : "#5a5a5a";
        String borderClr = dark ? "#ffffff12" : "#00000012";

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> map = data.get(i);
            String name = String.valueOf(map.get(keyName));
            Number val = (Number) map.get(valName);
            double value = val != null ? val.doubleValue() : 0.0;
            double pct = max > 0 ? value / max : 0.0;

            // Name
            Label nameLabel = new Label(name);
            nameLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
            nameLabel.setMinWidth(130);

            // Progress Fill & Track
            Region fill = new Region();
            fill.setPrefHeight(6);
            fill.setMinWidth(0);
            fill.setStyle("-fx-background-color: " + colorHex + "; -fx-background-radius: 3 0 0 3;");

            Region track = new Region();
            track.setPrefHeight(6);
            track.setStyle("-fx-background-color: " + trackBg + "; -fx-background-radius: 0 3 3 0;");
            HBox.setHgrow(track, Priority.ALWAYS);

            HBox barContainer = new HBox(fill, track);
            barContainer.setMaxWidth(Double.MAX_VALUE);
            barContainer.setPrefHeight(6);
            barContainer.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(barContainer, Priority.ALWAYS);

            // Listen to resize to scale correctly
            barContainer.widthProperty().addListener((obs, ov, nv) -> {
                fill.setPrefWidth(nv.doubleValue() * pct);
                track.setPrefWidth(nv.doubleValue() * (1 - pct));
            });
            double w = barContainer.getWidth();
            if (w > 0) {
                fill.setPrefWidth(w * pct);
                track.setPrefWidth(w * (1 - pct));
            }

            // Value label
            Label valLabel = new Label(String.format("%.1f%s", value, unit));
            valLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
            valLabel.setMinWidth(65);
            valLabel.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(12, nameLabel, barContainer, valLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 14, 8, 14));
            if (i < data.size() - 1) {
                row.setStyle(String.format(
                    "-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
                    borderClr
                ));
            }

            container.getChildren().add(row);
        }
    }

    // ================================================================
    // OFFICER ACTIVITY TAB
    // ================================================================

    private void showOfficerActivityTab() {
        updateTabButtonStyles(false);
        tabContentArea.getChildren().clear();

        // 1. Leaderboard panel (top)
        leaderboardList = new VBox(8);
        leaderboardList.setPadding(new Insets(10, 0, 10, 0));

        // Weekly / Monthly Toggle Buttons
        rangeGroup = new ToggleGroup();
        weeklyRadio = new RadioButton("Weekly (Last 7 Days)");
        weeklyRadio.setToggleGroup(rangeGroup);
        weeklyRadio.setSelected(true);
        weeklyRadio.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");

        monthlyRadio = new RadioButton("Monthly (Last 30 Days)");
        monthlyRadio.setToggleGroup(rangeGroup);
        monthlyRadio.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");

        rangeGroup.selectedToggleProperty().addListener((obs, ov, nv) -> loadOfficerLeaderboard());

        HBox toggleBox = new HBox(16, weeklyRadio, monthlyRadio);
        toggleBox.setAlignment(Pos.CENTER_LEFT);
        toggleBox.setPadding(new Insets(0, 0, 10, 0));

        VBox leaderboardCard = buildCard("Top Active Officers", new VBox(8, toggleBox, leaderboardList));
        VBox.setMargin(leaderboardCard, new Insets(0, 0, 20, 0));

        // 2. Audit Table (bottom)
        auditList = FXCollections.observableArrayList();
        auditTable = createAuditTable();
        auditTable.setItems(auditList);
        auditTable.setStyle("-fx-border-color: transparent;");
        VBox.setVgrow(auditTable, Priority.ALWAYS);

        VBox auditCard = buildCard("Recent Case Interactions (Audit Trail)", auditTable);
        VBox.setVgrow(auditCard, Priority.ALWAYS);

        VBox officerLayout = new VBox(0, leaderboardCard, auditCard);
        VBox.setVgrow(officerLayout, Priority.ALWAYS);
        tabContentArea.getChildren().add(officerLayout);

        loadOfficerActivityData();
    }

    private void loadOfficerActivityData() {
        loadOfficerLeaderboard();

        caseRepo.getRecentCaseActivity(60, data -> Platform.runLater(() -> {
            auditList.setAll(data);
            if (data.isEmpty()) {
                auditTable.setPlaceholder(new Label("No recent case interactions logged."));
            }
        }));
    }

    private void loadOfficerLeaderboard() {
        boolean weekly = weeklyRadio.isSelected();
        caseRepo.getOfficerActivityStats(weekly, data -> Platform.runLater(() -> populateLeaderboard(data)));
    }

    private void populateLeaderboard(List<Map<String, Object>> data) {
        leaderboardList.getChildren().clear();
        if (data.isEmpty()) {
            Label empty = new Label("No officer actions registered in this period.");
            empty.setFont(Font.font("System", 13));
            empty.getStyleClass().add("text-muted");
            empty.setPadding(new Insets(14, 0, 14, 0));
            leaderboardList.getChildren().add(empty);
            return;
        }

        HBox boardBox = new HBox(14);
        boardBox.setAlignment(Pos.CENTER_LEFT);

        boolean dark = tm.isDark();
        String cardBg = dark ? "#ffffff0c" : "#00000006";
        String cardBdr = dark ? "#ffffff18" : "#00000018";

        // Display up to top 4 officers in beautiful badges/cards side-by-side
        int displayCount = Math.min(4, data.size());
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> map = data.get(i);
            String officer = String.valueOf(map.get("officer"));
            int count = ((Number) map.get("action_count")).intValue();

            // Avatar representation
            StackPane avatar = new StackPane();
            avatar.setMinSize(32, 32);
            avatar.setMaxSize(32, 32);
            avatar.setStyle(String.format(
                "-fx-background-color: %s; -fx-background-radius: 16;",
                i == 0 ? tm.accentBlue() : i == 1 ? tm.accentGreen() : i == 2 ? tm.accentOrange() : tm.accentPurple()
            ));
            Label initials = new Label(officer.substring(0, Math.min(2, officer.length())).toUpperCase());
            initials.setFont(Font.font("System", FontWeight.BOLD, 12));
            initials.setTextFill(Color.WHITE);
            avatar.getChildren().add(initials);

            Label rankLbl = new Label("#" + (i + 1));
            rankLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
            rankLbl.getStyleClass().add("text-muted");

            Label nameLbl = new Label(officer);
            nameLbl.setFont(Font.font("System", FontWeight.BOLD, 13));

            Label countLbl = new Label(count + " actions");
            countLbl.setFont(Font.font("System", 11));
            countLbl.setStyle("-fx-text-fill: " + tm.accentBlue() + "; -fx-font-weight: bold;");

            VBox info = new VBox(2, nameLbl, countLbl);
            HBox cardRow = new HBox(10, avatar, info);
            cardRow.setAlignment(Pos.CENTER_LEFT);

            VBox card = new VBox(6, rankLbl, cardRow);
            card.setPadding(new Insets(10, 16, 10, 16));
            card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s; -fx-border-radius: 6; -fx-background-radius: 6;",
                cardBg, cardBdr
            ));
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);

            boardBox.getChildren().add(card);
        }

        leaderboardList.getChildren().add(boardBox);
    }

    private TableView<Map<String, Object>> createAuditTable() {
        TableView<Map<String, Object>> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // TIMESTAMP
        TableColumn<Map<String, Object>, String> tsCol = new TableColumn<>("TIMESTAMP");
        tsCol.setPrefWidth(130);
        tsCol.setCellValueFactory(cd -> {
            LocalDateTime ts = (LocalDateTime) cd.getValue().get("timestamp");
            return new javafx.beans.property.SimpleStringProperty(ts != null ? ts.format(TS_FMT) : "");
        });

        // OFFICER
        TableColumn<Map<String, Object>, String> userCol = new TableColumn<>("OFFICER");
        userCol.setPrefWidth(120);
        userCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("username", "System"))
        ));

        // CASE NUMBER
        TableColumn<Map<String, Object>, Map<String, Object>> caseCol = new TableColumn<>("CASE");
        caseCol.setPrefWidth(160);
        caseCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        caseCol.setCellFactory(col -> new TableCell<>() {
            private final Label numLbl = new Label();
            private final Label titleLbl = new Label();
            private final VBox box = new VBox(1, numLbl, titleLbl);
            {
                numLbl.setFont(Font.font("System", FontWeight.BOLD, 12));
                titleLbl.setFont(Font.font("System", 10));
                titleLbl.getStyleClass().add("text-muted");
            }
            @Override protected void updateItem(Map<String, Object> map, boolean empty) {
                super.updateItem(map, empty);
                if (empty || map == null) { setGraphic(null); } else {
                    String num = String.valueOf(map.getOrDefault("case_number", ""));
                    String title = String.valueOf(map.getOrDefault("case_title", ""));
                    numLbl.setText(num.isBlank() ? "Unknown Case" : num);
                    titleLbl.setText(title);
                    setGraphic(box);
                }
            }
        });

        // ACTION
        TableColumn<Map<String, Object>, String> actCol = new TableColumn<>("ACTION");
        actCol.setPrefWidth(90);
        actCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("action", ""))
        ));
        actCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.setPadding(new Insets(2, 8, 2, 8)); badge.setFont(Font.font("System", FontWeight.BOLD, 10)); }
            @Override protected void updateItem(String act, boolean empty) {
                super.updateItem(act, empty);
                setText(null);
                if (empty || act == null) { setGraphic(null); return; }
                badge.setText(act);
                String bg, txt;
                switch (act.toUpperCase()) {
                    case "CREATE":
                        bg = tm.badgeOpenBg(); txt = tm.badgeOpenText();
                        break;
                    case "DELETE":
                        bg = tm.badgeCriminalBg(); txt = tm.badgeCriminalText();
                        break;
                    case "UPDATE":
                    default:
                        bg = tm.badgeCivilBg(); txt = tm.badgeCivilText();
                        break;
                }
                badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", bg, txt));
                setGraphic(badge);
            }
        });

        // DETAILS
        TableColumn<Map<String, Object>, String> detCol = new TableColumn<>("DETAILS / NOTES");
        detCol.setPrefWidth(280);
        detCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
            String.valueOf(cd.getValue().getOrDefault("details", ""))
        ));
        detCol.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            { lbl.setFont(Font.font("System", 11)); lbl.setWrapText(true); }
            @Override protected void updateItem(String details, boolean empty) {
                super.updateItem(details, empty);
                if (empty || details == null) { setGraphic(null); } else {
                    lbl.setText(details);
                    setGraphic(lbl);
                }
            }
        });

        tv.getColumns().addAll(tsCol, userCol, caseCol, actCol, detCol);
        tv.setPlaceholder(new Label("Loading..."));

        String altBg = tm.isDark() ? "#ffffff05" : "#00000005";
        tv.setRowFactory(tv2 -> {
            TableRow<Map<String, Object>> row = new TableRow<>() {
                @Override protected void updateItem(Map<String, Object> item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        setStyle((getIndex() % 2 != 0 ? "-fx-background-color: " + altBg + ";" : "") + " -fx-cursor: hand;");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 1) {
                    showLogDetailDialog(row.getItem());
                }
            });
            return row;
        });
        return tv;
    }

    private void showLogDetailDialog(Map<String, Object> item) {
        if (item == null) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Activity Log Details");
        dialog.setHeaderText(null);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        DialogUtil.applyIcon(dialog);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        String bg = tm.isDark() ? "#161616" : "#f4f4f4";
        dialogPane.setStyle("-fx-background-color: " + bg + ";");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getColumnConstraints().addAll(
            new ColumnConstraints(100),
            new ColumnConstraints(340)
        );

        LocalDateTime ts = (LocalDateTime) item.get("timestamp");
        String tsStr = ts != null ? ts.format(TS_FMT) : "N/A";
        String username = getSafeString(item.get("username"));
        if (username.isEmpty()) username = "System";
        String action = getSafeString(item.get("action"));
        String status = getSafeString(item.get("status"));
        String caseNum = getSafeString(item.get("case_number"));
        String caseTitle = getSafeString(item.get("case_title"));
        String caseStr = caseNum.isEmpty() ? "N/A" : caseNum + (caseTitle.isEmpty() ? "" : " - " + caseTitle);
        String details = getSafeString(item.get("details"));

        int r = 0;
        grid.add(createLabel("Timestamp:", true), 0, r);
        grid.add(createLabel(tsStr, false), 1, r++);

        grid.add(createLabel("Officer:", true), 0, r);
        grid.add(createLabel(username, false), 1, r++);

        grid.add(createLabel("Action:", true), 0, r);
        Label actionLabel = new Label(action);
        actionLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        String actionColor;
        switch (action.toUpperCase()) {
            case "CREATE":
                actionColor = tm.badgeOpenText();
                break;
            case "DELETE":
                actionColor = tm.badgeCriminalText();
                break;
            default:
                actionColor = tm.badgeCivilText();
                break;
        }
        actionLabel.setStyle("-fx-text-fill: " + actionColor + ";");
        grid.add(actionLabel, 1, r++);

        if (!status.isEmpty()) {
            grid.add(createLabel("Status:", true), 0, r);
            grid.add(createLabel(status, false), 1, r++);
        }

        grid.add(createLabel("Case:", true), 0, r);
        Label caseLabel = new Label(caseStr);
        caseLabel.setFont(Font.font("System", 12));
        caseLabel.setWrapText(true);
        grid.add(caseLabel, 1, r++);

        VBox detailsBox = new VBox(6);
        Label detailsTitle = createLabel("Details / Notes:", true);
        TextArea detailsArea = new TextArea(details);
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(6);
        
        String areaBg = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String areaBorder = tm.isDark() ? "#383838" : "#dedede";
        detailsArea.setStyle(String.format(
            "-fx-control-inner-background: %s; -fx-background-color: %s; -fx-border-color: %s; -fx-border-radius: 4; -fx-background-radius: 4;",
            areaBg, areaBg, areaBorder
        ));
        detailsBox.getChildren().addAll(detailsTitle, detailsArea);

        VBox content = new VBox(14, grid, detailsBox);
        content.setPadding(new Insets(18));
        dialogPane.setContent(content);

        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.setMinWidth(480);
        stage.setMinHeight(380);

        dialog.setOnShown(ev -> {
            Node buttonBar = dialogPane.lookup(".button-bar");
            if (buttonBar != null) {
                String btnBg  = tm.isDark() ? "#1c1c1c" : "#f0f0f0";
                String btnBrd = tm.isDark() ? "#333333" : "#d0d0d0";
                buttonBar.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s transparent transparent transparent;" +
                        "-fx-border-width: 1 0 0 0; -fx-padding: 10 16;", btnBg, btnBrd));
            }
            String thumbClr = tm.isDark() ? "#505050" : "#b0b0b0";
            String trackClr = tm.isDark() ? "#222222" : "#e0e0e0";
            detailsArea.lookupAll(".scroll-bar").forEach(sb -> {
                sb.setStyle("-fx-background-color: transparent;");
                sb.lookupAll(".track").forEach(t ->
                        t.setStyle("-fx-background-color: " + trackClr + "; -fx-background-radius: 0;"));
                sb.lookupAll(".thumb").forEach(t ->
                        t.setStyle("-fx-background-color: " + thumbClr + "; -fx-background-radius: 4;"));
                sb.lookupAll(".increment-button, .decrement-button").forEach(b ->
                        b.setStyle("-fx-background-color: transparent; -fx-padding: 0;"));
            });
        });

        dialog.showAndWait();
    }

    private Label createLabel(String text, boolean bold) {
        Label l = new Label(text);
        if (bold) {
            l.setFont(Font.font("System", FontWeight.BOLD, 12));
            l.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + ";");
        } else {
            l.setFont(Font.font("System", 12));
        }
        return l;
    }

    private String getSafeString(Object val) {
        return val == null ? "" : String.valueOf(val).trim();
    }

    // ================================================================
    // COMMON HELPERS
    // ================================================================

    private VBox makeStatCard(String labelText, Label valueLabel, Feather icon, String color, boolean grow) {
        boolean dark = tm.isDark();
        String cardBg = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";

        Region topBar = new Region();
        topBar.setPrefHeight(3);
        topBar.setMinHeight(3);
        topBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8 8 0 0;");

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(36, 36);
        iconCircle.setMaxSize(36, 36);
        iconCircle.setStyle(String.format("-fx-background-color: %s1e; -fx-background-radius: 18;", color));
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(16);
        fi.setIconColor(Color.web(color));
        iconCircle.getChildren().add(fi);

        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.getStyleClass().add("text-muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(spacer, iconCircle);
        topRow.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(8, topRow, valueLabel, lbl);
        body.setPadding(new Insets(16, 20, 16, 20));

        VBox card = new VBox(0, topBar, body);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s;" +
            "-fx-border-radius: 8; -fx-background-radius: 8;",
            cardBg, cardBorder
        ));
        if (grow) HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildCard(String title, Parent bodyContent) {
        boolean dark = tm.isDark();
        String cardBg = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        HBox header = new HBox(titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(13, 16, 11, 16));
        header.setStyle(String.format(
            "-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
            cardBorder
        ));

        VBox body = new VBox(bodyContent);
        body.setPadding(new Insets(12, 16, 12, 16));
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox card = new VBox(0, header, body);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s;" +
            "-fx-border-radius: 8; -fx-background-radius: 8;",
            cardBg, cardBorder
        ));
        return card;
    }

    public Parent getRoot() {
        return root;
    }
}
