package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CourtCase;
import com.courttrack.repository.CaseRepository;
import com.courttrack.sync.SyncCoordinator;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import com.courttrack.sync.SyncCoordinator;
import javafx.stage.Modality;

public class CaseListView {
    private final VBox root;
    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<CourtCase> onViewDetail;
    private TableView<CourtCase> table;
    private ObservableList<CourtCase> caseList;
    private TextField searchField;
    private ComboBox<String> statusFilter;
    private ComboBox<String> categoryFilter;
    private ComboBox<Integer> pageSizeSelector;
    private Button prevBtn, nextBtn;
    private Label pageLabel;
    private int currentPage = 0;
    private int pageSize = 15;
    private int totalCount = 0;
    private final Map<String, List<CourtCase>> pageCache = new LinkedHashMap<String, List<CourtCase>>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<CourtCase>> eldest) {
            return size() > 5;
        }
    };

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

    public void refresh() {
        currentPage = 0;
        pageCache.clear();
        loadPage();
    }

    private String buildCacheKey(int page) {
        String status = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query = searchField.getText();
        return status + "|" + category + "|" + (query != null ? query : "") + "|" + page;
    }

    private void loadPage() {
        String status = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query = searchField.getText();
        String cacheKey = buildCacheKey(currentPage);
        
        int offset = currentPage * pageSize;
        
        List<CourtCase> cached = pageCache.get(cacheKey);
        if (cached != null) {
            caseList.setAll(cached);
            totalCount = cached.size() < pageSize && currentPage > 0 ? offset + cached.size() : offset + pageSize + (cached.size() == pageSize ? 0 : -1);
            caseRepo.countByStatusAndCategory(status, category, count -> Platform.runLater(() -> {
                totalCount = count;
                updatePaginationControls();
            }));
            preloadNextPage();
            return;
        }
        
        if (query != null && !query.isEmpty()) {
            caseRepo.search(query, cases -> {
                int count = cases.size();
                List<CourtCase> paged = cases.stream().skip(offset).limit(pageSize).toList();
                pageCache.put(buildCacheKey(currentPage), paged);
                Platform.runLater(() -> {
                    caseList.setAll(paged);
                    totalCount = count;
                    updatePaginationControls();
                });
                preloadNextPage();
            });
        } else {
            caseRepo.getAllPaginated(status, category, offset, pageSize, cases -> {
                pageCache.put(buildCacheKey(currentPage), cases);
                caseRepo.countByStatusAndCategory(status, category, count -> {
                    Platform.runLater(() -> {
                        caseList.setAll(cases);
                        totalCount = count;
                        updatePaginationControls();
                    });
                    preloadNextPage();
                });
            });
        }
    }

    private void preloadNextPage() {
        String nextKey = buildCacheKey(currentPage + 1);
        if (pageCache.containsKey(nextKey)) return;
        
        String status = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query = searchField.getText();
        int nextOffset = (currentPage + 1) * pageSize;
        
        if (query != null && !query.isEmpty()) {
            caseRepo.search(query, cases -> {
                if (cases.size() > nextOffset) {
                    List<CourtCase> paged = cases.stream().skip(nextOffset).limit(pageSize).toList();
                    pageCache.put(nextKey, paged);
                }
            });
        } else {
            caseRepo.getAllPaginated(status, category, nextOffset, pageSize, cases -> {
                if (!cases.isEmpty()) {
                    pageCache.put(nextKey, cases);
                }
            });
        }
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
        searchField.textProperty().addListener((obs, o, n) -> { pageCache.clear(); currentPage = 0; loadPage(); });

        statusFilter = new ComboBox<>(FXCollections.observableArrayList("All", "OPEN", "CLOSED", "ADJOURNED", "DISMISSED", "SETTLED"));
        statusFilter.setValue("All");
        statusFilter.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });

        categoryFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Criminal", "Traffic", "Civil"));
        categoryFilter.setValue("All");
        categoryFilter.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });

        pageSizeSelector = new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 20, 25, 30, 40, 50, 75, 100));
        pageSizeSelector.setValue(15);
        pageSizeSelector.setOnAction(e -> {
            pageSize = pageSizeSelector.getValue();
            pageCache.clear();
            currentPage = 0;
            loadPage();
        });

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

        Label pageSizeLabel = new Label("Items:");
        pageSizeLabel.setStyle("-fx-text-fill: #666;");

        HBox toolbar = new HBox(10, searchField, statusFilter, categoryFilter, spacer, prevBtn, pageLabel, nextBtn, addBtn, pageSizeLabel, pageSizeSelector);
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

        TableColumn<CourtCase, Number> numCol = new TableColumn<>("No.");
        numCol.setPrefWidth(45);
        numCol.setSortable(false);
        numCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf((currentPage * pageSize) + getIndex() + 1));
            }
        });
        tv.getColumns().add(0, numCol);

        // Case (number + title stacked) - simplified
        TableColumn<CourtCase, String> caseCol = new TableColumn<>("Case");
        caseCol.setPrefWidth(240);
        caseCol.setMinWidth(180);
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

        // Category badge - simplified
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
                    String color = switch (cat) { case "Criminal" -> tm.badgeCriminalText(); case "Traffic" -> tm.badgeTrafficText(); case "Civil" -> tm.badgeCivilText(); default -> "#888"; };
                    String bg = switch (cat) { case "Criminal" -> tm.badgeCriminalBg(); case "Traffic" -> tm.badgeTrafficBg(); case "Civil" -> tm.badgeCivilBg(); default -> "#eee"; };
                    badge.setText(cat);
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
        if (!query.isEmpty()) {
            caseRepo.search(query, cases -> Platform.runLater(() -> caseList.setAll(cases)));
        } else {
            caseRepo.getByStatusAndCategory(status, category, cases -> Platform.runLater(() -> caseList.setAll(cases)));
        }
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
            caseRepo.save(c, null, () -> {
                com.courttrack.model.Charge charge = new com.courttrack.model.Charge();
                charge.setCaseId(c.getCaseId());
                charge.setParticulars(c.getChargeParticulars());
                charge.setPlea(c.getChargePlea());
                charge.setVerdict(c.getChargeVerdict());
                caseRepo.saveCharge(charge, this::refreshTable);
            });
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
            caseRepo.save(updated, null, () -> {
                com.courttrack.model.Charge charge = new com.courttrack.model.Charge();
                charge.setCaseId(updated.getCaseId());
                charge.setParticulars(updated.getChargeParticulars());
                charge.setPlea(updated.getChargePlea());
                charge.setVerdict(updated.getChargeVerdict());
                caseRepo.saveCharge(charge, this::refreshTable);
            });
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
            caseRepo.getById(c.getCaseId(), courtCase -> {
                if (courtCase != null) {
                    courtCase.setDeleted(true);
                    caseRepo.save(courtCase, null, this::refreshTable);
                }
            });
        }
    }

    public Parent getRoot() { return root; }
}
