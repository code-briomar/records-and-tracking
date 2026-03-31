package com.courttrack.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.Person;
import com.courttrack.repository.PersonRepository;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;

public class OffenderListView {
    private final VBox root;
    private final PersonRepository personRepo = PersonRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<Person> onViewDetail;
    private TableView<Person> table;
    private ObservableList<Person> personList;
    private Label placeholderLabel;
    private TextField searchField;
    private Label totalOffendersLabel;
    private Label addedThisWeekLabel;
    private Label addedThisWeekPctLabel;
    private Label showingLabel;
    private HBox paginationBox;
    private Button prevBtn, nextBtn;
    private int currentPage = 0;
    private final int pageSize = 15;
    private int totalCount = 0;
    private final Map<String, List<Person>> pageCache = new LinkedHashMap<>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Person>> eldest) {
            return size() > 5;
        }
    };

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public OffenderListView(Consumer<Person> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(0);
        root.setPadding(new Insets(32, 40, 32, 40));
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
        personRepo.countAll(count -> Platform.runLater(() ->
            totalOffendersLabel.setText(String.format("%,d", count))));

        personRepo.countAddedThisWeek(thisWeek ->
            personRepo.countAddedLastWeek(lastWeek -> Platform.runLater(() -> {
                addedThisWeekLabel.setText(String.format("%,d", thisWeek));
                if (lastWeek > 0) {
                    int pct = (int) Math.round(((double)(thisWeek - lastWeek) / lastWeek) * 100);
                    String sign = pct >= 0 ? "+" : "";
                    addedThisWeekPctLabel.setText(sign + pct + "%");
                    addedThisWeekPctLabel.setVisible(true);
                    addedThisWeekPctLabel.setManaged(true);
                    String pctColor = pct >= 0 ? "#16a34a" : tm.accentRed();
                    addedThisWeekPctLabel.setStyle(String.format(
                        "-fx-background-color: %s33; -fx-text-fill: %s;" +
                        "-fx-background-radius: 4; -fx-padding: 2 6; -fx-font-size: 11;",
                        pctColor, pctColor));
                } else {
                    addedThisWeekPctLabel.setVisible(false);
                    addedThisWeekPctLabel.setManaged(false);
                }
            })));
    }

    private String buildCacheKey(int page) {
        String query = searchField.getText();
        return (query != null ? query : "") + "|" + page;
    }

    private void loadPage() {
        String query = searchField.getText();
        String cacheKey = buildCacheKey(currentPage);
        int offset = currentPage * pageSize;

        List<Person> cached = pageCache.get(cacheKey);
        if (cached != null) {
            personList.setAll(cached);
            updatePlaceholder(query != null && !query.isEmpty() ? "No matching persons found" : "No persons in the system");
            personRepo.countAll(count -> Platform.runLater(() -> {
                totalCount = count;
                updatePaginationControls();
            }));
            preloadNextPage();
            return;
        }

        if (query != null && !query.isEmpty()) {
            personRepo.search(query, persons -> {
                int count = persons.size();
                List<Person> paged = persons.stream().skip(offset).limit(pageSize).toList();
                pageCache.put(buildCacheKey(currentPage), paged);
                Platform.runLater(() -> {
                    personList.setAll(paged);
                    totalCount = count;
                    updatePlaceholder(count == 0 ? "No matching persons found" : null);
                    updatePaginationControls();
                });
                preloadNextPage();
            });
        } else {
            personRepo.getAllPaginated(offset, pageSize, persons -> {
                pageCache.put(buildCacheKey(currentPage), persons);
                personRepo.countAll(count -> {
                    Platform.runLater(() -> {
                        personList.setAll(persons);
                        totalCount = count;
                        updatePlaceholder(count == 0 ? "No persons in the system" : null);
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
        String query = searchField.getText();
        int nextOffset = (currentPage + 1) * pageSize;
        if (query != null && !query.isEmpty()) {
            personRepo.search(query, persons -> {
                if (persons.size() > nextOffset) {
                    pageCache.put(nextKey, persons.stream().skip(nextOffset).limit(pageSize).toList());
                }
            });
        } else {
            personRepo.getAllPaginated(nextOffset, pageSize, persons -> {
                if (!persons.isEmpty()) pageCache.put(nextKey, persons);
            });
        }
    }

    private void updatePaginationControls() {
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        int offset = currentPage * pageSize;
        int from = totalCount == 0 ? 0 : offset + 1;
        int to = Math.min(offset + pageSize, totalCount);
        showingLabel.setText(String.format("Showing %d to %d of %,d results", from, to, totalCount));
        rebuildPaginationButtons(Math.max(1, totalPages));
    }

    private void rebuildPaginationButtons(int totalPages) {
        paginationBox.getChildren().clear();
        String btnBase = "-fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 5 10; -fx-font-size: 12;";
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
        for (int i = start; i < end && i < totalPages; i++) {
            paginationBox.getChildren().add(pageBtn(String.valueOf(i + 1), btnNormal, btnActive, i, i == currentPage));
        }
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

    private void buildUI() {
        boolean dark = tm.isDark();
        String cardBg    = dark ? "#ffffff0a" : "#00000008";
        String cardBorder = dark ? "#ffffff18" : "#00000018";
        String cardStyle = "-fx-background-color: " + cardBg + "; -fx-border-color: " + cardBorder +
                           "; -fx-border-radius: 8; -fx-background-radius: 8;";

        // ── Header row ─────────────────────────────────────────────────────────
        Label pageTitle = new Label("Offenders");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 40));

        Label pageSubtitle = new Label("A comprehensive database of all persons in the court system.");
        pageSubtitle.getStyleClass().add("text-muted");
        pageSubtitle.setFont(Font.font("System", 14));

        VBox titleBox = new VBox(4, pageTitle, pageSubtitle);

        FontIcon addIcon = new FontIcon(Feather.USER_PLUS);
        addIcon.setIconSize(14);
        Button addBtn = new Button("+ ADD NEW OFFENDER");
        addBtn.setGraphic(addIcon);
        addBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-border-color: " + (dark ? "#ffffff60" : "#00000060") + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 18; -fx-font-weight: bold; -fx-cursor: hand;" +
            "-fx-font-size: 12;"
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
        VBox.setMargin(headerRow, new Insets(0, 0, 28, 0));

        // ── Stats row ──────────────────────────────────────────────────────────
        totalOffendersLabel = new Label("\u2014");
        totalOffendersLabel.setFont(Font.font("System", FontWeight.BOLD, 34));
        Label totalLbl = new Label("TOTAL OFFENDERS");
        totalLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        totalLbl.getStyleClass().add("text-muted");
        VBox totalCard = new VBox(6, totalLbl, totalOffendersLabel);
        totalCard.setPadding(new Insets(18, 28, 18, 28));
        totalCard.setStyle(cardStyle);
        totalCard.setMinWidth(160);

        addedThisWeekLabel = new Label("\u2014");
        addedThisWeekLabel.setFont(Font.font("System", FontWeight.BOLD, 34));
        addedThisWeekPctLabel = new Label();
        addedThisWeekPctLabel.setVisible(false);
        addedThisWeekPctLabel.setManaged(false);
        HBox weekValueRow = new HBox(10, addedThisWeekLabel, addedThisWeekPctLabel);
        weekValueRow.setAlignment(Pos.CENTER_LEFT);
        Label weekLbl = new Label("ADDED THIS WEEK");
        weekLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        weekLbl.getStyleClass().add("text-muted");
        VBox weekCard = new VBox(6, weekLbl, weekValueRow);
        weekCard.setPadding(new Insets(18, 28, 18, 28));
        weekCard.setStyle(cardStyle);
        weekCard.setMinWidth(160);

        FontIcon shieldIcon = new FontIcon(Feather.SHIELD);
        shieldIcon.setIconSize(13);
        shieldIcon.getStyleClass().add("text-muted");
        Label healthLbl = new Label("SYSTEM HEALTH");
        healthLbl.setFont(Font.font("System", FontWeight.BOLD, 10));
        healthLbl.getStyleClass().add("text-muted");
        HBox healthHeader = new HBox(6, shieldIcon, healthLbl);
        healthHeader.setAlignment(Pos.CENTER_LEFT);
        Label healthText = new Label("Records are synchronized with the court registry. All data is current and verified.");
        healthText.setWrapText(true);
        healthText.setFont(Font.font("System", 12));
        healthText.getStyleClass().add("text-muted");
        VBox healthCard = new VBox(8, healthHeader, healthText);
        healthCard.setPadding(new Insets(18, 28, 18, 28));
        healthCard.setStyle(cardStyle);
        HBox.setHgrow(healthCard, Priority.ALWAYS);

        HBox statsRow = new HBox(14, totalCard, weekCard, healthCard);
        statsRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(statsRow, new Insets(0, 0, 28, 0));

        // ── Search + table container ───────────────────────────────────────────
        FontIcon searchIcon = new FontIcon(Feather.SEARCH);
        searchIcon.setIconSize(15);
        searchIcon.getStyleClass().add("text-muted");

        searchField = new TextField();
        searchField.setPromptText("Search by name, ID number, or phone...");
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
        refineBtn.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });

        HBox searchRow = new HBox(12, searchStack, refineBtn);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setPadding(new Insets(10, 16, 10, 16));

        // ── Table ─────────────────────────────────────────────────────────────
        table = createTable();
        personList = FXCollections.observableArrayList();
        table.setItems(personList);
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
        prevBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5 8; -fx-background-radius: 4; -fx-border-color: " + cardBorder + "; -fx-border-radius: 4;");
        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; loadPage(); } });

        FontIcon chevRight = new FontIcon(Feather.CHEVRON_RIGHT);
        chevRight.setIconSize(13);
        nextBtn = new Button();
        nextBtn.setGraphic(chevRight);
        nextBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 5 8; -fx-background-radius: 4; -fx-border-color: " + cardBorder + "; -fx-border-radius: 4;");
        nextBtn.setOnAction(e -> { currentPage++; loadPage(); });

        paginationBox = new HBox(4);
        paginationBox.setAlignment(Pos.CENTER_RIGHT);

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox tableFooter = new HBox(showingLabel, footerSpacer, paginationBox);
        tableFooter.setAlignment(Pos.CENTER_LEFT);
        tableFooter.setPadding(new Insets(8, 16, 8, 16));

        Separator tableSep = new Separator();
        tableSep.setStyle("-fx-background-color: " + cardBorder + ";");

        VBox tableContainer = new VBox(0, searchRow, table, tableSep, tableFooter);
        tableContainer.setStyle(cardStyle);
        VBox.setVgrow(tableContainer, Priority.ALWAYS);
        VBox.setMargin(tableContainer, new Insets(0, 0, 28, 0));

        // ── Bottom section ─────────────────────────────────────────────────────
        FontIcon archiveShieldIcon = new FontIcon(Feather.SHIELD);
        archiveShieldIcon.setIconSize(16);
        archiveShieldIcon.setIconColor(Color.web(tm.accentGreen()));
        Label archiveTitle = new Label("Archival Integrity Notice");
        archiveTitle.setFont(Font.font("System", FontWeight.BOLD, 15));
        HBox archiveTitleRow = new HBox(8, archiveShieldIcon, archiveTitle);
        archiveTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label archiveText = new Label(
            "All offender records are maintained in accordance with court registry standards. " +
            "Modifications to existing profiles require authorisation and must be accompanied " +
            "by a formal court order. Unauthorised access to these records is logged.");
        archiveText.setWrapText(true);
        archiveText.setFont(Font.font("System", 12));
        archiveText.getStyleClass().add("text-muted");
        VBox archivalCard = new VBox(10, archiveTitleRow, archiveText);
        archivalCard.setPadding(new Insets(20, 24, 20, 24));
        archivalCard.setStyle(cardStyle);
        HBox.setHgrow(archivalCard, Priority.ALWAYS);

        FontIcon printIcon = new FontIcon(Feather.PRINTER);
        printIcon.setIconSize(22);
        printIcon.getStyleClass().add("text-muted");
        StackPane printIconBox = new StackPane(printIcon);
        printIconBox.setStyle(
            "-fx-background-color: " + (dark ? "#ffffff14" : "#00000014") + ";" +
            "-fx-background-radius: 50; -fx-padding: 12;"
        );
        Label exportTitle = new Label("EXPORT REGISTRY");
        exportTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        Label exportDesc = new Label("Generate a PDF or CSV summary of the current filtered results.");
        exportDesc.setWrapText(true);
        exportDesc.setFont(Font.font("System", 11));
        exportDesc.getStyleClass().add("text-muted");
        Button exportBtn = new Button("EXPORT DATA");
        exportBtn.setMaxWidth(Double.MAX_VALUE);
        exportBtn.setStyle(
            "-fx-background-color: " + (dark ? "#ffffff14" : "#00000014") + ";" +
            "-fx-border-color: " + cardBorder + ";" +
            "-fx-border-radius: 6; -fx-background-radius: 6;" +
            "-fx-padding: 10 16; -fx-font-weight: bold; -fx-cursor: hand;"
        );
        exportBtn.setOnAction(e -> handleExport());
        VBox exportRight = new VBox(8, exportTitle, exportDesc, exportBtn);
        exportRight.setAlignment(Pos.TOP_LEFT);

        HBox exportContent = new HBox(16, printIconBox, exportRight);
        exportContent.setAlignment(Pos.TOP_LEFT);
        VBox exportCard = new VBox(exportContent);
        exportCard.setPadding(new Insets(20, 24, 20, 24));
        exportCard.setStyle(cardStyle);
        exportCard.setMinWidth(260);
        exportCard.setMaxWidth(300);

        HBox bottomRow = new HBox(14, archivalCard, exportCard);
        bottomRow.setAlignment(Pos.TOP_LEFT);

        root.getChildren().addAll(headerRow, statsRow, tableContainer, bottomRow);
    }

    @SuppressWarnings("unchecked")
    private TableView<Person> createTable() {
        TableView<Person> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // NO.
        TableColumn<Person, String> numCol = new TableColumn<>("NO.");
        numCol.setPrefWidth(70);
        numCol.setMinWidth(60);
        numCol.setSortable(false);
        numCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    int globalIndex = currentPage * pageSize + getIndex() + 1;
                    char letter = (char)('A' + (globalIndex - 1) % 26);
                    setText(String.format("%03d-%s", globalIndex, letter));
                    setFont(Font.font("System", 11));
                    getStyleClass().add("text-muted");
                }
            }
        });

        // NAME
        TableColumn<Person, String> nameCol = new TableColumn<>("NAME");
        nameCol.setPrefWidth(180);
        nameCol.setMinWidth(130);
        nameCol.setCellValueFactory(cd -> {
            Person p = cd.getValue();
            String first = p.getFirstName() != null ? p.getFirstName() : "";
            String last = p.getLastName() != null ? p.getLastName() : "";
            return new SimpleStringProperty((first + " " + last).trim());
        });
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); } else {
                    setText(item.toUpperCase());
                    setFont(Font.font("System", FontWeight.BOLD, 13));
                }
            }
        });

        // NATIONAL ID
        TableColumn<Person, String> idCol = new TableColumn<>("NATIONAL ID");
        idCol.setPrefWidth(150);
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getNationalId() != null ? cd.getValue().getNationalId() : ""));

        // GENDER
        TableColumn<Person, String> genderCol = new TableColumn<>("GENDER");
        genderCol.setPrefWidth(90);
        genderCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getGender() != null ? cd.getValue().getGender() : ""));

        // DATE OF BIRTH
        TableColumn<Person, String> dobCol = new TableColumn<>("DATE OF BIRTH");
        dobCol.setPrefWidth(120);
        dobCol.setCellValueFactory(cd -> {
            LocalDate dob = cd.getValue().getDob();
            return new SimpleStringProperty(dob != null ? dob.format(DOB_FMT) : "");
        });

        // PHONE
        TableColumn<Person, String> phoneCol = new TableColumn<>("PHONE");
        phoneCol.setPrefWidth(140);
        phoneCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getPhoneNumber() != null ? cd.getValue().getPhoneNumber() : ""));

        // ACTIONS
        TableColumn<Person, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(100);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = iconBtn(Feather.EYE,     "View person",   tm.accentBlue());
            private final Button editBtn   = iconBtn(Feather.EDIT_2,  "Edit person",   tm.accentGreen());
            private final Button deleteBtn = iconBtn(Feather.TRASH_2, "Delete person", tm.accentRed());
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

        tv.getColumns().addAll(numCol, nameCol, idCol, genderCol, dobCol, phoneCol, actionsCol);
        tv.setPlaceholder(new Label("No persons found"));
        String altBg = tm.isDark() ? "#ffffff05" : "#00000005";
        tv.setRowFactory(tv2 -> new TableRow<Person>() {{
            indexProperty().addListener((obs, ov, nv) ->
                setStyle(nv.intValue() % 2 != 0 ? "-fx-background-color: " + altBg + ";" : ""));
        }});
        return tv;
    }

    private void refreshTable() {
        currentPage = 0;
        pageCache.clear();
        loadPage();
    }

    private void handleAdd() {
        OffenderFormDialog dialog = new OffenderFormDialog(null);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            personRepo.save(link.getPerson(), () -> {
                if (link.getCourtCase() != null && link.getCourtCase().getCaseId() != null) {
                    CaseParticipant cp = new CaseParticipant();
                    cp.setCaseId(link.getCourtCase().getCaseId());
                    cp.setPersonId(link.getPerson().getPersonId());
                    cp.setRoleType("Accused");
                    new CaseDao().addParticipant(cp);
                }
                Platform.runLater(this::refreshTable);
            });
        });
    }

    private void handleExport() {
        Dialog<Void> info = new Dialog<>();
        info.setTitle("Export Registry");
        info.setHeaderText(null);
        info.initModality(Modality.APPLICATION_MODAL);
        Label msg = new Label("Export functionality coming soon.\nThis will generate a PDF or CSV of the current records.");
        msg.setWrapText(true);
        VBox content = new VBox(msg);
        content.setPadding(new Insets(16, 24, 8, 24));
        info.getDialogPane().setContent(content);
        info.getDialogPane().getButtonTypes().add(ButtonType.OK);
        info.showAndWait();
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

    private void handleView(Person p) {
        if (onViewDetail != null) onViewDetail.accept(p);
    }

    private void handleEdit(Person p) {
        OffenderFormDialog dialog = new OffenderFormDialog(p);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> personRepo.save(link.getPerson(), this::refreshTable));
    }

    private void handleDelete(Person p) {
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

        Label titleLabel = new Label("Delete " + p.getFullName() + "?");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox titleRow = new HBox(12, warnIcon, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        String nationalId = (p.getNationalId() != null && !p.getNationalId().isBlank()) ? p.getNationalId() : "\u2014";
        Label detailLabel = new Label("This person will be soft-deleted from the system. " +
            "Their record can be restored later if needed.\n\nNational ID: " + nationalId);
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
            personRepo.delete(p.getPersonId(), this::refreshTable);
        }
    }

    public Parent getRoot() { return root; }
}
