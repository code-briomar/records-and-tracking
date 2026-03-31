package com.courttrack.ui;

import com.courttrack.model.CourtCase;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaseFormDialog extends Dialog<CourtCase> {

    // ---------------------------------------------------------------
    // Public types
    // ---------------------------------------------------------------

    /** A person to be created and linked to the case on save. */
    public record ParticipantEntry(String firstName, String lastName,
                                   String nationalId, String roleType) {}

    /** Inner class holding one participant row's fields. */
    private static final class ParticipantRow {
        final TextField firstNameField = new TextField();
        final TextField lastNameField  = new TextField();
        final TextField nationalIdField = new TextField();
        final ComboBox<String> roleBox;
        final HBox node;

        ParticipantRow(Runnable onDelete) {
            firstNameField.setPromptText("First name *");
            HBox.setHgrow(firstNameField, Priority.ALWAYS);

            lastNameField.setPromptText("Last name *");
            HBox.setHgrow(lastNameField, Priority.ALWAYS);

            nationalIdField.setPromptText("ID (optional)");
            nationalIdField.setPrefWidth(115);

            roleBox = new ComboBox<>(FXCollections.observableArrayList(
                "Accused", "Witness", "Victim", "Complainant", "Other"
            ));
            roleBox.setPromptText("Role");
            roleBox.setValue("Accused");
            roleBox.setPrefWidth(130);

            FontIcon delIcon = new FontIcon(Feather.X);
            delIcon.setIconSize(13);
            delIcon.setIconColor(Color.web("#eb5757"));
            Button deleteBtn = new Button();
            deleteBtn.setGraphic(delIcon);
            deleteBtn.setStyle("""
                -fx-background-color: transparent;
                -fx-padding: 4;
                -fx-cursor: hand;
                -fx-background-radius: 4;
            """);
            deleteBtn.setOnAction(e -> onDelete.run());

            node = new HBox(8, firstNameField, lastNameField, nationalIdField, roleBox, deleteBtn);
            node.setAlignment(Pos.CENTER_LEFT);
        }
    }

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    private static final Map<String, String> CATEGORY_PREFIXES = Map.of(
        "Civil", "CV-", "Criminal", "CR-", "Succession", "SC-",
        "Children", "CH-", "Traffic", "TR-", "Other", "OT-"
    );

    // Section 1
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

    // Section 2
    private final TextArea chargeParticularsArea;
    private final ComboBox<String> pleaBox;
    private final ComboBox<String> verdictBox;

    // Section 3
    private final DatePicker judgmentDatePicker;
    private final TextArea sentenceArea;
    private final TextArea mitigationNotesArea;
    private final ComboBox<String> appealStatusBox;

    // Section 4
    private final TextField prosecutionCounselField;
    private final TextField courtAssistantField;
    private final TextField locationOfOffenceField;
    private final TextArea evidenceSummaryArea;
    private final TextArea hearingDatesArea;

    // Participants
    private final List<ParticipantRow> participantRows = new ArrayList<>();
    private VBox participantsBox;

    private boolean addAnother = false;
    public boolean isAddAnother() { return addAnother; }

    public List<ParticipantEntry> getParticipantsToCreate() {
        return participantRows.stream()
            .filter(r -> !r.firstNameField.getText().trim().isEmpty())
            .map(r -> new ParticipantEntry(
                r.firstNameField.getText().trim(),
                r.lastNameField.getText().trim(),
                r.nationalIdField.getText().trim(),
                r.roleBox.getValue() != null ? r.roleBox.getValue() : "Accused"
            ))
            .toList();
    }

    private final ThemeManager tm = ThemeManager.getInstance();

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    public CaseFormDialog(CourtCase existing) {
        setTitle(existing == null ? "Add New Case" : "Edit Case");
        setHeaderText(null);
        setResizable(true);
        initModality(Modality.APPLICATION_MODAL);

        // ---- Field initialization ----

        caseNumberField = new TextField();
        caseNumberField.setPromptText("e.g., CR-009/2024");

        caseTitleField = new TextField();
        caseTitleField.setPromptText("e.g., Republic v. John Doe");

        categoryBox = new ComboBox<>(FXCollections.observableArrayList(
            "Civil", "Criminal", "Succession", "Children", "Traffic", "Other"
        ));
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

        // ---- Column constraints ----
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(110); labelCol.setMinWidth(110);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints labelCol2 = new ColumnConstraints();
        labelCol2.setPrefWidth(90); labelCol2.setMinWidth(90);
        ColumnConstraints fieldCol2 = new ColumnConstraints();
        fieldCol2.setHgrow(Priority.ALWAYS);

        // ================================================================
        // Section 1: Case Information — permanent card
        // ================================================================
        GridPane caseGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);
        int r = 0;
        caseGrid.add(fieldLabel("Case Number *"), 0, r);
        caseGrid.add(caseNumberField, 1, r);
        caseGrid.add(fieldLabel("Category *"), 2, r);
        caseGrid.add(categoryBox, 3, r++);

        caseGrid.add(fieldLabel("Case Title"), 0, r);
        GridPane.setColumnSpan(caseTitleField, 3);
        caseGrid.add(caseTitleField, 1, r++);

        caseGrid.add(fieldLabel("Case Type"), 0, r);
        caseGrid.add(caseTypeBox, 1, r);
        caseGrid.add(fieldLabel("Status"), 2, r);
        if (existing != null) {
            caseGrid.add(statusBox, 3, r++);
        } else {
            Label openBadge = new Label("OPEN");
            openBadge.setStyle(
                "-fx-background-color: " + tm.badgeOpenBg() + "; -fx-text-fill: " + tm.badgeOpenText() +
                "; -fx-background-radius: 4; -fx-padding: 4 12; -fx-font-weight: bold;");
            caseGrid.add(openBadge, 3, r++);
        }

        caseGrid.add(fieldLabel("Court Name"), 0, r);
        GridPane.setColumnSpan(courtNameField, 3);
        caseGrid.add(courtNameField, 1, r++);

        caseGrid.add(fieldLabel("Filing Date *"), 0, r);
        caseGrid.add(filingDatePicker, 1, r++);

        caseGrid.add(fieldLabel("Description"), 0, r);
        GridPane.setColumnSpan(descriptionArea, 3);
        caseGrid.add(descriptionArea, 1, r);

        VBox caseCard = buildFormCard("Case Information", Feather.BRIEFCASE, tm.accentBlue(), caseGrid);

        // ================================================================
        // Section 2: Charge Details
        // ================================================================
        GridPane chargeGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);
        int crow = 0;
        chargeGrid.add(fieldLabel("Particulars"), 0, crow);
        GridPane.setColumnSpan(chargeParticularsArea, 3);
        chargeGrid.add(chargeParticularsArea, 1, crow++);
        chargeGrid.add(fieldLabel("Plea"), 0, crow);
        chargeGrid.add(pleaBox, 1, crow);
        chargeGrid.add(fieldLabel("Verdict"), 2, crow);
        chargeGrid.add(verdictBox, 3, crow);
        VBox chargeCard = buildCollapsibleCard("Charge Details", Feather.FILE_TEXT, tm.accentBlue(), chargeGrid, existing != null);

        // ================================================================
        // Section 3: Judgment & Sentencing
        // ================================================================
        GridPane judgmentGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);
        int jrow = 0;
        judgmentGrid.add(fieldLabel("Judgment Date"), 0, jrow);
        judgmentGrid.add(judgmentDatePicker, 1, jrow);
        judgmentGrid.add(fieldLabel("Appeal"), 2, jrow);
        judgmentGrid.add(appealStatusBox, 3, jrow++);
        judgmentGrid.add(fieldLabel("Sentence"), 0, jrow);
        GridPane.setColumnSpan(sentenceArea, 3);
        judgmentGrid.add(sentenceArea, 1, jrow++);
        judgmentGrid.add(fieldLabel("Mitigation"), 0, jrow);
        GridPane.setColumnSpan(mitigationNotesArea, 3);
        judgmentGrid.add(mitigationNotesArea, 1, jrow);
        VBox judgmentCard = buildCollapsibleCard("Judgment & Sentencing", Feather.AWARD, tm.accentBlue(), judgmentGrid, existing != null);

        // ================================================================
        // Section 4: Additional Details
        // ================================================================
        GridPane detailGrid = buildGrid(labelCol, fieldCol, labelCol2, fieldCol2);
        int drow = 0;
        detailGrid.add(fieldLabel("Prosecutor"), 0, drow);
        detailGrid.add(prosecutionCounselField, 1, drow);
        detailGrid.add(fieldLabel("Priority"), 2, drow);
        detailGrid.add(priorityBox, 3, drow++);
        detailGrid.add(fieldLabel("Assistant"), 0, drow);
        detailGrid.add(courtAssistantField, 1, drow++);
        detailGrid.add(fieldLabel("Location"), 0, drow);
        GridPane.setColumnSpan(locationOfOffenceField, 3);
        detailGrid.add(locationOfOffenceField, 1, drow++);
        detailGrid.add(fieldLabel("Evidence"), 0, drow);
        GridPane.setColumnSpan(evidenceSummaryArea, 3);
        detailGrid.add(evidenceSummaryArea, 1, drow++);
        detailGrid.add(fieldLabel("Hearings"), 0, drow);
        GridPane.setColumnSpan(hearingDatesArea, 3);
        detailGrid.add(hearingDatesArea, 1, drow);
        VBox detailCard = buildCollapsibleCard("Additional Details", Feather.INFO, tm.accentBlue(), detailGrid, existing != null);

        // ================================================================
        // Section 5: Participants — collapsed by default
        // ================================================================
        participantsBox = new VBox(8);

        Label participantsHint = new Label(
            "Add defendants, witnesses, or other persons. They will be saved and linked to this case automatically.");
        participantsHint.setFont(Font.font("System", 12));
        participantsHint.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + ";");
        participantsHint.setWrapText(true);

        // Column header row
        Label hdrFirst = new Label("First Name");
        Label hdrLast  = new Label("Last Name");
        Label hdrId    = new Label("National ID");
        Label hdrRole  = new Label("Role");
        for (Label h : new Label[]{hdrFirst, hdrLast, hdrId, hdrRole}) {
            h.setFont(Font.font("System", FontWeight.SEMI_BOLD, 11));
            h.setStyle("-fx-text-fill: " + (tm.isDark() ? "#707070" : "#888888") + ";");
        }
        hdrFirst.setPrefWidth(Double.MAX_VALUE);
        HBox.setHgrow(hdrFirst, Priority.ALWAYS);
        hdrLast.setPrefWidth(Double.MAX_VALUE);
        HBox.setHgrow(hdrLast, Priority.ALWAYS);
        hdrId.setPrefWidth(115);
        hdrRole.setPrefWidth(130);
        Region hdrSpacer = new Region(); hdrSpacer.setPrefWidth(30);
        HBox columnHeaders = new HBox(8, hdrFirst, hdrLast, hdrId, hdrRole, hdrSpacer);

        Button addParticipantBtn = new Button("+ Add Participant");
        addParticipantBtn.setOnAction(e -> addParticipantRow());
        addParticipantBtn.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-border-color: %s;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 5 12;
            -fx-cursor: hand;
            -fx-font-size: 12px;
        """,
            "transparent",
            tm.accentBlue(),
            tm.accentBlue()));

        VBox participantsContent = new VBox(10, participantsHint, columnHeaders, participantsBox, addParticipantBtn);
        participantsContent.setPadding(new Insets(14, 20, 16, 20));

        VBox participantsCard = buildCollapsibleCard("Participants", Feather.USERS, tm.accentBlue(), participantsContent, false);

        // ================================================================
        // Assemble content
        // ================================================================
        VBox content = new VBox(10, caseCard, chargeCard, judgmentCard, detailCard, participantsCard);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + (tm.isDark() ? "#161616" : "#f4f4f4") + ";");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefWidth(680);
        scrollPane.setPrefHeight(600);
        scrollPane.setStyle("-fx-background-color: " + (tm.isDark() ? "#161616" : "#f4f4f4") +
                            "; -fx-background: " + (tm.isDark() ? "#161616" : "#f4f4f4") + ";");

        // ================================================================
        // Populate if editing
        // ================================================================
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
            priorityBox.setValue("MEDIUM");
            filingDatePicker.setValue(LocalDate.now());
        }

        // Auto-prefix case number when category selected (new case only)
        if (existing == null) {
            categoryBox.valueProperty().addListener((obs, oldCat, newCat) -> {
                if (newCat == null) return;
                String cur = caseNumberField.getText();
                boolean onlyPrefix = cur.isEmpty() || CATEGORY_PREFIXES.values().stream().anyMatch(cur::equals);
                if (onlyPrefix) {
                    String prefix = CATEGORY_PREFIXES.getOrDefault(newCat, "");
                    caseNumberField.setText(prefix);
                    caseNumberField.positionCaret(prefix.length());
                }
            });
        }

        // ================================================================
        // Dialog chrome
        // ================================================================
        getDialogPane().setContent(scrollPane);
        ButtonType ADD_ANOTHER = new ButtonType("Save & Add Another", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ADD_ANOTHER, ButtonType.CANCEL);

        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        stage.setMinWidth(840);
        stage.setMinHeight(640);

        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(existing == null ? "Add Case" : "Save Changes");
        okButton.getStyleClass().add("accent");

        Button cancelButton = (Button) getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setText("Cancel");

        javafx.event.EventHandler<javafx.event.ActionEvent> validationFilter = event -> {
            if (caseNumberField.getText().trim().isEmpty()) {
                showValidationError("Case number is required."); event.consume();
            } else if (categoryBox.getValue() == null) {
                showValidationError("Category is required."); event.consume();
            } else if (filingDatePicker.getValue() == null) {
                showValidationError("Filing date is required."); event.consume();
            }
        };
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, validationFilter);
        Button addAnotherBtn = (Button) getDialogPane().lookupButton(ADD_ANOTHER);
        addAnotherBtn.addEventFilter(javafx.event.ActionEvent.ACTION, validationFilter);

        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK || buttonType == ADD_ANOTHER) {
                addAnother = (buttonType == ADD_ANOTHER);
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

        Platform.runLater(() -> caseNumberField.requestFocus());
    }

    // ================================================================
    // Participant row management
    // ================================================================

    private void addParticipantRow() {
        ParticipantRow[] ref = new ParticipantRow[1];
        ref[0] = new ParticipantRow(() -> {
            participantRows.remove(ref[0]);
            participantsBox.getChildren().remove(ref[0].node);
        });
        participantRows.add(ref[0]);
        participantsBox.getChildren().add(ref[0].node);
        ref[0].firstNameField.requestFocus();
    }

    // ================================================================
    // Card builders
    // ================================================================

    private VBox buildFormCard(String title, Feather icon, String color, GridPane grid) {
        String bg     = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String border = tm.isDark() ? "#383838" : "#dedede";

        VBox card = new VBox(0);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; " +
            "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;", bg, border));

        Region accent = new Region();
        accent.setPrefWidth(3); accent.setMinWidth(3);
        accent.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 2 0 0 0;", color));

        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(14);
        fi.setIconColor(Color.web(color));

        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        HBox headerInner = new HBox(8, fi, label);
        headerInner.setAlignment(Pos.CENTER_LEFT);
        headerInner.setPadding(new Insets(11, 16, 9, 12));
        HBox.setHgrow(headerInner, Priority.ALWAYS);

        HBox header = new HBox(0, accent, headerInner);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(String.format(
            "-fx-border-color: transparent transparent %s transparent; " +
            "-fx-border-width: 0 0 1 0; -fx-background-radius: 8 8 0 0;", border));

        card.getChildren().addAll(header, grid);
        return card;
    }

    private VBox buildCollapsibleCard(String title, Feather icon, String color, Node content, boolean expanded) {
        String bg        = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String border    = tm.isDark() ? "#383838" : "#dedede";
        String mutedClr  = tm.isDark() ? "#5a5a5a" : "#aaaaaa";
        String hoverBg   = tm.isDark() ? "#ffffff07" : "#0000000a";

        VBox card = new VBox(0);
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s; " +
            "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;", bg, border));

        Region accent = new Region();
        accent.setPrefWidth(3); accent.setMinWidth(3);
        accent.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 2 0 0 0;", color));

        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(14);
        fi.setIconColor(Color.web(color));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        FontIcon arrowIcon = new FontIcon(expanded ? Feather.CHEVRON_UP : Feather.CHEVRON_DOWN);
        arrowIcon.setIconSize(13);
        arrowIcon.setIconColor(Color.web(mutedClr));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String expandedStyle  = String.format(
            "-fx-cursor: hand; -fx-border-color: transparent transparent %s transparent; " +
            "-fx-border-width: 0 0 1 0; -fx-background-radius: 8 8 0 0;", border);
        String collapsedStyle = "-fx-cursor: hand; -fx-border-color: transparent; -fx-border-width: 0; -fx-background-radius: 8;";

        HBox headerInner = new HBox(8, fi, titleLabel, spacer, arrowIcon);
        headerInner.setAlignment(Pos.CENTER_LEFT);
        headerInner.setPadding(new Insets(11, 14, 9, 12));
        HBox.setHgrow(headerInner, Priority.ALWAYS);

        HBox header = new HBox(0, accent, headerInner);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(expanded ? expandedStyle : collapsedStyle);

        content.setVisible(expanded);
        content.setManaged(expanded);

        header.setOnMouseClicked(e -> {
            boolean now = !content.isVisible();
            content.setVisible(now);
            content.setManaged(now);
            arrowIcon.setIconCode(now ? Feather.CHEVRON_UP : Feather.CHEVRON_DOWN);
            header.setStyle(now ? expandedStyle : collapsedStyle);
        });
        header.setOnMouseEntered(e -> header.setStyle(
            (content.isVisible() ? expandedStyle : collapsedStyle) + " -fx-background-color: " + hoverBg + ";"));
        header.setOnMouseExited(e ->
            header.setStyle(content.isVisible() ? expandedStyle : collapsedStyle));

        card.getChildren().addAll(header, content);
        return card;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private GridPane buildGrid(ColumnConstraints... cols) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(11);
        grid.setPadding(new Insets(14, 20, 16, 20));
        grid.getColumnConstraints().addAll(cols);
        return grid;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.NORMAL, 12));
        label.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + ";");
        return label;
    }

    private static String trimOrEmpty(String s) { return s == null ? "" : s.trim(); }

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
