package com.courttrack.ui;

import java.time.LocalDate;
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
import com.courttrack.repository.CaseRepository;

import javafx.application.Platform;
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
import javafx.scene.control.cell.PropertyValueFactory;
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
    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<Person> onViewDetail;
    private TableView<Person> table;
    private ObservableList<Person> personList;
    private TextField searchField;
    private ComboBox<Integer> pageSizeSelector;
    private Button prevBtn, nextBtn;
    private Label pageLabel;
    private int currentPage = 0;
    private int pageSize = 15;
    private int totalCount = 0;
    private final Map<String, List<Person>> pageCache = new LinkedHashMap<String, List<Person>>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Person>> eldest) {
            return size() > 5;
        }
    };

    public OffenderListView(Consumer<Person> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(20);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
        loadPage();
    }

    public void refresh() {
        currentPage = 0;
        pageCache.clear();
        loadPage();
    }

    private String buildCacheKey(int page) {
        String query = searchField.getText();
        return (query != null ? query : "") + "|" + page;
    }

    private void loadPage() {
        String query = searchField.getText();
        String cacheKey = buildCacheKey(currentPage);
        
        List<Person> cached = pageCache.get(cacheKey);
        if (cached != null) {
            personList.setAll(cached);
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
                List<Person> paged = persons.stream().skip(currentPage * pageSize).limit(pageSize).toList();
                pageCache.put(buildCacheKey(currentPage), paged);
                Platform.runLater(() -> {
                    personList.setAll(paged);
                    totalCount = count;
                    updatePaginationControls();
                });
                preloadNextPage();
            });
        } else {
            personRepo.getAllPaginated(currentPage * pageSize, pageSize, persons -> {
                pageCache.put(buildCacheKey(currentPage), persons);
                personRepo.countAll(count -> {
                    Platform.runLater(() -> {
                        personList.setAll(persons);
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
            personRepo.getAllPaginated(nextOffset, pageSize, persons -> {
                if (!persons.isEmpty()) {
                    pageCache.put(nextKey, persons);
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
        Label pageTitle = new Label("Offenders");
        pageTitle.setFont(Font.font("System", FontWeight.BOLD, 32));

        Label pageSubtitle = new Label("A database of all persons in the court system");
        pageSubtitle.setFont(Font.font("System", 14));
        pageSubtitle.getStyleClass().add("text-muted");

        VBox titleBox = new VBox(-2, pageTitle, pageSubtitle);

        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Filter...");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, o, n) -> { pageCache.clear(); currentPage = 0; loadPage(); });

        pageSizeSelector = new ComboBox<>(FXCollections.observableArrayList(5, 10, 15, 20, 25, 30, 40, 50, 75, 100));
        pageSizeSelector.setValue(15);
        pageSizeSelector.setOnAction(e -> {
            pageSize = pageSizeSelector.getValue();
            pageCache.clear();
            currentPage = 0;
            loadPage();
        });

        prevBtn = new Button("Prev");
        prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; loadPage(); } });
        
        pageLabel = new Label("1 / 1");
        pageLabel.setMinWidth(60);
        pageLabel.setAlignment(Pos.CENTER);
        
        nextBtn = new Button("Next");
        nextBtn.setOnAction(e -> { currentPage++; loadPage(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("accent");
        addBtn.setOnAction(e -> handleAdd());

        Label pageSizeLabel = new Label("Items:");
        pageSizeLabel.setStyle("-fx-text-fill: #666;");

        toolbar.getChildren().addAll(searchField, prevBtn, pageLabel, nextBtn, spacer, addBtn, pageSizeLabel, pageSizeSelector);

        table = createTable();
        personList = FXCollections.observableArrayList();
        table.setItems(personList);
        table.setPlaceholder(new Label("Loading..."));
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(titleBox, toolbar, table);
    }

    @SuppressWarnings("unchecked")
    private TableView<Person> createTable() {
        TableView<Person> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Person, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));

        TableColumn<Person, String> idCol = new TableColumn<>("National ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("nationalId"));

        TableColumn<Person, String> genderCol = new TableColumn<>("Gender");
        genderCol.setCellValueFactory(new PropertyValueFactory<>("gender"));

        TableColumn<Person, LocalDate> dobCol = new TableColumn<>("Date of Birth");
        dobCol.setCellValueFactory(new PropertyValueFactory<>("dob"));

        TableColumn<Person, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));

        TableColumn<Person, Void> actionsCol = new TableColumn<>();
        actionsCol.setPrefWidth(100);
        actionsCol.setSortable(false);
        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = iconBtn(Feather.EYE, "View", tm.accentBlue());
            private final Button editBtn = iconBtn(Feather.EDIT_2, "Edit", tm.accentGreen());
            private final Button deleteBtn = iconBtn(Feather.TRASH_2, "Delete", tm.accentRed());
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
        tv.setPlaceholder(new Label("No items"));

        return tv;
    }

    private void refreshTable() {
        currentPage = 0;
        loadPage();
    }

    private void handleAdd() {
        OffenderFormDialog dialog = new OffenderFormDialog(null);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            personRepo.save(link.getPerson(), () -> {
                CaseParticipant cp = new CaseParticipant();
                cp.setCaseId(link.getCourtCase().getCaseId());
                cp.setPersonId(link.getPerson().getPersonId());
                cp.setRoleType("Accused");
                com.courttrack.dao.CaseDao caseDao = new com.courttrack.dao.CaseDao();
                caseDao.addParticipant(cp);
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

    private String or(String s) {
        return (s != null && !s.isBlank()) ? s : "\u2014";
    }

    private void handleEdit(Person p) {
        OffenderFormDialog dialog = new OffenderFormDialog(p);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            personRepo.save(link.getPerson(), this::refreshTable);
        });
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

        Label detailLabel = new Label("This person will be soft-deleted from the system. " +
            "Their record can be restored later if needed.\n\n" +
            "National ID: " + or(p.getNationalId()));
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
