package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CourtCase;
import javafx.application.Platform;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import com.courttrack.sync.SyncCoordinator;
import javafx.stage.Modality;

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
    private Button prevBtn, nextBtn;
    private Label pageLabel;
    private int currentPage = 0;
    private int pageSize = 5;
    private int totalCount = 0;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public CaseListView(Consumer<CourtCase> onViewDetail) {
        long t0 = System.currentTimeMillis();
        this.onViewDetail = onViewDetail;
        root = new VBox(20);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
        loadPage();
        System.out.println("[DEBUG] CaseListView constructor TOTAL: " + (System.currentTimeMillis() - t0) + "ms");
    }

    private void loadPage() {
        String status = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query = searchField.getText();
        
        int offset = currentPage * pageSize;
        
        new Thread(() -> {
            long t0 = System.currentTimeMillis();
            List<CourtCase> cases;
            int count;
            if (query != null && !query.isEmpty()) {
                cases = caseDao.search(query);
                count = cases.size();
                final int fCount = count;
                final List<CourtCase> fCases = cases.stream().skip(offset).limit(pageSize).toList();
                System.out.println("[DEBUG] CaseListView: loadPage DB query: " + (System.currentTimeMillis() - t0) + "ms");
                Platform.runLater(() -> {
                    caseList.setAll(fCases);
                    totalCount = fCount;
                    updatePaginationControls();
                });
            } else {
                cases = caseDao.findByStatusAndCategoryPaginated(status, category, offset, pageSize);
                count = caseDao.countByStatusAndCategory(status, category);
                System.out.println("[DEBUG] CaseListView: loadPage DB query: " + (System.currentTimeMillis() - t0) + "ms");
                final List<CourtCase> finalCases = cases;
                Platform.runLater(() -> {
                    caseList.setAll(finalCases);
                    totalCount = count;
                    updatePaginationControls();
                });
            }
        }).start();
    }

    private void updatePaginationControls() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        pageLabel.setText((currentPage + 1) + " / " + Math.max(1, totalPages));
        prevBtn.setDisable(currentPage == 0);
        nextBtn.setDisable(currentPage >= totalPages - 1 || totalCount == 0);
    }

    private void buildUI() {
        Label pageTitle = new Label("Case Management");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label pageSubtitle = new Label("View, add, and manage court cases");
        pageSubtitle.setFont(Font.font("System", 14));
        pageSubtitle.getStyleClass().add("text-muted");

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        searchField = new TextField();
        searchField.setPromptText("Search by case number, title, or sentence...");
        searchField.setPrefWidth(300);
        searchField.textProperty().addListener((obs, o, n) -> { currentPage = 0; loadPage(); });

        statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "OPEN", "CLOSED", "ADJOURNED", "DISMISSED", "SETTLED"));
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> { currentPage = 0; loadPage(); });

        categoryFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Criminal", "Traffic", "Civil"));
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> { currentPage = 0; loadPage(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        prevBtn = new Button("Prev");
        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; loadPage(); } });
        
        pageLabel = new Label("1 / 1");
        pageLabel.setMinWidth(60);
        pageLabel.setAlignment(Pos.CENTER);
        
        nextBtn = new Button("Next");
        nextBtn.setOnAction(e -> { currentPage++; loadPage(); });

        Button addBtn = new Button("+ New Case");
        addBtn.getStyleClass().add("accent");
        addBtn.setOnAction(e -> handleAdd());

        HBox toolbar = new HBox(10, searchField, statusFilter, categoryFilter, spacer, prevBtn, pageLabel, nextBtn, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table = createTable();
        caseList = FXCollections.observableArrayList();
        table.setItems(caseList);
        table.setPlaceholder(new Label("Loading..."));
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

        // Sentence with tooltip
        TableColumn<CourtCase, String> sentenceCol = new TableColumn<>("Sentence");
        sentenceCol.setPrefWidth(220);
        sentenceCol.setMinWidth(150);
        sentenceCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getSentence() != null ? cd.getValue().getSentence() : "\u2014"
        ));
        sentenceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("\u2014")) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item));
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
                    switch (status) {
                        case "OPEN":
                            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeOpenBg(), tm.badgeOpenText()));
                            break;
                        case "CLOSED":
                            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeClosedBg(), tm.badgeClosedText()));
                            break;
                        case "ADJOURNED":
                            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeTrafficBg(), tm.badgeTrafficText()));
                            break;
                        case "DISMISSED":
                            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.accentRed() + "33", tm.accentRed()));
                            break;
                        case "SETTLED":
                            badge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", tm.badgeCivilBg(), tm.badgeCivilText()));
                            break;
                        default:
                            badge.setStyle("-fx-background-color: #66666633; -fx-text-fill: #666666; -fx-background-radius: 4;");
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
            private final Button deleteBtn = iconBtn(Feather.TRASH_2, "Delete case", tm.accentRed());
            private final HBox box = new HBox(2, viewBtn, editBtn, deleteBtn);
            {
                box.setAlignment(Pos.CENTER);
                viewBtn.setOnAction(e -> handleView(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(caseCol, catCol, sentenceCol, statusCol, dateCol, actionsCol);
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

    private void handleDelete(CourtCase c) {
        Dialog<Boolean> confirm = new Dialog<>();
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText(null);
        confirm.initModality(Modality.APPLICATION_MODAL);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16, 24, 8, 24));
        content.setPrefWidth(400);

        FontIcon warnIcon = new FontIcon(Feather.ALERT_TRIANGLE);
        warnIcon.setIconSize(28);
        warnIcon.setIconColor(Color.web(tm.accentRed()));

        Label titleLabel = new Label("Delete case " + c.getCaseNumber() + "?");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox titleRow = new HBox(12, warnIcon, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label detailLabel = new Label("This case will be soft-deleted from the system. " +
            "The record can be restored later if needed.\n\n" +
            "Title: " + (c.getCaseTitle() != null ? c.getCaseTitle() : "\u2014"));
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("text-muted");

        content.getChildren().addAll(titleRow, detailLabel);

        confirm.getDialogPane().setContent(content);

        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        confirm.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);

        Button deleteBtn = (Button) confirm.getDialogPane().lookupButton(deleteType);
        deleteBtn.setStyle("-fx-background-color: " + tm.accentRed() + "; -fx-text-fill: white;");

        confirm.setResultConverter(bt -> bt == deleteType);
        Optional<Boolean> result = confirm.showAndWait();
        if (result.isPresent() && result.get()) {
            caseDao.softDelete(c.getCaseId());
            SyncCoordinator.getInstance().queueCaseSync(c.getCaseId(), "DELETE", null);
            refreshTable();
        }
    }

    public Parent getRoot() { return root; }
}
