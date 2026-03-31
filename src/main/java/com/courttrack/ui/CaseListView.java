package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import com.courttrack.repository.CaseRepository;
import com.courttrack.sync.SyncCoordinator;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.stage.Modality;

public class CaseListView {
    private final VBox root;
    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<CourtCase> onViewDetail;
    private TableView<CourtCase> table;
    private ObservableList<CourtCase> caseList;
    private Label placeholderLabel;
    private TextField searchField;
    private ComboBox<String> statusFilter;
    private ComboBox<String> categoryFilter;
    private Label totalActiveLabel;
    private Label filingTodayLabel;
    private Label adjournedLabel;
    private Label showingLabel;
    private HBox paginationBox;
    private Button prevBtn, nextBtn;
    private VBox filterPanel;
    private boolean filterVisible = false;
    private int currentPage = 0;
    private final int pageSize = 15;
    private int totalCount = 0;
    private final Map<String, List<CourtCase>> pageCache = new LinkedHashMap<>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<CourtCase>> eldest) {
            return size() > 5;
        }
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public CaseListView(Consumer<CourtCase> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(0);
        root.setPadding(new Insets(24, 40, 24, 40));
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        buildUI();
        loadPage();
        loadStats();
    }

    public void refresh() {
        currentPage = 0;
        pageCache.clear();
        loadPage();
        loadStats();
    }

    private void loadStats() {
        caseRepo.countActive(count -> Platform.runLater(() ->
            totalActiveLabel.setText(String.format("%,d", count))));
        caseRepo.countFiledToday(count -> Platform.runLater(() ->
            filingTodayLabel.setText(String.format("%,d", count))));
        caseRepo.countByStatusAndCategory("ADJOURNED", "All", count -> Platform.runLater(() ->
            adjournedLabel.setText(String.format("%,d", count))));
    }

    private String buildCacheKey(int page) {
        String status   = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query    = searchField.getText();
        return status + "|" + category + "|" + (query != null ? query : "") + "|" + page;
    }

    private void loadPage() {
        String status   = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query    = searchField.getText();
        String cacheKey = buildCacheKey(currentPage);
        int offset      = currentPage * pageSize;

        List<CourtCase> cached = pageCache.get(cacheKey);
        if (cached != null) {
            caseList.setAll(cached);
            updatePlaceholder(query != null && !query.isEmpty() ? "No matching cases found" : "No cases found");
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
                    updatePlaceholder(count == 0 ? "No matching cases found" : null);
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
                        updatePlaceholder(count == 0 ? "No cases found" : null);
                        updatePaginationControls();
                    });
                    preloadNextPage();
                });
            });
        }
    }

    private void updatePlaceholder(String message) {
        if (placeholderLabel != null) {
            if (message == null) {
                table.setPlaceholder(null);
            } else {
                placeholderLabel.setText(message);
                table.setPlaceholder(placeholderLabel);
            }
        }
    }

    private void preloadNextPage() {
        String nextKey = buildCacheKey(currentPage + 1);
        if (pageCache.containsKey(nextKey)) return;
        String status   = statusFilter.getValue();
        String category = categoryFilter.getValue();
        String query    = searchField.getText();
        int nextOffset  = (currentPage + 1) * pageSize;

        if (query != null && !query.isEmpty()) {
            caseRepo.search(query, cases -> {
                if (cases.size() > nextOffset)
                    pageCache.put(nextKey, cases.stream().skip(nextOffset).limit(pageSize).toList());
            });
        } else {
            caseRepo.getAllPaginated(status, category, nextOffset, pageSize, cases -> {
                if (!cases.isEmpty()) pageCache.put(nextKey, cases);
            });
        }
    }

    private void updatePaginationControls() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int offset = currentPage * pageSize;
        int from   = totalCount == 0 ? 0 : offset + 1;
        int to     = Math.min(offset + pageSize, totalCount);
        showingLabel.setText(String.format("Showing %d to %d of %,d results", from, to, totalCount));
        rebuildPaginationButtons(Math.max(1, totalPages));
    }

    private void rebuildPaginationButtons(int totalPages) {
        paginationBox.getChildren().clear();
        String btnBase   = "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 5 10; -fx-font-size: 12;";
        String btnNormal = btnBase + "-fx-background-color: transparent;";
        String btnActive = btnBase + "-fx-background-color: " + tm.accentBlue() + "; -fx-text-fill: white;";

        prevBtn.setDisable(currentPage == 0);
        nextBtn.setDisable(currentPage >= totalPages - 1 || totalCount == 0);
        paginationBox.getChildren().add(prevBtn);

        int maxVisible = 7, half = maxVisible / 2;
        int start, end;
        if (totalPages <= maxVisible) { start = 0; end = totalPages; }
        else if (currentPage <= half) { start = 0; end = maxVisible - 1; }
        else if (currentPage >= totalPages - half - 1) { start = totalPages - maxVisible + 1; end = totalPages; }
        else { start = currentPage - half + 1; end = currentPage + half; }

        if (start > 0) {
            paginationBox.getChildren().add(pageBtn("1", btnNormal, btnActive, 0, currentPage == 0));
            if (start > 1) paginationBox.getChildren().add(dotsLabel());
        }
        for (int i = start; i < end && i < totalPages; i++)
            paginationBox.getChildren().add(pageBtn(String.valueOf(i + 1), btnNormal, btnActive, i, i == currentPage));
        if (end < totalPages) {
            if (end < totalPages - 1) paginationBox.getChildren().add(dotsLabel());
            paginationBox.getChildren().add(pageBtn(String.valueOf(totalPages), btnNormal, btnActive,
                totalPages - 1, currentPage == totalPages - 1));
        }
        paginationBox.getChildren().add(nextBtn);
    }

    private Label dotsLabel() {
        Label l = new Label("\u2026");
        l.setStyle("-fx-padding: 5 4;");
        return l;
    }

    private Button pageBtn(String text, String normalStyle, String activeStyle, int page, boolean isActive) {
        Button btn = new Button(text);
        btn.setStyle(isActive ? activeStyle : normalStyle);
        btn.setOnAction(e -> { currentPage = page; loadPage(); });
        if (!isActive) {
            btn.setOnMouseEntered(ev -> btn.setStyle(normalStyle + "-fx-background-color: " + tm.accentBlue() + "22;"));
            btn.setOnMouseExited(ev -> btn.setStyle(normalStyle));
        }
        return btn;
    }

    private VBox makeStatCard(String labelText, Label valueLabel, String cardStyle) {
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.getStyleClass().add("text-muted");
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 26));
        VBox card = new VBox(4, lbl, valueLabel);
        card.setPadding(new Insets(12, 24, 12, 24));
        card.setStyle(cardStyle);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void buildUI() {
        boolean dark = tm.isDark();
        String cardBg     = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String cardStyle  = "-fx-background-color: " + cardBg + "; -fx-border-color: " + cardBorder +
                            "; -fx-border-radius: 8; -fx-background-radius: 8;";

        // ── Header row ─────────────────────────────────────────────────────────
        Label pageTitle = new Label("Case Management");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 40));

        Label pageSubtitle = new Label("A comprehensive registry of all court cases.");
        pageSubtitle.getStyleClass().add("text-muted");
        pageSubtitle.setFont(Font.font("System", 14));

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        FontIcon addIcon = new FontIcon(Feather.PLUS);
        addIcon.setIconSize(14);
        Button addBtn = new Button("+ CREATE NEW CASE FILING");
        addBtn.setGraphic(addIcon);
        addBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: " + (dark ? "#ffffff60" : "#00000060") + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 18; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 12;"
        );
        addBtn.setOnMouseEntered(e -> addBtn.setStyle(
            "-fx-background-color: " + (dark ? "#ffffff14" : "#00000014") + ";" +
            "-fx-border-color: " + (dark ? "#ffffff80" : "#00000080") + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 18; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 12;"
        ));
        addBtn.setOnMouseExited(e -> addBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: " + (dark ? "#ffffff60" : "#00000060") + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 18; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 12;"
        ));
        addBtn.setOnAction(e -> handleAdd());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox headerRow = new HBox(titleBox, headerSpacer, addBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(headerRow, new Insets(0, 0, 16, 0));

        // ── Stats row ──────────────────────────────────────────────────────────
        totalActiveLabel = new Label("\u2014");
        filingTodayLabel = new Label("\u2014");
        adjournedLabel   = new Label("\u2014");

        HBox statsRow = new HBox(14,
            makeStatCard("TOTAL ACTIVE",   totalActiveLabel, cardStyle),
            makeStatCard("FILED TODAY",    filingTodayLabel, cardStyle),
            makeStatCard("ADJOURNED",      adjournedLabel,   cardStyle)
        );
        VBox.setMargin(statsRow, new Insets(0, 0, 16, 0));

        // ── Search row ─────────────────────────────────────────────────────────
        FontIcon searchIcon = new FontIcon(Feather.SEARCH);
        searchIcon.setIconSize(15);
        searchIcon.getStyleClass().add("text-muted");

        searchField = new TextField();
        searchField.setPromptText("Search by case ID, party, or counsel...");
        searchField.setStyle("-fx-padding: 10 12 10 38; -fx-background-color: transparent; -fx-border-color: transparent;");
        searchField.textProperty().addListener((obs, o, n) -> { pageCache.clear(); currentPage = 0; loadPage(); });
        HBox.setHgrow(searchField, Priority.ALWAYS);

        StackPane searchStack = new StackPane(searchField, searchIcon);
        StackPane.setAlignment(searchIcon, Pos.CENTER_LEFT);
        StackPane.setMargin(searchIcon, new Insets(0, 0, 0, 12));
        HBox.setHgrow(searchStack, Priority.ALWAYS);
        searchStack.setStyle(
            "-fx-background-color: " + (dark ? "#ffffff08" : "#00000008") + ";" +
            "-fx-border-color: " + cardBorder + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;"
        );

        FontIcon refineIcon = new FontIcon(Feather.SLIDERS);
        refineIcon.setIconSize(13);
        Button refineBtn = new Button("Refine Search");
        refineBtn.setGraphic(refineIcon);
        refineBtn.setStyle(
            "-fx-background-color: " + (dark ? "#ffffff0a" : "#00000008") + ";" +
            "-fx-border-color: " + cardBorder + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 16; -fx-cursor: hand;"
        );
        refineBtn.setOnAction(e -> toggleFilterPanel(refineBtn, cardBorder, dark));

        HBox searchRow = new HBox(12, searchStack, refineBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setPadding(new Insets(10, 16, 10, 16));

        // ── Collapsible filter panel ───────────────────────────────────────────
        Label catLabel = new Label("CATEGORY");
        catLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        catLabel.getStyleClass().add("text-muted");
        categoryFilter = new ComboBox<>(FXCollections.observableArrayList(
            "All", "Civil", "Criminal", "Succession", "Children", "Traffic", "Other"));
        categoryFilter.setValue("All");
        categoryFilter.setPrefWidth(160);
        categoryFilter.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox catBox = new VBox(4, catLabel, categoryFilter);

        Label statusLabel = new Label("STATUS");
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        statusLabel.getStyleClass().add("text-muted");
        statusFilter = new ComboBox<>(FXCollections.observableArrayList(
            "All", "OPEN", "CLOSED", "ADJOURNED", "DISMISSED", "SETTLED"));
        statusFilter.setValue("All");
        statusFilter.setPrefWidth(160);
        statusFilter.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox statusBox = new VBox(4, statusLabel, statusFilter);

        filterPanel = new VBox(catBox, statusBox);
        filterPanel.setStyle("-fx-padding: 0 16 12 16; -fx-spacing: 0;");
        // Lay out horizontally
        HBox filterRow = new HBox(14, catBox, statusBox);
        filterPanel = new VBox(filterRow);
        filterPanel.setPadding(new Insets(0, 16, 12, 16));
        filterPanel.setVisible(false);
        filterPanel.setManaged(false);

        // ── Table ─────────────────────────────────────────────────────────────
        table = createTable();
        caseList = FXCollections.observableArrayList();
        table.setItems(caseList);
        placeholderLabel = new Label("Loading...");
        table.setPlaceholder(placeholderLabel);
        table.setStyle("-fx-border-color: transparent;");
        VBox.setVgrow(table, Priority.ALWAYS);

        // ── Table footer ───────────────────────────────────────────────────────
        showingLabel = new Label("Loading\u2026");
        showingLabel.getStyleClass().add("text-muted");
        showingLabel.setFont(Font.font("System", 12));

        FontIcon chevLeft = new FontIcon(Feather.CHEVRON_LEFT);
        chevLeft.setIconSize(13);
        prevBtn = new Button();
        prevBtn.setGraphic(chevLeft);
        prevBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5 8; " +
            "-fx-background-radius: 4; -fx-border-color: " + cardBorder + "; -fx-border-radius: 4;");
        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; loadPage(); } });

        FontIcon chevRight = new FontIcon(Feather.CHEVRON_RIGHT);
        chevRight.setIconSize(13);
        nextBtn = new Button();
        nextBtn.setGraphic(chevRight);
        nextBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5 8; " +
            "-fx-background-radius: 4; -fx-border-color: " + cardBorder + "; -fx-border-radius: 4;");
        nextBtn.setOnAction(e -> { currentPage++; loadPage(); });

        paginationBox = new HBox(4);
        paginationBox.setAlignment(Pos.CENTER_RIGHT);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox tableFooter = new HBox(showingLabel, footerSpacer, paginationBox);
        tableFooter.setAlignment(Pos.CENTER_LEFT);
        tableFooter.setPadding(new Insets(8, 16, 8, 16));

        VBox tableContainer = new VBox(0, searchRow, filterPanel, table, new Separator(), tableFooter);
        tableContainer.setStyle(cardStyle);
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        root.getChildren().addAll(headerRow, statsRow, tableContainer);
    }

    private void toggleFilterPanel(Button refineBtn, String cardBorder, boolean dark) {
        filterVisible = !filterVisible;
        filterPanel.setVisible(filterVisible);
        filterPanel.setManaged(filterVisible);
        if (filterVisible) {
            refineBtn.setStyle(
                "-fx-background-color: " + tm.accentBlue() + "22;" +
                "-fx-border-color: " + tm.accentBlue() + ";" +
                "-fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-padding: 10 16; -fx-cursor: hand;");
        } else {
            refineBtn.setStyle(
                "-fx-background-color: " + (dark ? "#ffffff0a" : "#00000008") + ";" +
                "-fx-border-color: " + cardBorder + ";" +
                "-fx-border-radius: 6; -fx-background-radius: 6;" +
                "-fx-padding: 10 16; -fx-cursor: hand;");
        }
    }

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createTable() {
        TableView<CourtCase> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // CASE ID
        TableColumn<CourtCase, String> caseIdCol = new TableColumn<>("CASE NUMBER");
        caseIdCol.setPrefWidth(140);
        caseIdCol.setMinWidth(110);
        caseIdCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseNumber() != null ? cd.getValue().getCaseNumber() : ""));
        caseIdCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); } else {
                    setText(item);
                    setFont(Font.font("System", FontWeight.BOLD, 13));
                }
            }
        });

        // PRINCIPAL PARTY / COUNSEL
        TableColumn<CourtCase, CourtCase> partyCol = new TableColumn<>("TITLE");
        partyCol.setPrefWidth(260);
        partyCol.setMinWidth(180);
        partyCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        partyCol.setCellFactory(col -> new TableCell<>() {
            private final Label titleLbl  = new Label();
            private final Label counselLbl = new Label();
            private final VBox box = new VBox(1, titleLbl, counselLbl);
            {
                titleLbl.setFont(Font.font("System", FontWeight.MEDIUM, 13));
                counselLbl.setFont(Font.font("System", 11));
                counselLbl.getStyleClass().add("text-muted");
            }
            @Override protected void updateItem(CourtCase c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setGraphic(null); } else {
                    titleLbl.setText(c.getCaseTitle() != null && !c.getCaseTitle().isBlank()
                        ? c.getCaseTitle() : c.getCaseNumber());
                    String counsel = c.getProsecutionCounsel();
                    boolean hasCounsel = counsel != null && !counsel.isBlank();
                    counselLbl.setText(hasCounsel ? counsel : "");
                    counselLbl.setVisible(hasCounsel);
                    counselLbl.setManaged(hasCounsel);
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        // CATEGORY
        TableColumn<CourtCase, String> catCol = new TableColumn<>("CATEGORY");
        catCol.setPrefWidth(120);
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

        // FILING DATE
        TableColumn<CourtCase, String> dateCol = new TableColumn<>("FILING DATE");
        dateCol.setPrefWidth(120);
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getFilingDate() != null ? cd.getValue().getFilingDate().format(DATE_FMT) : ""));

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

        // ACTIONS
        TableColumn<CourtCase, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(100);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = iconBtn(Feather.EYE,     "View case",   tm.accentBlue());
            private final Button editBtn   = iconBtn(Feather.EDIT_2,  "Edit case",   tm.accentGreen());
            private final Button deleteBtn = iconBtn(Feather.TRASH_2, "Delete case", tm.accentRed());
            private final HBox box = new HBox(2, viewBtn, editBtn, deleteBtn);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.setOnAction(e   -> handleView(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e   -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(caseIdCol, partyCol, catCol, dateCol, statusCol, actionsCol);
        tv.setPlaceholder(new Label("No cases found"));
        String altBg = tm.isDark() ? "#ffffff05" : "#00000005";
        tv.setRowFactory(tv2 -> new TableRow<CourtCase>() {{
            indexProperty().addListener((obs, ov, nv) ->
                setStyle(nv.intValue() % 2 != 0 ? "-fx-background-color: " + altBg + ";" : ""));
        }});
        return tv;
    }

    private String categoryBg(String cat) {
        return switch (cat) {
            case "Criminal"   -> tm.badgeCriminalBg();
            case "Traffic"    -> tm.badgeTrafficBg();
            case "Civil"      -> tm.badgeCivilBg();
            case "Succession" -> tm.badgeSuccessionBg();
            case "Children"   -> tm.badgeChildrenBg();
            default           -> tm.badgeOtherBg();
        };
    }

    private String categoryText(String cat) {
        return switch (cat) {
            case "Criminal"   -> tm.badgeCriminalText();
            case "Traffic"    -> tm.badgeTrafficText();
            case "Civil"      -> tm.badgeCivilText();
            case "Succession" -> tm.badgeSuccessionText();
            case "Children"   -> tm.badgeChildrenText();
            default           -> tm.badgeOtherText();
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

    private void refreshTable() {
        String query    = searchField.getText().trim();
        String status   = statusFilter.getValue();
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
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        btn.setOnMouseEntered(e -> btn.setStyle(String.format(
            "-fx-background-color: %s18; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;", color)));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;"));
        return btn;
    }

    private void handleAdd() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        while (true) {
            Optional<CourtCase> result = dialog.showAndWait();
            if (result.isEmpty()) break;
            CourtCase c = result.get();
            List<CaseFormDialog.ParticipantEntry> participants = dialog.getParticipantsToCreate();
            caseRepo.save(c, null, () -> {
                com.courttrack.model.Charge charge = new com.courttrack.model.Charge();
                charge.setCaseId(c.getCaseId());
                charge.setParticulars(c.getChargeParticulars());
                charge.setPlea(c.getChargePlea());
                charge.setVerdict(c.getChargeVerdict());
                caseRepo.saveCharge(charge, () -> {
                    saveParticipants(c, participants);
                    refreshTable();
                });
            });
            if (!dialog.isAddAnother()) break;
            dialog = new CaseFormDialog(null);
        }
    }

    private void saveParticipants(CourtCase c, List<CaseFormDialog.ParticipantEntry> entries) {
        if (entries.isEmpty()) return;
        PersonDao personDao = new PersonDao();
        CaseDao caseDao = new CaseDao();
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

    private void handleView(CourtCase c) {
        if (onViewDetail != null) onViewDetail.accept(c);
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
