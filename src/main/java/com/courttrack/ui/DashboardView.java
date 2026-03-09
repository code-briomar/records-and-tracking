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

    private HBox statsRow;
    private TableView<CourtCase> recentTable;
    private Label recentPlaceholderLabel;
    private VBox recentSection;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public DashboardView(Runnable onNavigateCases, Runnable onNavigatePersons, Consumer<CourtCase> onViewCase) {
        this.onNavigateCases = onNavigateCases;
        this.onNavigatePersons = onNavigatePersons;
        this.onViewCase = onViewCase;
        root = new VBox(24);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
        loadDataAsync();
    }

    public void refresh() {
        loadDataAsync();
    }

    private void loadDataAsync() {
        // Load stats in background
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            int totalCases = caseDao.countAll();
            int openCases = caseDao.countByStatus("OPEN");
            int closedCases = caseDao.countByStatus("CLOSED");
            int totalOffenders = personDao.countAll();
            System.out.println("[DEBUG] Dashboard: DB stats queries (async): " + (System.currentTimeMillis() - t0) + "ms");

            Platform.runLater(() -> {
                statsRow.getChildren().setAll(
                    createStatCard("Total Cases", String.valueOf(totalCases), tm.accentBlue()),
                    createStatCard("Open Cases", String.valueOf(openCases), tm.accentGreen()),
                    createStatCard("Closed Cases", String.valueOf(closedCases), tm.accentOrange()),
                    createStatCard("Persons on Record", String.valueOf(totalOffenders), tm.accentPurple())
                );
            });
        }).start();

        // Load recent cases in background
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            var cases = caseDao.findRecent(10);
            System.out.println("[DEBUG] Dashboard: Recent cases query (async): " + (System.currentTimeMillis() - t0) + "ms");

            Platform.runLater(() -> {
                recentTable.getItems().setAll(cases);
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
        long t0 = System.currentTimeMillis();
        Label pageTitle = new Label("Dashboard");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label pageSubtitle = new Label("Overview of court records and statistics");
        pageSubtitle.setFont(Font.font("System", 14));
        pageSubtitle.getStyleClass().add("text-muted");

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        statsRow = new HBox(16);
        statsRow.getChildren().addAll(
            createStatCard("Total Cases", "...", tm.accentBlue()),
            createStatCard("Open Cases", "...", tm.accentGreen()),
            createStatCard("Closed Cases", "...", tm.accentOrange()),
            createStatCard("Persons on Record", "...", tm.accentPurple())
        );

        // --- Quick Actions ---
        Label actionsTitle = new Label("Quick Actions");
        actionsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        HBox actionsRow = new HBox(14);
        actionsRow.getChildren().addAll(
            createActionCard("New Case", "File a new court case", Feather.PLUS_CIRCLE, tm.accentBlue(), this::handleNewCase),
            createActionCard("Add Person", "Register a new person", Feather.USER_PLUS, tm.accentGreen(), this::handleNewPerson),
            createActionCard("View Cases", "Browse all court cases", Feather.FOLDER, tm.accentOrange(), onNavigateCases),
            createActionCard("View Persons", "Browse all persons", Feather.USERS, tm.accentPurple(), onNavigatePersons)
        );

        // --- Recent Cases ---
        Label recentTitle = new Label("Recent Cases");
        recentTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        recentTable = createRecentCasesTable();
        recentPlaceholderLabel = new Label("Loading...");
        recentTable.setPlaceholder(recentPlaceholderLabel);
        VBox.setVgrow(recentTable, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, statsRow, actionsTitle, actionsRow, recentTitle, recentTable);
        System.out.println("[DEBUG] Dashboard: buildUI TOTAL: " + (System.currentTimeMillis() - t0) + "ms");
    }

    private VBox createStatCard(String label, String value, String color) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setPrefWidth(220);
        card.setMinHeight(110);
        card.getStyleClass().add("bordered");

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 32));

        Label textLabel = new Label(label);
        textLabel.setFont(Font.font("System", 13));
        textLabel.getStyleClass().add("text-muted");

        Region accent = new Region();
        accent.setPrefHeight(3);
        accent.setMaxWidth(40);
        accent.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 2;", color));

        card.getChildren().addAll(accent, valueLabel, textLabel);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox createActionCard(String title, String description, Feather icon, String color, Runnable action) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(18));
        card.setPrefWidth(200);
        card.setMinHeight(120);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("bordered");
        card.setStyle(card.getStyle() + "-fx-cursor: hand;");

        StackPane iconCircle = new StackPane();
        iconCircle.setMinSize(40, 40);
        iconCircle.setMaxSize(40, 40);
        iconCircle.setStyle(String.format(
            "-fx-background-color: %s18; -fx-background-radius: 20;", color));

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

        card.getChildren().addAll(iconCircle, titleLabel, descLabel);
        HBox.setHgrow(card, Priority.ALWAYS);

        // Hover effect
        card.setOnMouseEntered(e -> card.setStyle(String.format(
            "-fx-cursor: hand; -fx-background-color: %s08; -fx-border-color: %s;",
            color, color)));
        card.setOnMouseExited(e -> card.setStyle("-fx-cursor: hand;"));
        card.setOnMouseClicked(e -> action.run());

        return card;
    }

    private void handleNewCase() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        Optional<CourtCase> result = dialog.showAndWait();
        result.ifPresent(c -> {
            caseDao.insert(c);
            caseDao.upsertFirstCharge(c.getCaseId(), c.getChargeParticulars(), c.getChargePlea(), c.getChargeVerdict());
            // Refresh dashboard
            root.getChildren().clear();
            buildUI();
        });
    }

    private void handleNewPerson() {
        OffenderFormDialog dialog = new OffenderFormDialog(null);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            Person p = link.getPerson();
            personDao.insert(p);
            CaseParticipant cp = new CaseParticipant();
            cp.setCaseId(link.getCourtCase().getCaseId());
            cp.setPersonId(p.getPersonId());
            cp.setRoleType("Accused");
            caseDao.addParticipant(cp);
            // Refresh dashboard
            root.getChildren().clear();
            buildUI();
        });
    }

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createRecentCasesTable() {
        TableView<CourtCase> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<CourtCase, Number> numCol = new TableColumn<>("No.");
        numCol.setPrefWidth(45);
        numCol.setSortable(false);
        numCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });
        table.getColumns().add(0, numCol);

        // Case (number + title stacked) - simplified
        TableColumn<CourtCase, String> caseCol = new TableColumn<>("Case");
        caseCol.setPrefWidth(220);
        caseCol.setCellValueFactory(cd -> {
            CourtCase c = cd.getValue();
            String text = c.getCaseNumber();
            if (c.getCaseTitle() != null && !c.getCaseTitle().isBlank()) {
                text = text + "\n" + c.getCaseTitle();
            }
            return new SimpleStringProperty(text);
        });
        caseCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String[] parts = text.split("\n", 2);
                    VBox box = new VBox(0);
                    Label numLabel = new Label(parts[0]);
                    numLabel.setFont(Font.font("System", FontWeight.MEDIUM, 13));
                    box.getChildren().add(numLabel);
                    if (parts.length > 1 && !parts[1].isBlank()) {
                        Label titleLabel = new Label(parts[1]);
                        titleLabel.setFont(Font.font("System", 11));
                        titleLabel.getStyleClass().add("text-muted");
                        box.getChildren().add(titleLabel);
                    }
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        // Category badge - simplified with reused label
        TableColumn<CourtCase, String> catCol = new TableColumn<>("Category");
        catCol.setPrefWidth(100);
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseCategory() != null ? cd.getValue().getCaseCategory() : ""));
        catCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setPadding(new Insets(2, 8, 2, 8));
                badge.setFont(Font.font("System", 11));
            }
            @Override
            protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                if (empty || cat == null || cat.isBlank()) {
                    setGraphic(null);
                } else {
                    badge.setText(cat);
                    String color = switch (cat) { case "Criminal" -> tm.badgeCriminalText(); case "Traffic" -> tm.badgeTrafficText(); case "Civil" -> tm.badgeCivilText(); default -> "#888"; };
                    String bg = switch (cat) { case "Criminal" -> tm.badgeCriminalBg(); case "Traffic" -> tm.badgeTrafficBg(); case "Civil" -> tm.badgeCivilBg(); default -> "#eee"; };
                    badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", bg, color));
                    setGraphic(badge);
                }
                setText(null);
            }
        });

        // Status badge - simplified with reused label
        TableColumn<CourtCase, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseStatus() != null ? cd.getValue().getCaseStatus() : ""));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setPadding(new Insets(2, 8, 2, 8));
                badge.setFont(Font.font("System", 11));
            }
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setGraphic(null);
                } else {
                    badge.setText(status);
                    if ("OPEN".equals(status)) {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeOpenBg(), tm.badgeOpenText()));
                    } else {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeClosedBg(), tm.badgeClosedText()));
                    }
                    setGraphic(badge);
                }
                setText(null);
            }
        });

        TableColumn<CourtCase, String> verdictCol = new TableColumn<>("Verdict");
        verdictCol.setPrefWidth(120);
        verdictCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getChargeVerdict() != null ? cd.getValue().getChargeVerdict().replace("_", " ") : "Pending"
        ));

        TableColumn<CourtCase, String> dateCol = new TableColumn<>("Filed");
        dateCol.setPrefWidth(110);
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getFilingDate() != null ? cd.getValue().getFilingDate().format(DATE_FMT) : ""
        ));

        // View button column
        TableColumn<CourtCase, Void> actionsCol = new TableColumn<>();
        actionsCol.setPrefWidth(70);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = createViewButton();
            {
                viewBtn.setOnAction(e -> {
                    CourtCase c = getTableView().getItems().get(getIndex());
                    if (onViewCase != null) onViewCase.accept(c);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : viewBtn);
            }
        });

        table.getColumns().addAll(caseCol, catCol, statusCol, verdictCol, dateCol, actionsCol);
        table.setItems(FXCollections.observableArrayList(caseDao.findRecent(5)));
        table.setPlaceholder(new Label("No cases found"));

        // Double-click to view
        table.setRowFactory(tv -> {
            TableRow<CourtCase> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && onViewCase != null) {
                    onViewCase.accept(row.getItem());
                }
            });
            return row;
        });

        return table;
    }

    private Button createViewButton() {
        FontIcon fi = new FontIcon(Feather.ARROW_RIGHT);
        fi.setIconSize(14);
        fi.setIconColor(Color.web(tm.accentBlue()));
        Button btn = new Button();
        btn.setGraphic(fi);
        btn.setTooltip(new Tooltip("View details"));
        btn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 4 8;
            -fx-background-radius: 4;
        """);
        btn.setOnMouseEntered(e -> btn.setStyle(String.format("""
            -fx-background-color: %s18;
            -fx-cursor: hand;
            -fx-padding: 4 8;
            -fx-background-radius: 4;
        """, tm.accentBlue())));
        btn.setOnMouseExited(e -> btn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 4 8;
            -fx-background-radius: 4;
        """));
        return btn;
    }

    public Parent getRoot() {
        return root;
    }
}
