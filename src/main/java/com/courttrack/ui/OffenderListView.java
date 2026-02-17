package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.PersonDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.Person;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import javafx.stage.Modality;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

public class OffenderListView {
    private final VBox root;
    private final PersonDao personDao = new PersonDao();
    private final CaseDao caseDao = new CaseDao();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Consumer<Person> onViewDetail;
    private TableView<Person> table;
    private ObservableList<Person> personList;
    private TextField searchField;

    public OffenderListView(Consumer<Person> onViewDetail) {
        this.onViewDetail = onViewDetail;
        root = new VBox(20);
        root.setPadding(new Insets(32, 40, 32, 40));
        buildUI();
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
        searchField.textProperty().addListener((obs, o, n) -> refreshTable());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("accent");
        addBtn.setOnAction(e -> handleAdd());

        toolbar.getChildren().addAll(searchField, spacer, addBtn);

        table = createTable();
        personList = FXCollections.observableArrayList(personDao.findAll());
        table.setItems(personList);
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
        String query = searchField.getText().trim();
        if (query.isEmpty()) { personList.setAll(personDao.findAll()); }
        else { personList.setAll(personDao.search(query)); }
    }

    private void handleAdd() {
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
            refreshTable();
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
        result.ifPresent(link -> { personDao.update(link.getPerson()); refreshTable(); });
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
            personDao.softDelete(p.getPersonId());
            refreshTable();
        }
    }

    public Parent getRoot() { return root; }
}
