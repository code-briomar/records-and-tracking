package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CourtCase;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.time.format.DateTimeFormatter;

public class DashboardView {
    private final VBox root;
    private final CaseDao caseDao = new CaseDao();
    private final PersonDao personDao = new PersonDao();
    private final ThemeManager tm = ThemeManager.getInstance();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public DashboardView() {
        root = new VBox(24);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
    }

    private void buildUI() {
        Label pageTitle = new Label("Dashboard");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label pageSubtitle = new Label("Overview of court records and statistics");
        pageSubtitle.setFont(Font.font("System", 14));
        pageSubtitle.getStyleClass().add("text-muted");

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        int totalCases = caseDao.countAll();
        int openCases = caseDao.countByStatus("OPEN");
        int closedCases = caseDao.countByStatus("CLOSED");
        int totalOffenders = personDao.countAll();

        HBox statsRow = new HBox(16);
        statsRow.getChildren().addAll(
            createStatCard("Total Cases", String.valueOf(totalCases), tm.accentBlue()),
            createStatCard("Open Cases", String.valueOf(openCases), tm.accentGreen()),
            createStatCard("Closed Cases", String.valueOf(closedCases), tm.accentOrange()),
            createStatCard("Persons on Record", String.valueOf(totalOffenders), tm.accentPurple())
        );

        Label recentTitle = new Label("Recent Cases");
        recentTitle.setFont(Font.font("System", FontWeight.BOLD, 18));

        TableView<CourtCase> recentTable = createRecentCasesTable();
        VBox.setVgrow(recentTable, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, statsRow, recentTitle, recentTable);
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

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createRecentCasesTable() {
        TableView<CourtCase> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(52);

        // Case (number + title stacked)
        TableColumn<CourtCase, String> caseCol = new TableColumn<>("Case");
        caseCol.setPrefWidth(220);
        caseCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseNumber()));
        caseCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String num, boolean empty) {
                super.updateItem(num, empty);
                if (empty || num == null) {
                    setGraphic(null);
                } else {
                    CourtCase c = getTableView().getItems().get(getIndex());
                    VBox box = new VBox(1);
                    box.setPadding(new Insets(4, 0, 4, 0));
                    Label numLabel = new Label(num);
                    numLabel.setFont(Font.font("System", FontWeight.MEDIUM, 13));
                    box.getChildren().add(numLabel);
                    if (c.getCaseTitle() != null && !c.getCaseTitle().isBlank()) {
                        Label titleLabel = new Label(c.getCaseTitle());
                        titleLabel.setFont(Font.font("System", 11));
                        titleLabel.getStyleClass().add("text-muted");
                        box.getChildren().add(titleLabel);
                    }
                    setGraphic(box);
                }
                setText(null);
            }
        });

        TableColumn<CourtCase, String> catCol = new TableColumn<>("Category");
        catCol.setPrefWidth(100);
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseCategory()));

        TableColumn<CourtCase, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseStatus()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(status);
                    badge.setPadding(new Insets(3, 10, 3, 10));
                    badge.setFont(Font.font("System", 11));
                    if ("OPEN".equals(status)) {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeOpenBg(), tm.badgeOpenText()));
                    } else {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeClosedBg(), tm.badgeClosedText()));
                    }
                    setGraphic(badge);
                    setText(null);
                }
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

        table.getColumns().addAll(caseCol, catCol, statusCol, verdictCol, dateCol);
        table.setItems(FXCollections.observableArrayList(caseDao.findRecent(5)));
        table.setPlaceholder(new Label("No cases found"));

        return table;
    }

    public Parent getRoot() {
        return root;
    }
}
