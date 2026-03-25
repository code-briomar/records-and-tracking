package com.courttrack.ui;

import java.time.YearMonth;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private ComboBox<String> genderFilter;
    private Label totalPersonsLabel;
    private Label newThisMonthLabel;
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

    public OffenderListView(Consumer<Person> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(0);
        root.setPadding(new Insets(32, 40, 32, 40));
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
            totalPersonsLabel.setText(String.format("%,d", count))));
        personRepo.countAddedThisMonth(count -> Platform.runLater(() ->
            newThisMonthLabel.setText(String.format("%,d", count))));
    }

    private String buildCacheKey(int page) {
        String gender = genderFilter.getValue();
        String query = searchField.getText();
        return (gender != null ? gender : "All") + "|" + (query != null ? query : "") + "|" + page;
    }

    private void loadPage() {
        String gender = genderFilter.getValue();
        String query = searchField.getText();
        String cacheKey = buildCacheKey(currentPage);
        int offset = currentPage * pageSize;

        List<Person> cached = pageCache.get(cacheKey);
        if (cached != null) {
            personList.setAll(cached);
            updatePlaceholder(query != null && !query.isEmpty() ? "No matching persons found" : "No persons in the system");
            personRepo.countFiltered(gender, count -> Platform.runLater(() -> {
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
            personRepo.getAllPaginatedFiltered(gender, offset, pageSize, persons -> {
                pageCache.put(buildCacheKey(currentPage), persons);
                personRepo.countFiltered(gender, count -> {
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

        String gender = genderFilter.getValue();
        String query = searchField.getText();
        int nextOffset = (currentPage + 1) * pageSize;

        if (query != null && !query.isEmpty()) {
            personRepo.search(query, persons -> {
                if (persons.size() > nextOffset) {
                    List<Person> paged = persons.stream().skip(nextOffset).limit(pageSize).toList();
                    pageCache.put(nextKey, paged);
                }
            });
        } else {
            personRepo.getAllPaginatedFiltered(gender, nextOffset, pageSize, persons -> {
                if (!persons.isEmpty()) {
                    pageCache.put(nextKey, persons);
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
        Label pageTitle = new Label("Persons");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 26));
        VBox.setMargin(pageTitle, new Insets(0, 0, 20, 0));

        // ── Filter row ─────────────────────────────────────────────────────────
        Label genderLabel = new Label("GENDER");
        genderLabel.setFont(Font.font("System", FontWeight.BOLD, 10));
        genderLabel.getStyleClass().add("text-muted");
        genderFilter = new ComboBox<>(FXCollections.observableArrayList("All", "Male", "Female", "Other"));
        genderFilter.setValue("All");
        genderFilter.setPrefWidth(140);
        genderFilter.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox genderBox = new VBox(4, genderLabel, genderFilter);

        FontIcon filterIcon = new FontIcon(Feather.SLIDERS);
        filterIcon.setIconSize(13);
        Button refineBtn = new Button("REFINE SEARCH");
        refineBtn.setGraphic(filterIcon);
        refineBtn.setOnAction(e -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox refineBtnWrapper = new VBox(refineBtn);
        refineBtnWrapper.setAlignment(Pos.BOTTOM_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search by name or national ID...");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, o, n) -> { pageCache.clear(); currentPage = 0; loadPage(); });
        VBox searchWrapper = new VBox(searchField);
        searchWrapper.setAlignment(Pos.BOTTOM_LEFT);

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);
        HBox filterRow = new HBox(12, genderBox, refineBtnWrapper, filterSpacer, searchWrapper);
        filterRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox.setMargin(filterRow, new Insets(0, 0, 20, 0));

        // ── Stats cards ────────────────────────────────────────────────────────
        totalPersonsLabel = new Label("\u2014");
        newThisMonthLabel = new Label("\u2014");
        VBox totalCard = makeStatCard("TOTAL PERSONS", totalPersonsLabel);
        VBox monthCard = makeStatCard("NEW THIS MONTH", newThisMonthLabel);
        HBox statsRow = new HBox(12, totalCard, monthCard);
        VBox.setMargin(statsRow, new Insets(0, 0, 24, 0));

        // ── Record header ──────────────────────────────────────────────────────
        YearMonth ym = YearMonth.now();
        Label archiveLabel = new Label("Person Records:  " + ym.getYear() + "/" + String.format("%02d", ym.getMonthValue()));
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
        personList = FXCollections.observableArrayList();
        table.setItems(personList);
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

        paginationBox = new HBox(4);
        paginationBox.setAlignment(Pos.CENTER_LEFT);

        FontIcon plusIcon = new FontIcon(Feather.PLUS);
        plusIcon.setIconSize(14);
        Button addBtn = new Button("  ADD NEW PERSON");
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
    private TableView<Person> createTable() {
        TableView<Person> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Person, String> nameCol = new TableColumn<>("NAME");
        nameCol.setPrefWidth(200);
        nameCol.setMinWidth(140);
        nameCol.setCellValueFactory(cd -> {
            Person p = cd.getValue();
            String name = "";
            if (p.getFirstName() != null) name += p.getFirstName();
            if (p.getLastName() != null) name += (name.isEmpty() ? "" : " ") + p.getLastName();
            return new SimpleStringProperty(name);
        });
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); } else {
                    setText(item);
                    setFont(Font.font("System", FontWeight.MEDIUM, 13));
                }
            }
        });

        TableColumn<Person, String> idCol = new TableColumn<>("NATIONAL ID");
        idCol.setPrefWidth(140);
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getNationalId() != null ? cd.getValue().getNationalId() : ""));

        TableColumn<Person, String> genderCol = new TableColumn<>("GENDER");
        genderCol.setPrefWidth(100);
        genderCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getGender() != null ? cd.getValue().getGender() : ""));

        TableColumn<Person, String> dobCol = new TableColumn<>("DATE OF BIRTH");
        dobCol.setPrefWidth(130);
        dobCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getDob() != null ? cd.getValue().getDob().toString() : ""));

        TableColumn<Person, String> phoneCol = new TableColumn<>("PHONE");
        phoneCol.setPrefWidth(130);
        phoneCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getPhoneNumber() != null ? cd.getValue().getPhoneNumber() : ""));

        TableColumn<Person, Void> actionsCol = new TableColumn<>("ACTIONS");
        actionsCol.setPrefWidth(100);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = iconBtn(Feather.EYE, "View person", tm.accentBlue());
            private final Button editBtn = iconBtn(Feather.EDIT_2, "Edit person", tm.accentGreen());
            private final Button deleteBtn = iconBtn(Feather.TRASH_2, "Delete person", tm.accentRed());
            private final HBox box = new HBox(2, viewBtn, editBtn, deleteBtn);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                viewBtn.setOnAction(e -> handleView(getTableView().getItems().get(getIndex())));
                editBtn.setOnAction(e -> handleEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> handleDelete(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        tv.getColumns().addAll(nameCol, idCol, genderCol, dobCol, phoneCol, actionsCol);
        tv.setPlaceholder(new Label("No persons found"));
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
                    CaseDao caseDao = new CaseDao();
                    caseDao.addParticipant(cp);
                }
                Platform.runLater(this::refreshTable);
            });
        });
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
        if (onViewDetail != null) {
            onViewDetail.accept(p);
        }
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
