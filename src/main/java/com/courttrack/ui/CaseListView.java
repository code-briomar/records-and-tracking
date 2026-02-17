package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CourtCase;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

public class CaseListView {
    private final VBox root;
    private final CaseDao caseDao = new CaseDao();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<CourtCase> onViewDetail;
    private TableView<CourtCase> table;
    private ObservableList<CourtCase> caseList;
    private TextField searchField;
    private ComboBox<String> statusFilter;
    private ComboBox<String> categoryFilter;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public CaseListView(Consumer<CourtCase> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(20);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
    }

    private void buildUI() {
        Label pageTitle = new Label("Case Management");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label pageSubtitle = new Label("View, add, and manage court cases");
        pageSubtitle.setFont(Font.font("System", 14));
        pageSubtitle.getStyleClass().add("text-muted");

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        searchField = new TextField();
        searchField.setPromptText("Search by case number, title, or charge...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, o, n) -> refreshTable());

        statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "OPEN", "CLOSED"));
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> refreshTable());

        categoryFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Criminal", "Traffic", "Civil"));
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> refreshTable());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ New Case");
        addBtn.getStyleClass().add("accent");
        addBtn.setOnAction(e -> handleAdd());

        HBox toolbar = new HBox(10, searchField, statusFilter, categoryFilter, spacer, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table = createTable();
        caseList = FXCollections.observableArrayList(caseDao.findAll());
        table.setItems(caseList);
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, toolbar, table);
    }

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createTable() {
        TableView<CourtCase> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tv.setFixedCellSize(52);

        // Case (number + title stacked)
        TableColumn<CourtCase, String> caseCol = new TableColumn<>("Case");
        caseCol.setPrefWidth(240);
        caseCol.setMinWidth(180);
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

        // Category badge
        TableColumn<CourtCase, String> catCol = new TableColumn<>("Category");
        catCol.setPrefWidth(100);
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseCategory()));
        catCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                if (empty || cat == null) { setGraphic(null); } else {
                    Label badge = new Label(cat);
                    badge.setPadding(new Insets(3, 10, 3, 10));
                    badge.setFont(Font.font("System", 11));
                    String color = switch (cat) { case "Criminal" -> tm.badgeCriminalText(); case "Traffic" -> tm.badgeTrafficText(); case "Civil" -> tm.badgeCivilText(); default -> "#888"; };
                    String bg = switch (cat) { case "Criminal" -> tm.badgeCriminalBg(); case "Traffic" -> tm.badgeTrafficBg(); case "Civil" -> tm.badgeCivilBg(); default -> "#eee"; };
                    badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", bg, color));
                    setGraphic(badge);
                }
                setText(null);
            }
        });

        // Charge particulars
        TableColumn<CourtCase, String> chargeCol = new TableColumn<>("Charge");
        chargeCol.setPrefWidth(220);
        chargeCol.setMinWidth(150);
        chargeCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getChargeParticulars() != null ? cd.getValue().getChargeParticulars() : "\u2014"
        ));

        // Verdict
        TableColumn<CourtCase, String> verdictCol = new TableColumn<>("Verdict");
        verdictCol.setPrefWidth(120);
        verdictCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getChargeVerdict()));
        verdictCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String verdict, boolean empty) {
                super.updateItem(verdict, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else if (verdict == null || verdict.isBlank()) {
                    setText("Pending");
                    setStyle("-fx-text-fill: #91918e; -fx-font-style: italic;");
                    setGraphic(null);
                } else {
                    String display = verdict.replace("_", " ");
                    display = display.substring(0, 1).toUpperCase() + display.substring(1).toLowerCase();
                    Label badge = new Label(display);
                    badge.setPadding(new Insets(3, 10, 3, 10));
                    badge.setFont(Font.font("System", 11));
                    boolean isConvicted = verdict.contains("CONVICTED") || verdict.contains("GUILTY");
                    boolean isForPlaintiff = verdict.contains("PLAINTIFF");
                    if (isConvicted) {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeCriminalBg(), tm.badgeCriminalText()));
                    } else if (isForPlaintiff) {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeOpenBg(), tm.badgeOpenText()));
                    } else {
                        badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeCivilBg(), tm.badgeCivilText()));
                    }
                    setGraphic(badge);
                    setText(null);
                    setStyle("");
                }
            }
        });

        // Status badge
        TableColumn<CourtCase, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(90);
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getCaseStatus()));
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); } else {
                    Label badge = new Label(status);
                    badge.setPadding(new Insets(3, 10, 3, 10));
                    badge.setFont(Font.font("System", 11));
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

        // Filing Date (formatted)
        TableColumn<CourtCase, String> dateCol = new TableColumn<>("Filed");
        dateCol.setPrefWidth(110);
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getFilingDate() != null ? cd.getValue().getFilingDate().format(DATE_FMT) : ""
        ));

        // Actions (icon buttons)
        TableColumn<CourtCase, Void> actionsCol = new TableColumn<>();
        actionsCol.setPrefWidth(80);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = iconBtn(Feather.EYE, "View case", tm.accentBlue());
            private final Button editBtn = iconBtn(Feather.EDIT_2, "Edit case", tm.accentGreen());
            private final HBox box = new HBox(2, viewBtn, editBtn);
            {
                box.setAlignment(Pos.CENTER);
                viewBtn.setOnAction(e -> handleView(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(caseCol, catCol, chargeCol, verdictCol, statusCol, dateCol, actionsCol);
        tv.setPlaceholder(new Label("No cases found"));
        return tv;
    }

    private void refreshTable() {
        String query = searchField.getText().trim();
        String status = statusFilter.getValue();
        String category = categoryFilter.getValue();
        if (!query.isEmpty()) { caseList.setAll(caseDao.search(query)); }
        else { caseList.setAll(caseDao.findByStatusAndCategory(status, category)); }
    }

    private Button iconBtn(Feather icon, String tooltip, String color) {
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(16);
        fi.setIconColor(Color.web(color));
        Button btn = new Button();
        btn.setGraphic(fi);
        btn.setTooltip(new Tooltip(tooltip));
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
        """, color)));
        btn.setOnMouseExited(e -> btn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 4 8;
            -fx-background-radius: 4;
        """));
        return btn;
    }


    private void handleAdd() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        Optional<CourtCase> result = dialog.showAndWait();
        result.ifPresent(c -> {
            caseDao.insert(c);
            caseDao.upsertFirstCharge(c.getCaseId(), c.getChargeParticulars(), c.getChargePlea(), c.getChargeVerdict());
            refreshTable();
        });
    }

    private void handleView(CourtCase c) {
        if (onViewDetail != null) {
            onViewDetail.accept(c);
        }
    }

    private void handleEdit(CourtCase c) {
        CaseFormDialog dialog = new CaseFormDialog(c);
        Optional<CourtCase> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            caseDao.update(updated);
            caseDao.upsertFirstCharge(updated.getCaseId(), updated.getChargeParticulars(), updated.getChargePlea(), updated.getChargeVerdict());
            refreshTable();
        });
    }

    public Parent getRoot() { return root; }
}
