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

import java.time.YearMonth;
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
    private Label showingLabel;
    private HBox paginationBox;
    private Button prevBtn, nextBtn;
    private Label pageLabel;
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
        long t0 = System.currentTimeMillis();
        this.onViewDetail = onViewDetail;
        root = new VBox(0);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
        loadPage();
        loadStats();
        System.out.println("[DEBUG] CaseListView constructor TOTAL: " + (System.currentTimeMillis() - t0) + "ms");
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
        int offset = currentPage * pageSize;
        int from = totalCount == 0 ? 0 : offset + 1;
        int to = Math.min(offset + pageSize, totalCount);
        showingLabel.setText(String.format("Showing %d\u2013%d of %,d records", from, to, totalCount));
        rebuildPaginationButtons(Math.max(1, totalPages));
    }

    private void rebuildPaginationButtons(int totalPages) {
        paginationBox.getChildren().clear();

        String btnBase = "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 10; -fx-font-size: 12;";
        String btnNormal = btnBase + "-fx-background-color: transparent;";
        String btnActiveStyle = btnBase + "-fx-background-color: " + tm.accentBlue() + "; -fx-text-fill: white;";

        prevBtn.setDisable(currentPage == 0);
        nextBtn.setDisable(currentPage >= totalPages - 1 || totalCount == 0);

        paginationBox.getChildren().add(prevBtn);

        int maxVisible = 7;
        int half = maxVisible / 2;
        int start, end;
        if (totalPages <= maxVisible) {
            start = 0;
            end = totalPages;
        } else if (currentPage <= half) {
            start = 0;
            end = maxVisible - 1;
        } else if (currentPage >= totalPages - half - 1) {
            start = totalPages - maxVisible + 1;
            end = totalPages;
        } else {
            start = currentPage - half + 1;
            end = currentPage + half;
        }

        if (start > 0) {
            paginationBox.getChildren().add(pageBtn("1", btnNormal, btnActiveStyle, 0, currentPage == 0));
            if (start > 1) {
                Label dots = new Label("\u2026");
                dots.setStyle("-fx-padding: 4 4;");
                paginationBox.getChildren().add(dots);
            }
        }
        for (int i = start; i < end && i < totalPages; i++) {
            paginationBox.getChildren().add(pageBtn(String.valueOf(i + 1), btnNormal, btnActiveStyle, i, i == currentPage));
        }
        if (end < totalPages) {
            if (end < totalPages - 1) {
                Label dots = new Label("\u2026");
                dots.setStyle("-fx-padding: 4 4;");
                paginationBox.getChildren().add(dots);
            }
            paginationBox.getChildren().add(pageBtn(String.valueOf(totalPages), btnNormal, btnActiveStyle,
                totalPages - 1, currentPage == totalPages - 1));
        }

        paginationBox.getChildren().add(nextBtn);
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

    private VBox makeStatCard(String labelText, Label valueLabel) {
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        lbl.getStyleClass().add("text-muted");

        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 28));

        VBox card = new VBox(4, lbl, valueLabel);
        card.setPadding(new Insets(14, 24, 14, 24));
        card.setStyle(
            "-fx-background-radius: 6;" +
            "-fx-background-color: " + (tm.isDark() ? "#ffffff0a" : "#00000008") + ";" +
            "-fx-border-color: " + (tm.isDark() ? "#ffffff14" : "#00000014") + ";" +
            "-fx-border-radius: 6;"
        );
        return card;
    }

    private void buildUI() {
        // ── Title ──────────────────────────────────────────────────────────────
        Label pageTitle = new Label("Case Management");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 26));
        VBox.setMargin(pageTitle, new Insets(0, 0, 20, 0));

        // ── Filter row ─────────────────────────────────────────────────────────
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

        FontIcon filterIcon = new FontIcon(Feather.SLIDERS);
        filterIcon.setIconSize(13);
        Button refineBtn = new Button("REFINE SEARCH");
        refineBtn.setGraphic(filterIcon);
        refineBtn.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });
        // Align button baseline with dropdowns
        VBox refineBtnWrapper = new VBox(refineBtn);
        refineBtnWrapper.setAlignment(Pos.BOTTOM_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search by ID, Party, or Counsel...");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, o, n) -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox searchWrapper = new VBox(searchField);
        searchWrapper.setAlignment(Pos.BOTTOM_LEFT);

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        HBox filterRow = new HBox(12, catBox, statusBox, refineBtnWrapper, filterSpacer, searchWrapper);
        filterRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setMargin(filterRow, new Insets(0, 0, 20, 0));

        // ── Stats cards ────────────────────────────────────────────────────────
        totalActiveLabel = new Label("\u2014");
        filingTodayLabel = new Label("\u2014");
        VBox activeCard = makeStatCard("TOTAL ACTIVE", totalActiveLabel);
        VBox todayCard = makeStatCard("FILING TODAY", filingTodayLabel);
        HBox statsRow = new HBox(12, activeCard, todayCard);
        VBox.setMargin(statsRow, new Insets(0, 0, 24, 0));

        // ── Archive record header ──────────────────────────────────────────────
        YearMonth ym = YearMonth.now();
        Label archiveLabel = new Label("Archive Record:  " + ym.getYear() + "/" + String.format("%02d", ym.getMonthValue()));
        archiveLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        showingLabel = new Label("Loading\u2026");
        showingLabel.getStyleClass().add("text-muted");
        showingLabel.setFont(Font.font("System", 12));

        Region archiveSpacer = new Region();
        HBox.setHgrow(archiveSpacer, Priority.ALWAYS);
        HBox archiveRow = new HBox(archiveLabel, archiveSpacer, showingLabel);
        archiveRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(archiveRow, new Insets(0, 0, 10, 0));

        // ── Table ─────────────────────────────────────────────────────────────
        table = createTable();
        caseList = FXCollections.observableArrayList();
        table.setItems(caseList);
        placeholderLabel = new Label("Loading...");
        table.setPlaceholder(placeholderLabel);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setMargin(table, new Insets(0, 0, 16, 0));

        // ── Footer: pagination + add button ───────────────────────────────────
        FontIcon chevLeft = new FontIcon(Feather.CHEVRON_LEFT);
        chevLeft.setIconSize(13);
        prevBtn = new Button();
        prevBtn.setGraphic(chevLeft);
        prevBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; loadPage(); } });

        FontIcon chevRight = new FontIcon(Feather.CHEVRON_RIGHT);
        chevRight.setIconSize(13);
        nextBtn = new Button();
        nextBtn.setGraphic(chevRight);
        nextBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4 8; -fx-background-radius: 4;");
        nextBtn.setOnAction(e -> { currentPage++; loadPage(); });

        pageLabel = new Label();
        pageLabel.setVisible(false);
        pageLabel.setManaged(false);

        paginationBox = new HBox(4);
        paginationBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon plusIcon = new FontIcon(Feather.PLUS);
        plusIcon.setIconSize(14);
        Button addBtn = new Button("  CREATE NEW CASE FILING");
        addBtn.setGraphic(plusIcon);
        addBtn.getStyleClass().add("accent");
        addBtn.setStyle("-fx-padding: 10 20; -fx-font-weight: bold; -fx-background-radius: 6;");
        addBtn.setOnAction(e -> handleAdd());

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(paginationBox, footerSpacer, addBtn);
        footer.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(pageTitle, filterRow, statsRow, archiveRow, table, footer);
    }

    @SuppressWarnings("unchecked")
    private TableView<CourtCase> createTable() {
        TableView<CourtCase> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // CASE ID
        TableColumn<CourtCase, String> caseIdCol = new TableColumn<>("CASE ID");
        caseIdCol.setPrefWidth(140);
        caseIdCol.setMinWidth(110);
        caseIdCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseNumber() != null ? cd.getValue().getCaseNumber() : ""
        ));
        caseIdCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                } else {
                    setText(item);
                    setFont(Font.font("System", FontWeight.BOLD, 12));
                }
            }
        });

        // PRINCIPAL PARTY / COUNSEL
        TableColumn<CourtCase, CourtCase> partyCol = new TableColumn<>("PRINCIPAL PARTY / COUNSEL");
        partyCol.setPrefWidth(260);
        partyCol.setMinWidth(180);
        partyCol.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        partyCol.setCellFactory(col -> new TableCell<>() {
            private final Label titleLbl = new Label();
            private final Label counselLbl = new Label();
            private final VBox box = new VBox(1, titleLbl, counselLbl);
            {
                titleLbl.setFont(Font.font("System", FontWeight.MEDIUM, 13));
                counselLbl.setFont(Font.font("System", 11));
                counselLbl.getStyleClass().add("text-muted");
            }
            @Override
            protected void updateItem(CourtCase c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                } else {
                    titleLbl.setText(c.getCaseTitle() != null && !c.getCaseTitle().isBlank()
                        ? c.getCaseTitle() : c.getCaseNumber());
                    String counsel = c.getProsecutionCounsel();
                    if (counsel != null && !counsel.isBlank()) {
                        counselLbl.setText(counsel);
                        counselLbl.setVisible(true);
                        counselLbl.setManaged(true);
                    } else {
                        counselLbl.setVisible(false);
                        counselLbl.setManaged(false);
                    }
                    setGraphic(box);
                    setText(null);
                }
            }
        });

        // CATEGORY badge
        TableColumn<CourtCase, String> catCol = new TableColumn<>("CATEGORY");
        catCol.setPrefWidth(120);
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseCategory() != null ? cd.getValue().getCaseCategory() : ""
        ));
        catCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setPadding(new Insets(3, 10, 3, 10));
                badge.setFont(Font.font("System", 11));
            }
            @Override
            protected void updateItem(String cat, boolean empty) {
                super.updateItem(cat, empty);
                if (empty || cat == null || cat.isBlank()) {
                    setGraphic(null);
                } else {
                    String color = switch (cat) {
                        case "Criminal"   -> tm.badgeCriminalText();
                        case "Traffic"    -> tm.badgeTrafficText();
                        case "Civil"      -> tm.badgeCivilText();
                        case "Succession" -> tm.badgeSuccessionText();
                        case "Children"   -> tm.badgeChildrenText();
                        default           -> tm.badgeOtherText();
                    };
                    String bg = switch (cat) {
                        case "Criminal"   -> tm.badgeCriminalBg();
                        case "Traffic"    -> tm.badgeTrafficBg();
                        case "Civil"      -> tm.badgeCivilBg();
                        case "Succession" -> tm.badgeSuccessionBg();
                        case "Children"   -> tm.badgeChildrenBg();
                        default           -> tm.badgeOtherBg();
                    };
                    badge.setText(cat);
                    badge.setStyle(String.format(
                        "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;", bg, color));
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // FILING DATE
        TableColumn<CourtCase, String> dateCol = new TableColumn<>("FILING DATE");
        dateCol.setPrefWidth(120);
        dateCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getFilingDate() != null ? cd.getValue().getFilingDate().format(DATE_FMT) : ""
        ));

        // STATUS badge
        TableColumn<CourtCase, String> statusCol = new TableColumn<>("STATUS");
        statusCol.setPrefWidth(100);
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getCaseStatus() != null ? cd.getValue().getCaseStatus() : ""
        ));
        statusCol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setPadding(new Insets(3, 10, 3, 10));
                badge.setFont(Font.font("System", 11));
            }
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setGraphic(null);
                } else {
                    badge.setText(status);
                    String style = switch (status) {
                        case "OPEN" -> String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                            tm.badgeOpenBg(), tm.badgeOpenText());
                        case "CLOSED" -> String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                            tm.badgeClosedBg(), tm.badgeClosedText());
                        case "ADJOURNED" -> String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                            tm.badgeTrafficBg(), tm.badgeTrafficText());
                        case "DISMISSED" -> String.format(
                            "-fx-background-color: %s33; -fx-text-fill: %s; -fx-background-radius: 4;",
                            tm.accentRed(), tm.accentRed());
                        case "SETTLED" -> String.format(
                            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                            tm.badgeCivilBg(), tm.badgeCivilText());
                        default -> "-fx-background-color: #66666633; -fx-text-fill: #666666; -fx-background-radius: 4;";
                    };
                    badge.setStyle(style);
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // ACTIONS
        TableColumn<CourtCase, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(100);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = iconBtn(Feather.EYE,    "View case",   tm.accentBlue());
            private final Button editBtn   = iconBtn(Feather.EDIT_2, "Edit case",   tm.accentGreen());
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
