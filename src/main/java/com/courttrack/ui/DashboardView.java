package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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

import java.time.format.DateTimeFormatter;
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

    private Label totalCasesValue;
    private Label openCasesValue;
    private Label closedCasesValue;
    private Label personsValue;
    private TableView<CourtCase> recentTable;
    private Label recentPlaceholderLabel;
    private Label showingRecentLabel;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

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

    private void loadDataAsync() {
        new Thread(() -> {
            int totalCases    = caseDao.countAll();
            int openCases     = caseDao.countByStatus("OPEN");
            int closedCases   = caseDao.countByStatus("CLOSED");
            int totalPersons  = personDao.countAll();
            Platform.runLater(() -> {
                totalCasesValue.setText(String.format("%,d", totalCases));
                openCasesValue.setText(String.format("%,d", openCases));
                closedCasesValue.setText(String.format("%,d", closedCases));
                personsValue.setText(String.format("%,d", totalPersons));
            });
        }).start();

        new Thread(() -> {
            var cases = caseDao.findRecent(10);
            Platform.runLater(() -> {
                recentTable.getItems().setAll(cases);
                int n = cases.size();
                showingRecentLabel.setText(n == 0 ? "No recent cases" : "Showing " + n + " most recent case" + (n == 1 ? "" : "s"));
                if (cases.isEmpty()) {
                    recentPlaceholderLabel.setText("No recent cases");
                    recentTable.setPlaceholder(recentPlaceholderLabel);
                } else {
                    recentTable.setPlaceholder(null);
                }
            });
        }).start();
    }

    private void buildUI() {
        boolean dark = tm.isDark();
        String cardBg     = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String cardStyle  = "-fx-background-color: " + cardBg + "; -fx-border-color: " + cardBorder +
                            "; -fx-border-radius: 8; -fx-background-radius: 8;";

        // ── Header ────────────────────────────────────────────────────────────
        Label pageTitle = new Label("Dashboard");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 40));

        Label pageSubtitle = new Label("Overview of court records and system activity.");
        pageSubtitle.getStyleClass().add("text-muted");
        pageSubtitle.setFont(Font.font("System", 14));

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);
        VBox.setMargin(titleBox, new Insets(0, 0, 28, 0));

        // ── Stats row ──────────────────────────────────────────────────────────
        totalCasesValue  = statValue();
        openCasesValue   = statValue();
        closedCasesValue = statValue();
        personsValue     = statValue();

        HBox statsRow = new HBox(14,
            makeStatCard("TOTAL CASES",      totalCasesValue,  cardStyle, true),
            makeStatCard("OPEN CASES",       openCasesValue,   cardStyle, true),
            makeStatCard("CLOSED CASES",     closedCasesValue, cardStyle, true),
            makeStatCard("PERSONS ON RECORD", personsValue,    cardStyle, true)
        );
        VBox.setMargin(statsRow, new Insets(0, 0, 32, 0));

        // ── Quick actions ──────────────────────────────────────────────────────
        Label actionsTitle = new Label("Quick Actions");
        actionsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        VBox.setMargin(actionsTitle, new Insets(0, 0, 12, 0));

        HBox actionsRow = new HBox(14,
            makeActionCard("New Case Filing",   "File a new court case record",    Feather.PLUS_CIRCLE, tm.accentBlue(),   cardStyle, this::handleNewCase),
            makeActionCard("Register Person",   "Add a person to the registry",    Feather.USER_PLUS,   tm.accentGreen(),  cardStyle, this::handleNewPerson),
            makeActionCard("Browse Cases",      "View all court case records",     Feather.FOLDER,      tm.accentOrange(), cardStyle, onNavigateCases),
            makeActionCard("Browse Persons",    "View the persons registry",       Feather.USERS,       tm.accentPurple(), cardStyle, onNavigatePersons)
        );
        VBox.setMargin(actionsRow, new Insets(0, 0, 32, 0));

        // ── Recent cases container ─────────────────────────────────────────────
        Label recentTitle = new Label("Recent Cases");
        recentTitle.setFont(Font.font("System", FontWeight.BOLD, 16));

        showingRecentLabel = new Label("Loading\u2026");
        showingRecentLabel.getStyleClass().add("text-muted");
        showingRecentLabel.setFont(Font.font("System", 12));

        FontIcon viewAllIcon = new FontIcon(Feather.ARROW_RIGHT);
        viewAllIcon.setIconSize(13);
        viewAllIcon.setIconColor(Color.web(tm.accentBlue()));
        Button viewAllBtn = new Button("View All");
        viewAllBtn.setGraphic(viewAllIcon);
        viewAllBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8;" +
                            "-fx-text-fill: " + tm.accentBlue() + "; -fx-background-radius: 4;");
        viewAllBtn.setOnAction(e -> { if (onNavigateCases != null) onNavigateCases.run(); });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox recentHeader = new HBox(recentTitle, headerSpacer, showingRecentLabel, viewAllBtn);
        recentHeader.setAlignment(Pos.CENTER_LEFT);
        recentHeader.setPadding(new Insets(8, 16, 8, 16));

        recentTable = createRecentCasesTable();
        recentPlaceholderLabel = new Label("Loading...");
        recentTable.setPlaceholder(recentPlaceholderLabel);
        recentTable.setStyle("-fx-border-color: transparent;");
        VBox.setVgrow(recentTable, Priority.ALWAYS);

        VBox recentContainer = new VBox(0, recentHeader, new Separator(), recentTable);
        recentContainer.setStyle(cardStyle);
        VBox.setVgrow(recentContainer, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, statsRow, actionsTitle, actionsRow, recentContainer);
    }

    private Label statValue() {
        Label lbl = new Label("\u2014");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 34));
        return lbl;
    }

    private VBox makeStatCard(String labelText, Label valueLabel, String cardStyle, boolean grow) {
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.getStyleClass().add("text-muted");

        VBox card = new VBox(6, lbl, valueLabel);
        card.setPadding(new Insets(18, 24, 18, 24));
        card.setStyle(cardStyle);
        if (grow) HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox makeActionCard(String title, String description, Feather icon, String color, String baseCardStyle, Runnable action) {
        boolean dark = tm.isDark();

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(42, 42);
        iconCircle.setMaxSize(42, 42);
        iconCircle.setStyle(String.format("-fx-background-color: %s18; -fx-background-radius: 21;", color));
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(18);
        fi.setIconColor(Color.web(color));
        iconCircle.getChildren().add(fi);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("System", 12));
        descLabel.getStyleClass().add("text-muted");
        descLabel.setWrapText(true);

        VBox card = new VBox(10, iconCircle, titleLabel, descLabel);
        card.setPadding(new Insets(20, 20, 20, 20));
        card.setStyle(baseCardStyle + "-fx-cursor: hand;");
        HBox.setHgrow(card, Priority.ALWAYS);

        card.setOnMouseEntered(e -> card.setStyle(String.format(
            "-fx-background-color: %s18; -fx-border-color: %s; -fx-border-radius: 8; -fx-background-radius: 8; -fx-cursor: hand;",
            color, color)));
        card.setOnMouseExited(e -> card.setStyle(baseCardStyle + "-fx-cursor: hand;"));
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

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

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createRecentCasesTable() {
        TableView<CourtCase> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // CASE ID
        TableColumn<CourtCase, CourtCase> caseCol = new TableColumn<>("CASE");
        caseCol.setPrefWidth(240);
        caseCol.setMinWidth(160);
        caseCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        caseCol.setCellFactory(col -> new TableCell<>() {
            private final Label numLbl = new Label();
            private final Label titleLbl = new Label();
            private final VBox box = new VBox(1, numLbl, titleLbl);
            {
                numLbl.setFont(Font.font("System", FontWeight.BOLD, 13));
                titleLbl.setFont(Font.font("System", 11));
                titleLbl.getStyleClass().add("text-muted");
            }
            @Override protected void updateItem(CourtCase c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setGraphic(null); } else {
                    numLbl.setText(c.getCaseNumber() != null ? c.getCaseNumber() : "");
                    String t = c.getCaseTitle();
                    boolean hasTitle = t != null && !t.isBlank();
                    titleLbl.setText(hasTitle ? t : "");
                    titleLbl.setVisible(hasTitle);
                    titleLbl.setManaged(hasTitle);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        // CATEGORY
        TableColumn<CourtCase, String> catCol = new TableColumn<>("CATEGORY");
        catCol.setPrefWidth(110);
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseCategory() != null ? cd.getValue().getCaseCategory() : ""));
        catCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.setPadding(new Insets(3, 10, 3, 10)); badge.setFont(Font.font("System", 11)); }
            @Override protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                setText(null);
                if (empty || cat == null || cat.isBlank()) { setGraphic(null); return; }
                badge.setText(cat);
                badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                    categoryBg(cat), categoryText(cat)));
                setGraphic(badge);
            }
        });

        // STATUS
        TableColumn<CourtCase, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setPrefWidth(100);
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseStatus() != null ? cd.getValue().getCaseStatus() : ""));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.setPadding(new Insets(3, 10, 3, 10)); badge.setFont(Font.font("System", 11)); }
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(null);
                if (empty || s == null || s.isBlank()) { setGraphic(null); return; }
                badge.setText(s);
                badge.setStyle(statusBadgeStyle(s));
                setGraphic(badge);
            }
        });

        // FILED
        TableColumn<CourtCase, String> dateCol = new TableColumn<>("FILED");
        dateCol.setPrefWidth(120);
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getFilingDate() != null ? cd.getValue().getFilingDate().format(DATE_FMT) : ""));

        // VIEW
        TableColumn<CourtCase, Void> actionsCol = new TableColumn<>();
        actionsCol.setPrefWidth(60);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = viewBtn();
            { btn.setOnAction(e -> { if (onViewCase != null) onViewCase.accept(getTableView().getItems().get(getIndex())); }); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().addAll(caseCol, catCol, statusCol, dateCol, actionsCol);
        table.setItems(FXCollections.observableArrayList());
        table.setPlaceholder(new Label("No cases found"));
        String altBg = tm.isDark() ? "#ffffff05" : "#00000005";
        table.setRowFactory(tv2 -> new TableRow<CourtCase>() {{
            indexProperty().addListener((obs, ov, nv) ->
                setStyle(nv.intValue() % 2 != 0 ? "-fx-background-color: " + altBg + ";" : ""));
        }});
        table.setRowFactory(tv -> {
            TableRow<CourtCase> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onViewCase != null)
                    onViewCase.accept(row.getItem());
            });
            return row;
        });
        return table;
    }

    private Button viewBtn() {
        FontIcon fi = new FontIcon(Feather.ARROW_RIGHT);
        fi.setIconSize(14);
        fi.setIconColor(Color.web(tm.accentBlue()));
        Button btn = new Button();
        btn.setGraphic(fi);
        btn.setTooltip(new Tooltip("View details"));
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + tm.accentBlue() + "18; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;"));
        return btn;
    }

    private String categoryBg(String cat) {
        return switch (cat) {
            case "Criminal" -> tm.badgeCriminalBg();
            case "Traffic"  -> tm.badgeTrafficBg();
            case "Civil"    -> tm.badgeCivilBg();
            case "Succession" -> tm.badgeSuccessionBg();
            case "Children" -> tm.badgeChildrenBg();
            default -> tm.badgeOtherBg();
        };
    }

    private String categoryText(String cat) {
        return switch (cat) {
            case "Criminal" -> tm.badgeCriminalText();
            case "Traffic"  -> tm.badgeTrafficText();
            case "Civil"    -> tm.badgeCivilText();
            case "Succession" -> tm.badgeSuccessionText();
            case "Children" -> tm.badgeChildrenText();
            default -> tm.badgeOtherText();
        };
    }

    private String statusBadgeStyle(String status) {
        return switch (status.toUpperCase()) {
            case "OPEN", "ACTIVE" -> String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                tm.badgeOpenBg(), tm.badgeOpenText());
            case "CLOSED" -> String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                tm.badgeClosedBg(), tm.badgeClosedText());
            case "ADJOURNED" -> String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                tm.badgeTrafficBg(), tm.badgeTrafficText());
            case "DISMISSED" -> String.format("-fx-background-color: %s33; -fx-text-fill: %s; -fx-background-radius: 4;",
                tm.accentRed(), tm.accentRed());
            case "SETTLED" -> String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                tm.badgeCivilBg(), tm.badgeCivilText());
            default -> "-fx-background-color: #66666633; -fx-text-fill: #666666; -fx-background-radius: 4;";
        };
    }

    public Parent getRoot() { return root; }
}
