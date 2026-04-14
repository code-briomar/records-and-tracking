package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.function.Consumer;

public class DashboardView {
    private final VBox root;
    private final CaseDao caseDao = new CaseDao();
    private final PersonDao personDao = new PersonDao();
    private final ThemeManager tm = ThemeManager.getInstance();

    private final Runnable onNavigateCases;
    private final Runnable onNavigatePersons;
    private final Consumer<CourtCase> onViewCase;

    // Stat labels
    private Label totalCasesValue;
    private Label openCasesValue;
    private Label closedCasesValue;
    private Label personsValue;

    // Category breakdown
    private static final String[] CATEGORIES = {"Criminal", "Traffic", "Civil", "Succession", "Children", "Other"};
    private final Region[]  catFills   = new Region[CATEGORIES.length];
    private final Region[]  catTracks  = new Region[CATEGORIES.length];
    private final HBox[]    catBarBoxes = new HBox[CATEGORIES.length];
    private final Label[]   catCounts  = new Label[CATEGORIES.length];
    private final Label[]   catPcts    = new Label[CATEGORIES.length];
    private final HBox[]    catRows    = new HBox[CATEGORIES.length];

    // Status breakdown
    private static final String[] STATUSES = {"Active", "Closed", "Pending", "Review"};
    private final Region[]  stFills    = new Region[STATUSES.length];
    private final Region[]  stTracks   = new Region[STATUSES.length];
    private final HBox[]    stBarBoxes  = new HBox[STATUSES.length];
    private final Label[]   stCounts   = new Label[STATUSES.length];
    private final Label[]   stPcts     = new Label[STATUSES.length];
    private final HBox[]    stRows     = new HBox[STATUSES.length];

    public DashboardView(Runnable onNavigateCases, Runnable onNavigatePersons, Consumer<CourtCase> onViewCase) {
        this.onNavigateCases   = onNavigateCases;
        this.onNavigatePersons = onNavigatePersons;
        this.onViewCase        = onViewCase;
        root = new VBox(0);
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        buildUI();
        loadDataAsync();
    }

    public void refresh() { loadDataAsync(); }

    // ================================================================
    // Data loading
    // ================================================================

    private void loadDataAsync() {
        new Thread(() -> {
            int total   = caseDao.countAll();
            int active  = caseDao.countByStatus("Active");
            int closed  = caseDao.countByStatus("Closed");
            int persons = personDao.countAll();

            int[] catCounts = new int[CATEGORIES.length];
            for (int i = 0; i < CATEGORIES.length; i++)
                catCounts[i] = caseDao.countByStatusAndCategory("All", CATEGORIES[i]);

            int[] stCounts = new int[STATUSES.length];
            for (int i = 0; i < STATUSES.length; i++)
                stCounts[i] = caseDao.countByStatus(STATUSES[i]);

            Platform.runLater(() -> {
                totalCasesValue .setText(String.format("%,d", total));
                openCasesValue  .setText(String.format("%,d", active));
                closedCasesValue.setText(String.format("%,d", closed));
                personsValue    .setText(String.format("%,d", persons));
                updateBars(catCounts, catFills, catTracks, catBarBoxes, this.catCounts, catPcts, catRows, total);
                updateBars(stCounts,  stFills,  stTracks,  stBarBoxes,  this.stCounts,  stPcts,  stRows,  total);
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
            // Update fill width once bar has a real width
            final int idx = i;
            final double p = pct;
            barBoxes[i].widthProperty().addListener((obs, ov, nv) -> {
                fills[idx].setPrefWidth(nv.doubleValue() * p);
                tracks[idx].setPrefWidth(nv.doubleValue() * (1 - p));
            });
            // Set immediately if width already known
            double w = barBoxes[i].getWidth();
            if (w > 0) {
                fills[i].setPrefWidth(w * pct);
                tracks[i].setPrefWidth(w * (1 - pct));
            }
            // Dim zero rows
            rows[i].setOpacity(c == 0 ? 0.35 : 1.0);
        }
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
        totalCasesValue  = statValue();
        openCasesValue   = statValue();
        closedCasesValue = statValue();
        personsValue     = statValue();

        HBox statsRow = new HBox(14,
            makeStatCard("TOTAL CASES",       totalCasesValue,  Feather.BRIEFCASE,    tm.accentBlue(),   true),
            makeStatCard("ACTIVE CASES",      openCasesValue,   Feather.FOLDER,       tm.accentGreen(),  true),
            makeStatCard("CLOSED CASES",      closedCasesValue, Feather.CHECK_CIRCLE, "#9a9a9a",         true),
            makeStatCard("PERSONS ON RECORD", personsValue,     Feather.USERS,        tm.accentPurple(), true)
        );
        VBox.setMargin(statsRow, new Insets(0, 0, 20, 0));

        // ── Action buttons ──────────────────────────────────────────
        HBox actionsRow = new HBox(12,
            makeActionBtn("New Case Filing", Feather.PLUS_CIRCLE, tm.accentBlue(),  this::handleNewCase),
            makeActionBtn("Register Person", Feather.USER_PLUS,   tm.accentGreen(), this::handleNewPerson)
        );
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(actionsRow, new Insets(0, 0, 20, 0));

        // ── Two-column breakdown ────────────────────────────────────
        VBox catCard = buildBreakdownCard("Cases by Category", buildCategoryRows());
        VBox stCard  = buildBreakdownCard("Cases by Status",   buildStatusRows());
        HBox.setHgrow(catCard, Priority.ALWAYS);
        HBox.setHgrow(stCard,  Priority.ALWAYS);
        catCard.setMaxWidth(Double.MAX_VALUE);
        stCard.setMaxWidth(Double.MAX_VALUE);

        HBox bottomRow = new HBox(14, catCard, stCard);
        bottomRow.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, statsRow, actionsRow, bottomRow);
    }

    // ================================================================
    // Breakdown card builder
    // ================================================================

    private VBox buildBreakdownCard(String title, VBox rows) {
        boolean dark      = tm.isDark();
        String cardBg     = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18"  : "#00000018";

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
                "-fx-border-radius: 8; -fx-background-radius: 8;", cardBg, cardBorder));
        return card;
    }

    private VBox buildCategoryRows() {
        boolean dark      = tm.isDark();
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String mutedClr   = dark ? "#8a8a8a"   : "#5a5a5a";
        String trackBg    = dark ? "#ffffff0f"  : "#0000000f";

        VBox rows = new VBox(0);
        for (int i = 0; i < CATEGORIES.length; i++) {
            String color = categoryColor(CATEGORIES[i]);
            catRows[i] = buildBreakdownRow(
                    CATEGORIES[i], color, trackBg, mutedClr,
                    catFills, catTracks, catBarBoxes, catCounts, catPcts, i,
                    i < CATEGORIES.length - 1 ? cardBorder : null);
            rows.getChildren().add(catRows[i]);
        }
        return rows;
    }

    private VBox buildStatusRows() {
        boolean dark      = tm.isDark();
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String mutedClr   = dark ? "#8a8a8a"   : "#5a5a5a";
        String trackBg    = dark ? "#ffffff0f"  : "#0000000f";

        VBox rows = new VBox(0);
        for (int i = 0; i < STATUSES.length; i++) {
            String color = statusColor(STATUSES[i]);
            stRows[i] = buildBreakdownRow(
                    STATUSES[i], color, trackBg, mutedClr,
                    stFills, stTracks, stBarBoxes, stCounts, stPcts, i,
                    i < STATUSES.length - 1 ? cardBorder : null);
            rows.getChildren().add(stRows[i]);
        }
        return rows;
    }

    private HBox buildBreakdownRow(String name, String color, String trackBg, String mutedClr,
                                    Region[] fills, Region[] tracks, HBox[] barBoxes,
                                    Label[] countLabels, Label[] pctLabels,
                                    int i, String borderClr) {
        Region dot = new Region();
        dot.setMinSize(8, 8); dot.setMaxSize(8, 8);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 12));
        nameLabel.setMinWidth(80);

        // Custom colored bar
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

    private String categoryColor(String cat) {
        return switch (cat) {
            case "Criminal"   -> tm.accentRed();
            case "Traffic"    -> tm.accentOrange();
            case "Civil"      -> tm.accentBlue();
            case "Succession" -> tm.accentPurple();
            case "Children"   -> "#d55e8a";
            default           -> "#888888";
        };
    }

    private String statusColor(String status) {
        return switch (status) {
            case "Active"  -> tm.accentGreen();
            case "Closed"  -> "#9a9a9a";
            case "Pending" -> tm.accentOrange();
            case "Review"  -> tm.accentBlue();
            default        -> "#888888";
        };
    }

    // ================================================================
    // Stat card
    // ================================================================

    private Label statValue() {
        Label lbl = new Label("\u2014");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 34));
        return lbl;
    }

    private VBox makeStatCard(String labelText, Label valueLabel, Feather icon, String color, boolean grow) {
        boolean dark      = tm.isDark();
        String cardBg     = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18"  : "#00000018";

        Region topBar = new Region();
        topBar.setPrefHeight(3); topBar.setMinHeight(3);
        topBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 8 8 0 0;");

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(36, 36); iconCircle.setMaxSize(36, 36);
        iconCircle.setStyle(String.format("-fx-background-color: %s1e; -fx-background-radius: 18;", color));
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(16); fi.setIconColor(Color.web(color));
        iconCircle.getChildren().add(fi);

        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.getStyleClass().add("text-muted");

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topRow = new HBox(spacer, iconCircle);
        topRow.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(8, topRow, valueLabel, lbl);
        body.setPadding(new Insets(16, 20, 16, 20));

        VBox card = new VBox(0, topBar, body);
        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s;" +
                "-fx-border-radius: 8; -fx-background-radius: 8;", cardBg, cardBorder));
        if (grow) HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    // ================================================================
    // Action button
    // ================================================================

    private Button makeActionBtn(String title, Feather icon, String color, Runnable action) {
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(15); fi.setIconColor(Color.web(color));
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        HBox inner = new HBox(8, fi, lbl);
        inner.setAlignment(Pos.CENTER_LEFT);
        Button btn = new Button();
        btn.setGraphic(inner);
        btn.setCursor(javafx.scene.Cursor.HAND);
        String base  = String.format("-fx-background-color: %s18; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 9 18;", color, color);
        String hover = String.format("-fx-background-color: %s30; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 9 18;", color, color);
        btn.setStyle(base);
        btn.setOnMouseEntered(e -> btn.setStyle(hover));
        btn.setOnMouseExited(e  -> btn.setStyle(base));
        btn.setOnAction(e -> { if (action != null) action.run(); });
        return btn;
    }

    // ================================================================
    // Handlers
    // ================================================================

    private void handleNewCase() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        while (true) {
            Optional<CourtCase> result = dialog.showAndWait();
            if (result.isEmpty()) break;
            CourtCase c = result.get();
            java.util.List<CaseFormDialog.ParticipantEntry> participants = dialog.getParticipantsToCreate();
            caseDao.insert(c);
            caseDao.upsertFirstCharge(c.getCaseId(), c.getChargeParticulars(), c.getChargePlea(), c.getChargeVerdict());
            saveParticipants(c, participants);
            if (!dialog.isAddAnother()) break;
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

    private void saveParticipants(CourtCase c, java.util.List<CaseFormDialog.ParticipantEntry> entries) {
        if (entries.isEmpty()) return;
        for (CaseFormDialog.ParticipantEntry entry : entries) {
            Person p = new Person();
            p.setFirstName(entry.firstName());
            p.setLastName(entry.lastName());
            if (!entry.nationalId().isBlank()) p.setNationalId(entry.nationalId());
            personDao.insert(p);
            CaseParticipant cp = new CaseParticipant();
            cp.setCaseId(c.getCaseId());
            cp.setPersonId(p.getPersonId());
            cp.setRoleType(entry.roleType());
            caseDao.addParticipant(cp);
        }
    }

    public Parent getRoot() { return root; }
}
