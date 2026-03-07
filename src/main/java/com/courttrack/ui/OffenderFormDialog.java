package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import com.courttrack.repository.CaseRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

public class OffenderFormDialog extends Dialog<OffenderFormDialog.PersonCaseLink> {

    public static class PersonCaseLink {
        private final Person person;
        private final CourtCase courtCase;

        public PersonCaseLink(Person person, CourtCase courtCase) {
            this.person = person;
            this.courtCase = courtCase;
        }

        public Person getPerson() { return person; }
        public CourtCase getCourtCase() { return courtCase; }
    }

    private final TextField firstNameField;
    private final TextField lastNameField;
    private final TextField otherNamesField;
    private final TextField nationalIdField;
    private final ComboBox<String> genderBox;
    private final DatePicker dobPicker;
    private final TextField phoneField;
    private final TextField emailField;
    private final ComboBox<CourtCase> caseBox;

    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final ObservableList<CourtCase> caseItems = FXCollections.observableArrayList();

    public OffenderFormDialog(Person existing) {
        setTitle(existing == null ? "Add New Person" : "Edit Person");
        setHeaderText(null);
        initModality(Modality.APPLICATION_MODAL);

        // --- Fields ---
        firstNameField = new TextField();
        firstNameField.setPromptText("First name");

        lastNameField = new TextField();
        lastNameField.setPromptText("Last name");

        otherNamesField = new TextField();
        otherNamesField.setPromptText("Middle names, aliases (optional)");

        nationalIdField = new TextField();
        nationalIdField.setPromptText("e.g., 12345678");

        genderBox = new ComboBox<>(FXCollections.observableArrayList("Male", "Female"));
        genderBox.setMaxWidth(Double.MAX_VALUE);
        genderBox.setPromptText("Select");

        dobPicker = new DatePicker();
        dobPicker.setMaxWidth(Double.MAX_VALUE);

        phoneField = new TextField();
        phoneField.setPromptText("e.g., 0712345678");

        emailField = new TextField();
        emailField.setPromptText("e.g., name@email.com");

        caseBox = new ComboBox<>(caseItems);
        caseBox.setMaxWidth(Double.MAX_VALUE);
        caseBox.setPromptText("Select a case...");
        
        caseRepo.getAll(cases -> {
            javafx.application.Platform.runLater(() -> caseItems.setAll(cases));
        });
        caseBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CourtCase c) {
                if (c == null) return null;
                String label = c.getCaseNumber();
                if (c.getCaseTitle() != null && !c.getCaseTitle().isBlank()) {
                    label += " \u2014 " + c.getCaseTitle();
                } else if (c.getCaseCategory() != null) {
                    label += " \u2014 " + c.getCaseCategory();
                }
                return label;
            }

            @Override
            public CourtCase fromString(String string) { return null; }
        });

        Button newCaseBtn = new Button("+ New Case");
        newCaseBtn.getStyleClass().add("accent");
        newCaseBtn.setOnAction(e -> handleNewCase());

        // --- Layout ---
        VBox content = new VBox(0);
        content.setPrefWidth(560);

        // Section 1: Personal Information
        content.getChildren().add(buildSectionHeader("Personal Information", Feather.USER, tm.accentBlue()));

        GridPane personalGrid = new GridPane();
        personalGrid.setHgap(14);
        personalGrid.setVgap(12);
        personalGrid.setPadding(new Insets(12, 24, 16, 24));

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(100);
        labelCol.setMinWidth(100);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints labelCol2 = new ColumnConstraints();
        labelCol2.setPrefWidth(80);
        labelCol2.setMinWidth(80);
        ColumnConstraints fieldCol2 = new ColumnConstraints();
        fieldCol2.setHgrow(Priority.ALWAYS);
        personalGrid.getColumnConstraints().addAll(labelCol, fieldCol, labelCol2, fieldCol2);

        int row = 0;
        personalGrid.add(fieldLabel("First Name *"), 0, row);
        personalGrid.add(firstNameField, 1, row);
        personalGrid.add(fieldLabel("Last Name *"), 2, row);
        personalGrid.add(lastNameField, 3, row);

        row++;
        personalGrid.add(fieldLabel("Other Names"), 0, row);
        GridPane.setColumnSpan(otherNamesField, 3);
        personalGrid.add(otherNamesField, 1, row);

        row++;
        personalGrid.add(fieldLabel("National ID *"), 0, row);
        personalGrid.add(nationalIdField, 1, row);

        row++;
        personalGrid.add(fieldLabel("Gender"), 0, row);
        personalGrid.add(genderBox, 1, row);
        personalGrid.add(fieldLabel("Born"), 2, row);
        personalGrid.add(dobPicker, 3, row);

        content.getChildren().add(personalGrid);

        // Separator
        Separator sep1 = new Separator();
        sep1.setPadding(new Insets(4, 24, 4, 24));
        content.getChildren().add(sep1);

        // Section 2: Contact Details
        content.getChildren().add(buildSectionHeader("Contact Details", Feather.PHONE, tm.accentGreen()));

        GridPane contactGrid = new GridPane();
        contactGrid.setHgap(14);
        contactGrid.setVgap(12);
        contactGrid.setPadding(new Insets(12, 24, 16, 24));
        contactGrid.getColumnConstraints().addAll(labelCol, fieldCol, labelCol2, fieldCol2);

        contactGrid.add(fieldLabel("Phone"), 0, 0);
        contactGrid.add(phoneField, 1, 0);
        contactGrid.add(fieldLabel("Email"), 2, 0);
        contactGrid.add(emailField, 3, 0);

        content.getChildren().add(contactGrid);

        // Separator
        Separator sep2 = new Separator();
        sep2.setPadding(new Insets(4, 24, 4, 24));
        content.getChildren().add(sep2);

        // Section 3: Case Assignment
        content.getChildren().add(buildSectionHeader("Case Assignment", Feather.BRIEFCASE, tm.accentOrange()));

        HBox caseRow = new HBox(10, caseBox, newCaseBtn);
        caseRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(caseBox, Priority.ALWAYS);

        GridPane caseGrid = new GridPane();
        caseGrid.setHgap(14);
        caseGrid.setVgap(12);
        caseGrid.setPadding(new Insets(12, 24, 20, 24));
        ColumnConstraints caseLabelCol = new ColumnConstraints();
        caseLabelCol.setPrefWidth(100);
        caseLabelCol.setMinWidth(100);
        ColumnConstraints caseFieldCol = new ColumnConstraints();
        caseFieldCol.setHgrow(Priority.ALWAYS);
        caseGrid.getColumnConstraints().addAll(caseLabelCol, caseFieldCol);

        caseGrid.add(fieldLabel("Case *"), 0, 0);
        caseGrid.add(caseRow, 1, 0);

        content.getChildren().add(caseGrid);

        // --- Populate if editing ---
        if (existing != null) {
            firstNameField.setText(existing.getFirstName());
            lastNameField.setText(existing.getLastName());
            otherNamesField.setText(existing.getOtherNames());
            nationalIdField.setText(existing.getNationalId());
            genderBox.setValue(existing.getGender());
            dobPicker.setValue(existing.getDob());
            phoneField.setText(existing.getPhoneNumber());
            emailField.setText(existing.getEmail());
        }

        // --- Dialog chrome ---
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(existing == null ? "Add Person" : "Save Changes");
        okButton.getStyleClass().add("accent");

        Button cancelButton = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Cancel");

        // Validation
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (trimOrEmpty(firstNameField.getText()).isEmpty()) {
                showValidationError("First name is required.");
                event.consume();
            } else if (trimOrEmpty(lastNameField.getText()).isEmpty()) {
                showValidationError("Last name is required.");
                event.consume();
            } else if (trimOrEmpty(nationalIdField.getText()).isEmpty()) {
                showValidationError("National ID is required.");
                event.consume();
            } else if (caseBox.getValue() == null) {
                showValidationError("Please select a case to assign this person to.");
                event.consume();
            }
        });

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Person p = existing != null ? existing : new Person();
                p.setFirstName(trimOrEmpty(firstNameField.getText()));
                p.setLastName(trimOrEmpty(lastNameField.getText()));
                p.setOtherNames(trimOrEmpty(otherNamesField.getText()));
                p.setNationalId(trimOrEmpty(nationalIdField.getText()));
                p.setGender(genderBox.getValue());
                p.setDob(dobPicker.getValue());
                p.setPhoneNumber(trimOrEmpty(phoneField.getText()));
                p.setEmail(trimOrEmpty(emailField.getText()));
                return new PersonCaseLink(p, caseBox.getValue());
            }
            return null;
        });
    }

    private void handleNewCase() {
        CaseFormDialog dialog = new CaseFormDialog(null);
        Optional<CourtCase> result = dialog.showAndWait();
        result.ifPresent(newCase -> {
            caseRepo.save(newCase, null, () -> {
                javafx.application.Platform.runLater(() -> {
                    caseItems.add(newCase);
                    caseBox.setValue(newCase);
                });
            });
        });
    }

    private HBox buildSectionHeader(String title, Feather icon, String color) {
        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(16);
        fi.setIconColor(Color.web(color));

        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        HBox header = new HBox(8, fi, label);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 4, 24));
        return header;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", 12));
        label.getStyleClass().add("text-muted");
        return label;
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
