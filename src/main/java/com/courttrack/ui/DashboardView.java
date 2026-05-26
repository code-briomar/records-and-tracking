package com.courttrack.ui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class DashboardView {
    private final VBox root;
    private final CaseDao caseDao = new CaseDao();
    private final PersonDao personDao = new PersonDao();
    private final ThemeManager tm = ThemeManager.getInstance();

    private final Runnable onNavigateCases;
    private final Runnable onNavigatePersons;
    private final Consumer<CourtCase> onViewCase;

    // Stat card values
    private Label filedTodayValue;
    private Label pendingValue;
    private Label thisMonthValue;
    private Label onAppealValue;

    // Case pipeline breakdown
    private static final String[] PIPELINE = { "Registered", "Mention", "Hearing", "Ruling", "Appeal", "Closed" };
    private final Region[] stFills = new Region[PIPELINE.length];
    private final Region[] stTracks = new Region[PIPELINE.length];
    private final HBox[] stBarBoxes = new HBox[PIPELINE.length];
    private final Label[] stCounts = new Label[PIPELINE.length];
    private final Label[] stPcts = new Label[PIPELINE.length];
    private final HBox[] stRows = new HBox[PIPELINE.length];

    // Recent filings list — repopulated on every refresh
    private VBox recentList;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d");

    public DashboardView(Runnable onNavigateCases, Runnable onNavigatePersons, Consumer<CourtCase> onViewCase) {
        this.onNavigateCases = onNavigateCases;
        this.onNavigatePersons = onNavigatePersons;
        this.onViewCase = onViewCase;
        root = new VBox(0);
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        buildUI();
        loadDataAsync();
    }

    public void refresh() {
        loadDataAsync();
    }

    // ================================================================
    // Data loading
    // ================================================================

    private void loadDataAsync() {
        new Thread(() -> {
            int today = caseDao.countFiledToday();
            int pending = caseDao.countByStatus("Hearing") + caseDao.countByStatus("Mention");
            int month = caseDao.countFiledThisMonth();
            int appeal = caseDao.countOnAppeal();
            List<CourtCase> recent = caseDao.findRecent(6);

            int[] pipeline = new int[PIPELINE.length];
            for (int i = 0; i < PIPELINE.length; i++)
                pipeline[i] = caseDao.countByStatus(PIPELINE[i]);
            int pipelineTotal = 0;
            for (int c : pipeline)
                pipelineTotal += c;
            final int pt = pipelineTotal;

            Platform.runLater(() -> {
                filedTodayValue.setText(String.valueOf(today));
                pendingValue.setText(String.valueOf(pending));
                thisMonthValue.setText(String.valueOf(month));
                onAppealValue.setText(String.valueOf(appeal));
                updateBars(pipeline, stFills, stTracks, stBarBoxes, stCounts, stPcts, stRows, pt);
                populateRecentActivity(recent);
            });
        }).start();
    }

    private void updateBars(int[] counts, Region[] fills, Region[] tracks, HBox[] barBoxes,
            Label[] countLabels, Label[] pctLabels, HBox[] rows, int total) {
        for (int i = 0; i < counts.length; i++) {
            int c = counts[i];
            double pct = total > 0 ? (double) c / total : 0;
            countLabels[i].setText(String.valueOf(c));
            pctLabels[i].setText(total > 0 ? String.format("%.0f%%", pct * 100) : "0%");
            final int idx = i;
            final double p = pct;
            barBoxes[i].widthProperty().addListener((obs, ov, nv) -> {
                fills[idx].setPrefWidth(nv.doubleValue() * p);
                tracks[idx].setPrefWidth(nv.doubleValue() * (1 - p));
            });
            double w = barBoxes[i].getWidth();
            if (w > 0) {
                fills[i].setPrefWidth(w * pct);
                tracks[i].setPrefWidth(w * (1 - pct));
            }
            rows[i].setOpacity(c == 0 ? 0.35 : 1.0);
        }
    }

    private void populateRecentActivity(List<CourtCase> cases) {
        boolean dark = tm.isDark();
        String borderClr = dark ? "#ffffff18" : "#00000018";
        String mutedClr = dark ? "#8a8a8a" : "#5a5a5a";

        recentList.getChildren().clear();

        if (cases.isEmpty()) {
            Label empty = new Label("No cases filed yet.");
            empty.setFont(Font.font("System", 13));
            empty.setStyle("-fx-text-fill: " + mutedClr + ";");
            empty.setPadding(new Insets(24, 16, 24, 16));
            recentList.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < cases.size(); i++) {
            String border = i < cases.size() - 1 ? borderClr : null;
            recentList.getChildren().add(buildRecentRow(cases.get(i), mutedClr, border));
        }
    }

    private HBox buildRecentRow(CourtCase c, String mutedClr, String borderClr) {
        boolean dark = tm.isDark();
        String hoverBg = dark ? "#ffffff09" : "#00000009";

        String num = (c.getCaseNumber() != null && !c.getCaseNumber().isBlank())
                ? c.getCaseNumber()
                : c.getCaseId().substring(0, 8).toUpperCase();
        Label numLabel = new Label(num);
        numLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        numLabel.setMinWidth(110);

        String titleStr = c.getCaseTitle() != null ? c.getCaseTitle() : "Untitled";
        if (titleStr.length() > 34)
            titleStr = titleStr.substring(0, 32) + "…";
        Label titleLabel = new Label(titleStr);
        titleLabel.setFont(Font.font("System", 12));
        titleLabel.setStyle("-fx-text-fill: " + mutedClr + ";");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        String catColor = categoryColor(c.getCaseCategory());
        String catText = c.getCaseCategory() != null ? c.getCaseCategory() : "—";
        Label catBadge = new Label(catText);
        catBadge.setFont(Font.font("System", FontWeight.BOLD, 10));
        catBadge.setStyle(String.format(
                "-fx-background-color: %s22; -fx-text-fill: %s;" +
                        "-fx-background-radius: 4; -fx-padding: 2 7 2 7;",
                catColor, catColor));

        String dateStr = c.getFilingDate() != null ? c.getFilingDate().format(DATE_FMT) : "—";
        Label dateLabel = new Label(dateStr);
        dateLabel.setFont(Font.font("System", 11));
        dateLabel.setStyle("-fx-text-fill: " + mutedClr + ";");
        dateLabel.setMinWidth(44);
        dateLabel.setAlignment(Pos.CENTER_RIGHT);

        String normalStyle = borderClr != null
                ? String.format("-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
                        borderClr)
                : "";
        String hoverStyle = normalStyle + " -fx-background-color: " + hoverBg + ";";

        HBox row = new HBox(10, numLabel, titleLabel, catBadge, dateLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setStyle(normalStyle);
        row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
        row.setOnMouseExited(e -> row.setStyle(normalStyle));
        row.setOnMouseClicked(e -> {
            if (onViewCase != null)
                onViewCase.accept(c);
        });
        return row;
    }

    // ================================================================
    // UI build
    // ================================================================

    private void buildUI() {
        // ── Header ──────────────────────────────────────────────────
        Label pageTitle = new Label("Dashboard");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 40));
        Label pageSubtitle = new Label("Overview of court records and system activity.");
        pageSubtitle.getStyleClass().add("text-muted");
        pageSubtitle.setFont(Font.font("System", 14));
        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);
        VBox.setMargin(titleBox, new Insets(0, 0, 24, 0));

        // ── Stat cards ──────────────────────────────────────────────
        filedTodayValue = statValue();
        pendingValue = statValue();
        thisMonthValue = statValue();
        onAppealValue = statValue();

        HBox statsRow = new HBox(14,
                makeStatCard("FILED TODAY", filedTodayValue, Feather.FILE_PLUS, tm.accentBlue(), true),
                makeStatCard("PENDING", pendingValue, Feather.CLOCK, tm.accentOrange(), true),
                makeStatCard("FILED THIS MONTH", thisMonthValue, Feather.CALENDAR, tm.accentGreen(), true),
                makeStatCard("ON APPEAL", onAppealValue, Feather.ALERT_TRIANGLE, tm.accentPurple(), true));
        VBox.setMargin(statsRow, new Insets(0, 0, 20, 0));

        // ── Action buttons ──────────────────────────────────────────
        HBox actionsRow = new HBox(12,
                makeActionBtn("New Case Filing", Feather.PLUS_CIRCLE, tm.accentBlue(), this::handleNewCase),
                makeActionBtn("Register Person", Feather.USER_PLUS, tm.accentGreen(), this::handleNewPerson));
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(actionsRow, new Insets(0, 0, 20, 0));

        // ── Two-column: Recent Filings + Case Pipeline ──────────────
        VBox recentCard = buildRecentFilingsCard();
        VBox pipelineCard = buildBreakdownCard("Case Pipeline", buildPipelineRows());
        HBox.setHgrow(recentCard, Priority.ALWAYS);
        HBox.setHgrow(pipelineCard, Priority.ALWAYS);
        recentCard.setMaxWidth(Double.MAX_VALUE);
        pipelineCard.setMaxWidth(Double.MAX_VALUE);

        HBox bottomRow = new HBox(14, recentCard, pipelineCard);
        bottomRow.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, statsRow, actionsRow, bottomRow);
    }

    // ================================================================
    // Recent filings card
    // ================================================================

    private VBox buildRecentFilingsCard() {
        boolean dark = tm.isDark();
        String cardBg = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String mutedClr = dark ? "#8a8a8a" : "#5a5a5a";

        Label titleLabel = new Label("Recent Filings");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        Hyperlink viewAll = new Hyperlink("View all →");
        viewAll.setFont(Font.font("System", 12));
        viewAll.setStyle("-fx-text-fill: " + mutedClr + ";");
        viewAll.setBorder(Border.EMPTY);
        viewAll.setPadding(new Insets(0));
        viewAll.setOnAction(e -> {
            if (onNavigateCases != null)
                onNavigateCases.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(titleLabel, spacer, viewAll);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(13, 16, 11, 16));
        header.setStyle(String.format(
                "-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
                cardBorder));

        recentList = new VBox(0);

        VBox card = new VBox(0, header, recentList);
        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8;",
                cardBg, cardBorder));
        return card;
    }

    // ================================================================
    // Pipeline breakdown card
    // ================================================================

    private VBox buildBreakdownCard(String title, VBox rows) {
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
                cardBorder));

        VBox card = new VBox(0, header, rows);
        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s;" +
                        "-fx-border-radius: 8; -fx-background-radius: 8;",
                cardBg, cardBorder));
        return card;
    }

    private VBox buildPipelineRows() {
        boolean dark = tm.isDark();
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String mutedClr = dark ? "#8a8a8a" : "#5a5a5a";
        String trackBg = dark ? "#ffffff0f" : "#0000000f";

        VBox rows = new VBox(0);
        for (int i = 0; i < PIPELINE.length; i++) {
            stRows[i] = buildBreakdownRow(
                    PIPELINE[i], pipelineColor(PIPELINE[i]), trackBg, mutedClr,
                    stFills, stTracks, stBarBoxes, stCounts, stPcts, i,
                    i < PIPELINE.length - 1 ? cardBorder : null);
            rows.getChildren().add(stRows[i]);
        }
        return rows;
    }

    private HBox buildBreakdownRow(String name, String color, String trackBg, String mutedClr,
            Region[] fills, Region[] tracks, HBox[] barBoxes,
            Label[] countLabels, Label[] pctLabels,
            int i, String borderClr) {
        Region dot = new Region();
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        nameLabel.setMinWidth(80);

        fills[i] = new Region();
        fills[i].setPrefHeight(5);
        fills[i].setMinWidth(0);
        fills[i].setStyle("-fx-background-color: " + color + "; -fx-background-radius: 3 0 0 3;");

        tracks[i] = new Region();
        tracks[i].setPrefHeight(5);
        tracks[i].setStyle("-fx-background-color: " + trackBg + "; -fx-background-radius: 0 3 3 0;");
        HBox.setHgrow(tracks[i], Priority.ALWAYS);

        barBoxes[i] = new HBox(fills[i], tracks[i]);
        barBoxes[i].setMaxWidth(Double.MAX_VALUE);
        barBoxes[i].setPrefHeight(5);
        barBoxes[i].setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barBoxes[i], Priority.ALWAYS);

        countLabels[i] = new Label("—");
        countLabels[i].setFont(Font.font("System", FontWeight.BOLD, 12));
        countLabels[i].setMinWidth(28);
        countLabels[i].setAlignment(Pos.CENTER_RIGHT);

        pctLabels[i] = new Label("—");
        pctLabels[i].setFont(Font.font("System", 11));
        pctLabels[i].setStyle("-fx-text-fill: " + mutedClr + ";");
        pctLabels[i].setMinWidth(32);
        pctLabels[i].setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(8, dot, nameLabel, barBoxes[i], countLabels[i], pctLabels[i]);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 14, 9, 14));
        if (borderClr != null) {
            row.setStyle(String.format(
                    "-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
                    borderClr));
        }
        return row;
    }

    // ================================================================
    // Colors
    // ================================================================

    private String categoryColor(String cat) {
        if (cat == null)
            return "#888888";
        return switch (cat) {
            case "Criminal" -> tm.accentRed();
            case "Traffic" -> tm.accentOrange();
            case "Civil" -> tm.accentBlue();
            case "Succession" -> tm.accentPurple();
            case "Children" -> "#d55e8a";
            default -> "#888888";
        };
    }

    private String pipelineColor(String status) {
        return switch (status) {
            case "Registered" -> tm.accentGreen();
            case "Mention" -> tm.accentOrange();
            case "Hearing" -> "#c9a227";
            case "Ruling" -> tm.accentBlue();
            case "Appeal" -> tm.accentPurple();
            case "Closed" -> "#9a9a9a";
            default -> "#888888";
        };
    }

    // ================================================================
    // Stat card
    // ================================================================

    private Label statValue() {
        Label lbl = new Label("—");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 34));
        return lbl;
    }

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
                cardBg, cardBorder));
        if (grow)
            HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ================================================================
    // Action button
    // ================================================================

    private Button makeActionBtn(String title, Feather icon, String color, Runnable action) {
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(15);
        fi.setIconColor(Color.web(color));
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        HBox inner = new HBox(8, fi, lbl);
        inner.setAlignment(Pos.CENTER_LEFT);
        Button btn = new Button();
        btn.setGraphic(inner);
        btn.setCursor(javafx.scene.Cursor.HAND);
        String base = String.format("-fx-background-color: %s18; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 9 18;", color, color);
        String hover = String.format("-fx-background-color: %s30; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 9 18;", color, color);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        btn.setOnAction(e -> {
            if (action != null)
                action.run();
        });
        return btn;
    }

    // ================================================================
    // Handlers
    // ================================================================

    private void handleNewCase() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        while (true) {
            Optional<CourtCase> result = dialog.showAndWait();
            if (result.isEmpty())
                break;
            CourtCase c = result.get();
            List<CaseFormDialog.ParticipantEntry> participants = dialog.getParticipantsToCreate();
            caseDao.insert(c);
            caseDao.upsertFirstCharge(c.getCaseId(), c.getChargeParticulars(), c.getChargePlea(), c.getChargeVerdict());
            saveParticipants(c, participants);
            if (!dialog.isAddAnother())
                break;
            dialog = new CaseFormDialog(null);
        }
        loadDataAsync();
    }

    private void handleNewPerson() {
        OffenderFormDialog dialog = new OffenderFormDialog(null);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            Person p = link.getPerson();
            personDao.insert(p);
            if (link.getCourtCase() != null) {
                CaseParticipant cp = new CaseParticipant();
                cp.setCaseId(link.getCourtCase().getCaseId());
                cp.setPersonId(p.getPersonId());
                cp.setRoleType("Accused");
                caseDao.addParticipant(cp);
            }
            loadDataAsync();
        });
    }

    private void saveParticipants(CourtCase c, List<CaseFormDialog.ParticipantEntry> entries) {
        if (entries.isEmpty())
            return;
        for (CaseFormDialog.ParticipantEntry entry : entries) {
            Person p = new Person();
            p.setFirstName(entry.firstName());
            p.setLastName(entry.lastName());
            if (!entry.nationalId().isBlank())
                p.setNationalId(entry.nationalId());
            personDao.insert(p);
            CaseParticipant cp = new CaseParticipant();
            cp.setCaseId(c.getCaseId());
            cp.setPersonId(p.getPersonId());
            cp.setRoleType(entry.roleType());
            caseDao.addParticipant(cp);
        }
    }

    public Parent getRoot() {
        return root;
    }
}
