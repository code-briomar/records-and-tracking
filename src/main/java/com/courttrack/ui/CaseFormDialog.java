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
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;

public class CaseFormDialog extends Dialog<CourtCase> {
    // Section 1: Case Information
    private final TextField caseNumberField;
    private final TextField caseTitleField;
    private final ComboBox<String> categoryBox;
    private final ComboBox<String> caseTypeBox;
    private final ComboBox<String> statusBox;
    private final ComboBox<String> priorityBox;
    private final DatePicker filingDatePicker;
    private final TextField courtIdField;
    private final TextField courtNameField;
    private final TextArea descriptionArea;

    // Section 2: Charge Details
    private final TextArea chargeParticularsArea;
    private final ComboBox<String> pleaBox;
    private final ComboBox<String> verdictBox;

    // Section 3: Judgment & Sentencing
    private final DatePicker judgmentDatePicker;
    private final TextArea sentenceArea;
    private final TextArea mitigationNotesArea;
    private final ComboBox<String> appealStatusBox;

    // Section 4: Case Details
    private final TextField prosecutionCounselField;
    private final TextField courtAssistantField;
    private final TextField locationOfOffenceField;
    private final TextArea evidenceSummaryArea;
    private final TextArea hearingDatesArea;

    private final ThemeManager tm = ThemeManager.getInstance();

    public CaseFormDialog(CourtCase existing) {
        setTitle(existing == null ? "Add New Case" : "Edit Case");
        setHeaderText(null);
        setResizable(true);
        initModality(Modality.APPLICATION_MODAL);

        // --- Fields ---

        // Section 1
        caseNumberField = new TextField();
        caseNumberField.setPromptText("e.g., CR-009/2024");

        caseTitleField = new TextField();
        caseTitleField.setPromptText("e.g., Republic v. John Doe");

        categoryBox = new ComboBox<>(FXCollections.observableArrayList("Criminal", "Traffic", "Civil"));
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.setPromptText("Select category");

        caseTypeBox = new ComboBox<>(FXCollections.observableArrayList(
            "Felony", "Misdemeanor", "Petty Offence", "Appeal", "Revision", "Judicial Review"
        ));
        caseTypeBox.setMaxWidth(Double.MAX_VALUE);
        caseTypeBox.setPromptText("Select type");
        caseTypeBox.setEditable(true);

        statusBox = new ComboBox<>(FXCollections.observableArrayList(
            "OPEN", "CLOSED", "ADJOURNED", "DISMISSED", "SETTLED"
        ));
        statusBox.setMaxWidth(Double.MAX_VALUE);

        priorityBox = new ComboBox<>(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH"));
        priorityBox.setMaxWidth(Double.MAX_VALUE);
        priorityBox.setPromptText("Select priority");

        filingDatePicker = new DatePicker();
        filingDatePicker.setMaxWidth(Double.MAX_VALUE);

        courtIdField = new TextField();
        courtIdField.setPromptText("e.g., court-nairobi-mc");

        courtNameField = new TextField();
        courtNameField.setPromptText("e.g., Nairobi Magistrates Court");

        descriptionArea = new TextArea();
        descriptionArea.setPromptText("Brief description of the case...");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        // Section 2
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

        // Section 3
        judgmentDatePicker = new DatePicker();
        judgmentDatePicker.setMaxWidth(Double.MAX_VALUE);

        sentenceArea = new TextArea();
        sentenceArea.setPromptText("Sentence details...");
        sentenceArea.setPrefRowCount(2);
        sentenceArea.setWrapText(true);

        mitigationNotesArea = new TextArea();
        mitigationNotesArea.setPromptText("Mitigation notes...");
        mitigationNotesArea.setPrefRowCount(2);
        mitigationNotesArea.setWrapText(true);

        appealStatusBox = new ComboBox<>(FXCollections.observableArrayList(
            "", "NONE", "FILED", "HEARD", "ALLOWED", "DISMISSED"
        ));
        appealStatusBox.setMaxWidth(Double.MAX_VALUE);
        appealStatusBox.setPromptText("Select appeal status");

        // Section 4
        prosecutionCounselField = new TextField();
        prosecutionCounselField.setPromptText("Name of prosecution counsel");

        courtAssistantField = new TextField();
        courtAssistantField.setPromptText("Name of court assistant");

        locationOfOffenceField = new TextField();
        locationOfOffenceField.setPromptText("Location where offence occurred");

        evidenceSummaryArea = new TextArea();
        evidenceSummaryArea.setPromptText("Summary of evidence...");
        evidenceSummaryArea.setPrefRowCount(3);
        evidenceSummaryArea.setWrapText(true);

        hearingDatesArea = new TextArea();
        hearingDatesArea.setPromptText("Hearing dates (one per line)...");
        hearingDatesArea.setPrefRowCount(2);
        hearingDatesArea.setWrapText(true);

        // --- Column constraints (shared) ---
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(110);
        labelCol.setMinWidth(110);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints labelCol2 = new ColumnConstraints();
        labelCol2.setPrefWidth(90);
        labelCol2.setMinWidth(90);
        ColumnConstraints fieldCol2 = new ColumnConstraints();
        fieldCol2.setHgrow(Priority.ALWAYS);

        // --- Layout ---
        VBox content = new VBox(0);

        // Section 1: Case Information
        content.getChildren().add(buildSectionHeader("Case Information", Feather.BRIEFCASE, tm.accentBlue()));
        GridPane caseGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);

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
        caseGrid.add(fieldLabel("Case Type"), 0, row);
        caseGrid.add(caseTypeBox, 1, row);
        caseGrid.add(fieldLabel("Priority"), 2, row);
        caseGrid.add(priorityBox, 3, row);

        row++;
        caseGrid.add(fieldLabel("Court ID"), 0, row);
        caseGrid.add(courtIdField, 1, row);
        caseGrid.add(fieldLabel("Status"), 2, row);
        caseGrid.add(statusBox, 3, row);

        row++;
        caseGrid.add(fieldLabel("Court Name"), 0, row);
        GridPane.setColumnSpan(courtNameField, 3);
        caseGrid.add(courtNameField, 1, row);

        row++;
        caseGrid.add(fieldLabel("Filing Date *"), 0, row);
        caseGrid.add(filingDatePicker, 1, row);

        row++;
        caseGrid.add(fieldLabel("Description"), 0, row);
        GridPane.setColumnSpan(descriptionArea, 3);
        caseGrid.add(descriptionArea, 1, row);

        content.getChildren().add(caseGrid);
        content.getChildren().add(buildSeparator());

        // Section 2: Charge Details
        content.getChildren().add(buildSectionHeader("Charge Details", Feather.FILE_TEXT, tm.accentOrange()));
        GridPane chargeGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);

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
        content.getChildren().add(buildSeparator());

        // Section 3: Judgment & Sentencing
        content.getChildren().add(buildSectionHeader("Judgment & Sentencing", Feather.AWARD, tm.accentRed()));
        GridPane judgmentGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);

        int jrow = 0;
        judgmentGrid.add(fieldLabel("Judgment Date"), 0, jrow);
        judgmentGrid.add(judgmentDatePicker, 1, jrow);
        judgmentGrid.add(fieldLabel("Appeal"), 2, jrow);
        judgmentGrid.add(appealStatusBox, 3, jrow);

        jrow++;
        judgmentGrid.add(fieldLabel("Sentence"), 0, jrow);
        GridPane.setColumnSpan(sentenceArea, 3);
        judgmentGrid.add(sentenceArea, 1, jrow);

        jrow++;
        judgmentGrid.add(fieldLabel("Mitigation"), 0, jrow);
        GridPane.setColumnSpan(mitigationNotesArea, 3);
        judgmentGrid.add(mitigationNotesArea, 1, jrow);

        content.getChildren().add(judgmentGrid);
        content.getChildren().add(buildSeparator());

        // Section 4: Additional Details
        content.getChildren().add(buildSectionHeader("Additional Details", Feather.INFO, tm.accentGreen()));
        GridPane detailGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);

        int drow = 0;
        detailGrid.add(fieldLabel("Prosecutor"), 0, drow);
        detailGrid.add(prosecutionCounselField, 1, drow);
        detailGrid.add(fieldLabel("Assistant"), 2, drow);
        detailGrid.add(courtAssistantField, 3, drow);

        drow++;
        detailGrid.add(fieldLabel("Location"), 0, drow);
        GridPane.setColumnSpan(locationOfOffenceField, 3);
        detailGrid.add(locationOfOffenceField, 1, drow);

        drow++;
        detailGrid.add(fieldLabel("Evidence"), 0, drow);
        GridPane.setColumnSpan(evidenceSummaryArea, 3);
        detailGrid.add(evidenceSummaryArea, 1, drow);

        drow++;
        detailGrid.add(fieldLabel("Hearings"), 0, drow);
        GridPane.setColumnSpan(hearingDatesArea, 3);
        detailGrid.add(hearingDatesArea, 1, drow);

        content.getChildren().add(detailGrid);

        // Wrap in ScrollPane
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefWidth(640);
        scrollPane.setPrefHeight(600);

        // --- Populate if editing ---
        if (existing != null) {
            caseNumberField.setText(existing.getCaseNumber());
            caseTitleField.setText(existing.getCaseTitle());
            categoryBox.setValue(existing.getCaseCategory());
            caseTypeBox.setValue(existing.getCaseType());
            statusBox.setValue(existing.getCaseStatus());
            priorityBox.setValue(existing.getPriority());
            filingDatePicker.setValue(existing.getFilingDate());
            courtIdField.setText(existing.getCourtId());
            courtNameField.setText(existing.getCourtName());
            descriptionArea.setText(existing.getDescription());
            chargeParticularsArea.setText(existing.getChargeParticulars());
            pleaBox.setValue(existing.getChargePlea());
            verdictBox.setValue(existing.getChargeVerdict());
            judgmentDatePicker.setValue(existing.getDateOfJudgment());
            sentenceArea.setText(existing.getSentence());
            mitigationNotesArea.setText(existing.getMitigationNotes());
            appealStatusBox.setValue(existing.getAppealStatus());
            prosecutionCounselField.setText(existing.getProsecutionCounsel());
            courtAssistantField.setText(existing.getCourtAssistant());
            locationOfOffenceField.setText(existing.getLocationOfOffence());
            evidenceSummaryArea.setText(existing.getEvidenceSummary());
            hearingDatesArea.setText(existing.getHearingDates());
        } else {
            statusBox.setValue("OPEN");
            filingDatePicker.setValue(LocalDate.now());
        }

        // --- Dialog chrome ---
        getDialogPane().setContent(scrollPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Make resizable
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        stage.setMinWidth(800);
        stage.setMinHeight(600);

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
                c.setCaseTitle(trimOrEmpty(caseTitleField.getText()));
                c.setCaseCategory(categoryBox.getValue());
                c.setCaseType(caseTypeBox.getValue());
                c.setCaseStatus(statusBox.getValue());
                c.setPriority(priorityBox.getValue());
                c.setFilingDate(filingDatePicker.getValue());
                c.setCourtId(trimOrEmpty(courtIdField.getText()));
                c.setCourtName(trimOrEmpty(courtNameField.getText()));
                c.setDescription(trimOrEmpty(descriptionArea.getText()));
                c.setChargeParticulars(trimOrEmpty(chargeParticularsArea.getText()));
                c.setChargePlea(nonBlankOrNull(pleaBox.getValue()));
                c.setChargeVerdict(nonBlankOrNull(verdictBox.getValue()));
                c.setDateOfJudgment(judgmentDatePicker.getValue());
                c.setSentence(trimOrEmpty(sentenceArea.getText()));
                c.setMitigationNotes(trimOrEmpty(mitigationNotesArea.getText()));
                c.setAppealStatus(nonBlankOrNull(appealStatusBox.getValue()));
                c.setProsecutionCounsel(trimOrEmpty(prosecutionCounselField.getText()));
                c.setCourtAssistant(trimOrEmpty(courtAssistantField.getText()));
                c.setLocationOfOffence(trimOrEmpty(locationOfOffenceField.getText()));
                c.setEvidenceSummary(trimOrEmpty(evidenceSummaryArea.getText()));
                c.setHearingDates(trimOrEmpty(hearingDatesArea.getText()));
                return c;
            }
            return null;
        });
    }

    private GridPane buildGrid(ColumnConstraints... cols) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(12, 24, 16, 24));
        grid.getColumnConstraints().addAll(cols);
        return grid;
    }

    private Separator buildSeparator() {
        Separator sep = new Separator();
        sep.setPadding(new Insets(4, 24, 4, 24));
        return sep;
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

    private static String nonBlankOrNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validation");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
