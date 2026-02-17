package com.courttrack.ui;

import com.courttrack.model.CourtCase;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class CaseFormDialog extends Dialog<CourtCase> {
    private final TextField caseNumberField;
    private final TextField caseTitleField;
    private final ComboBox<String> categoryBox;
    private final ComboBox<String> statusBox;
    private final DatePicker filingDatePicker;
    private final TextField courtIdField;
    private final TextArea chargeParticularsArea;
    private final ComboBox<String> pleaBox;
    private final ComboBox<String> verdictBox;

    private final ThemeManager tm = ThemeManager.getInstance();

    public CaseFormDialog(CourtCase existing) {
        setTitle(existing == null ? "Add New Case" : "Edit Case");
        setHeaderText(null);

        // --- Fields ---
        caseNumberField = new TextField();
        caseNumberField.setPromptText("e.g., CR-009/2024");

        caseTitleField = new TextField();
        caseTitleField.setPromptText("e.g., Republic v. John Doe");

        categoryBox = new ComboBox<>(FXCollections.observableArrayList("Criminal", "Traffic", "Civil"));
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.setPromptText("Select category");

        statusBox = new ComboBox<>(FXCollections.observableArrayList("OPEN", "CLOSED"));
        statusBox.setMaxWidth(Double.MAX_VALUE);

        filingDatePicker = new DatePicker();
        filingDatePicker.setMaxWidth(Double.MAX_VALUE);

        courtIdField = new TextField();
        courtIdField.setPromptText("e.g., NAIROBI-MC");

        chargeParticularsArea = new TextArea();
        chargeParticularsArea.setPromptText("Describe the charge particulars...");
        chargeParticularsArea.setPrefRowCount(3);
        chargeParticularsArea.setWrapText(true);

        pleaBox = new ComboBox<>(FXCollections.observableArrayList("", "Guilty", "Not Guilty"));
        pleaBox.setMaxWidth(Double.MAX_VALUE);
        pleaBox.setPromptText("Select plea");

        verdictBox = new ComboBox<>(FXCollections.observableArrayList(
            "", "CONVICTED", "ACQUITTED", "JUDGEMENT_FOR_PLAINTIFF", "JUDGEMENT_FOR_DEFENDANT", "DISMISSED"
        ));
        verdictBox.setMaxWidth(Double.MAX_VALUE);
        verdictBox.setPromptText("Select verdict");

        // --- Layout ---
        VBox content = new VBox(0);
        content.setPrefWidth(540);

        // Section 1: Case Information
        content.getChildren().add(buildSectionHeader("Case Information", Feather.BRIEFCASE, tm.accentBlue()));
        GridPane caseGrid = new GridPane();
        caseGrid.setHgap(14);
        caseGrid.setVgap(12);
        caseGrid.setPadding(new Insets(12, 24, 20, 24));

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
        caseGrid.getColumnConstraints().addAll(labelCol, fieldCol, labelCol2, fieldCol2);

        int row = 0;
        caseGrid.add(fieldLabel("Case Number *"), 0, row);
        caseGrid.add(caseNumberField, 1, row);
        caseGrid.add(fieldLabel("Category *"), 2, row);
        caseGrid.add(categoryBox, 3, row);

        row++;
        caseGrid.add(fieldLabel("Case Title"), 0, row);
        GridPane.setColumnSpan(caseTitleField, 3);
        caseGrid.add(caseTitleField, 1, row);

        row++;
        caseGrid.add(fieldLabel("Court"), 0, row);
        caseGrid.add(courtIdField, 1, row);
        caseGrid.add(fieldLabel("Status"), 2, row);
        caseGrid.add(statusBox, 3, row);

        row++;
        caseGrid.add(fieldLabel("Filing Date *"), 0, row);
        caseGrid.add(filingDatePicker, 1, row);

        content.getChildren().add(caseGrid);

        // Separator
        Separator sep = new Separator();
        sep.setPadding(new Insets(4, 24, 4, 24));
        content.getChildren().add(sep);

        // Section 2: Charge Details
        content.getChildren().add(buildSectionHeader("Charge Details", Feather.FILE_TEXT, tm.accentOrange()));
        GridPane chargeGrid = new GridPane();
        chargeGrid.setHgap(14);
        chargeGrid.setVgap(12);
        chargeGrid.setPadding(new Insets(12, 24, 20, 24));
        chargeGrid.getColumnConstraints().addAll(labelCol, fieldCol, labelCol2, fieldCol2);

        int crow = 0;
        chargeGrid.add(fieldLabel("Particulars"), 0, crow);
        GridPane.setColumnSpan(chargeParticularsArea, 3);
        chargeGrid.add(chargeParticularsArea, 1, crow);

        crow++;
        chargeGrid.add(fieldLabel("Plea"), 0, crow);
        chargeGrid.add(pleaBox, 1, crow);
        chargeGrid.add(fieldLabel("Verdict"), 2, crow);
        chargeGrid.add(verdictBox, 3, crow);

        content.getChildren().add(chargeGrid);

        // --- Populate if editing ---
        if (existing != null) {
            caseNumberField.setText(existing.getCaseNumber());
            caseTitleField.setText(existing.getCaseTitle());
            categoryBox.setValue(existing.getCaseCategory());
            statusBox.setValue(existing.getCaseStatus());
            filingDatePicker.setValue(existing.getFilingDate());
            courtIdField.setText(existing.getCourtId());
            chargeParticularsArea.setText(existing.getChargeParticulars());
            pleaBox.setValue(existing.getChargePlea());
            verdictBox.setValue(existing.getChargeVerdict());
        } else {
            statusBox.setValue("OPEN");
            filingDatePicker.setValue(java.time.LocalDate.now());
        }

        // --- Dialog chrome ---
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(existing == null ? "Add Case" : "Save Changes");
        okButton.getStyleClass().add("accent");

        Button cancelButton = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Cancel");

        // Validation
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (caseNumberField.getText().trim().isEmpty()) {
                showValidationError("Case number is required.");
                event.consume();
            } else if (categoryBox.getValue() == null) {
                showValidationError("Category is required.");
                event.consume();
            } else if (filingDatePicker.getValue() == null) {
                showValidationError("Filing date is required.");
                event.consume();
            }
        });

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                CourtCase c = existing != null ? existing : new CourtCase();
                c.setCaseNumber(caseNumberField.getText().trim());
                c.setCaseTitle(caseTitleField.getText().trim());
                c.setCaseCategory(categoryBox.getValue());
                c.setCaseStatus(statusBox.getValue());
                c.setFilingDate(filingDatePicker.getValue());
                c.setCourtId(courtIdField.getText().trim());
                c.setChargeParticulars(trimOrEmpty(chargeParticularsArea.getText()));
                c.setChargePlea(pleaBox.getValue() != null && !pleaBox.getValue().isBlank() ? pleaBox.getValue() : null);
                c.setChargeVerdict(verdictBox.getValue() != null && !verdictBox.getValue().isBlank() ? verdictBox.getValue() : null);
                return c;
            }
            return null;
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
