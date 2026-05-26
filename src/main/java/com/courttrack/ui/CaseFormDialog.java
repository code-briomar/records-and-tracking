package com.courttrack.ui;

import com.courttrack.model.CourtCase;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaseFormDialog extends Dialog<CourtCase> {

    // ---------------------------------------------------------------
    // Public types (used by CaseListView)
    // ---------------------------------------------------------------

    public record ParticipantEntry(String firstName, String lastName,
                                   String nationalId, String roleType) {}

    private static final class ParticipantRow {
        final TextField firstNameField  = new TextField();
        final TextField lastNameField   = new TextField();
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
                    "Accused", "Witness", "Victim", "Complainant", "Other"));
            roleBox.setValue("Accused");
            roleBox.setPrefWidth(130);
            FontIcon x = new FontIcon(Feather.X);
            x.setIconSize(13); x.setIconColor(Color.web("#eb5757"));
            Button del = new Button(); del.setGraphic(x);
            del.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-cursor: hand;");
            del.setOnAction(e -> onDelete.run());
            node = new HBox(8, firstNameField, lastNameField, nationalIdField, roleBox, del);
            node.setAlignment(Pos.CENTER_LEFT);
        }
    }

    // ---------------------------------------------------------------
    // Category prefixes
    // ---------------------------------------------------------------

    private static final Map<String, String> CATEGORY_PREFIXES = Map.of(
            "Civil", "CV-", "Criminal", "CR-", "Succession", "SC-",
            "Children", "CH-", "Traffic", "TR-", "Other", "OT-");

    // ---------------------------------------------------------------
    // Section 1 — Case Information (always open)
    // ---------------------------------------------------------------
    private final TextField     caseNumberField  = new TextField();
    private final ComboBox<String> categoryBox   = new ComboBox<>(FXCollections.observableArrayList(
            "Civil", "Criminal", "Succession", "Children", "Traffic", "Other"));
    private final TextField     caseTitleField   = new TextField();
    private final ComboBox<String> statusBox     = new ComboBox<>(FXCollections.observableArrayList(
            "Registered", "Mention", "Hearing", "Ruling", "Appeal", "Closed"));
    private final DatePicker    filingDatePicker = new DatePicker();

    // Transition notes (shuffled predefs)
    private final Label transitionNotesTitleLabel = new Label("Select Transition Note *");
    private final VBox transitionNotesContainer = new VBox(6);
    private final ToggleGroup transitionNotesGroup = new ToggleGroup();

    // ---------------------------------------------------------------
    // Section 2 — Case Description
    // ---------------------------------------------------------------
    private final TextArea  descriptionArea      = new TextArea();
    private final TextArea  evidenceSummaryArea  = new TextArea();
    private final TextArea  chargeDescArea       = new TextArea();
    private final TextField applicableLawField   = new TextField();

    // ---------------------------------------------------------------
    // Section 3 — Parties
    // ---------------------------------------------------------------
    private final TextField accusedNameField          = new TextField();
    private final TextField complainantNameField      = new TextField();
    private final TextField prosecutionCounselField   = new TextField();
    private final TextField defenseWitnessesField     = new TextField();
    private final TextField prosecutionWitnessesField = new TextField();

    // ---------------------------------------------------------------
    // Section 4 — Court Information
    // ---------------------------------------------------------------
    private final ComboBox<String> courtNameBox = new ComboBox<>(FXCollections.observableArrayList(
            "Kilungu Law Courts", "Makueni Law Courts", "Machakos Law Courts",
            "Nairobi Magistrates Court", "Mombasa Magistrates Court"));
    private final ComboBox<String> judgeNameBox = new ComboBox<>(FXCollections.observableArrayList(
            "Hon. R. Mutuku", "Hon. J. Mutua", "Hon. A. Wambua",
            "Hon. M. Kioko", "Hon. P. Nzomo", "Hon. C. Mwangi",
            "Hon. E. Njeru", "Hon. D. Mumo"));
    private final DatePicker    judgmentDatePicker = new DatePicker();
    private final TextField     courtAssistantField = new TextField();

    // ---------------------------------------------------------------
    // Section 5 — Hearing
    // ---------------------------------------------------------------
    private final TextField hearingDatesField = new TextField();

    // ---------------------------------------------------------------
    // Section 6 — Outcome
    // ---------------------------------------------------------------
    private final ComboBox<String> verdictBox = new ComboBox<>(FXCollections.observableArrayList(
            "", "Convicted", "Acquitted", "Judgment for Plaintiff",
            "Judgment for Defendant", "Dismissed"));
    private final TextArea  sentenceArea       = new TextArea();
    private final TextArea  mitigationArea     = new TextArea();
    private final ComboBox<String> appealStatusBox = new ComboBox<>(FXCollections.observableArrayList(
            "None", "Pending", "Allowed", "Dismissed", "Withdrawn"));
    private final TextArea  offenderHistoryArea = new TextArea();

    // ---------------------------------------------------------------
    // Section 7 — Documents
    // ---------------------------------------------------------------
    private final List<File> selectedDocuments = new ArrayList<>();
    private VBox documentsListBox;

    // ---------------------------------------------------------------
    // Participants (kept for CaseListView compatibility)
    // ---------------------------------------------------------------
    private final List<ParticipantRow> participantRows = new ArrayList<>();
    private VBox participantsBox;

    private boolean addAnother = false;
    private final ThemeManager tm = ThemeManager.getInstance();

    // ---------------------------------------------------------------
    // Public accessors
    // ---------------------------------------------------------------

    public boolean isAddAnother() { return addAnother; }

    public List<File> getSelectedDocuments() { return selectedDocuments; }

    public List<ParticipantEntry> getParticipantsToCreate() {
        return participantRows.stream()
                .filter(r -> !r.firstNameField.getText().trim().isEmpty())
                .map(r -> new ParticipantEntry(
                        r.firstNameField.getText().trim(),
                        r.lastNameField.getText().trim(),
                        r.nationalIdField.getText().trim(),
                        r.roleBox.getValue() != null ? r.roleBox.getValue() : "Accused"))
                .toList();
    }

    // ================================================================
    // Constructor
    // ================================================================

    public CaseFormDialog(CourtCase existing) {
        setTitle(existing == null ? "New Case" : "Edit Case");
        setHeaderText(null);
        setResizable(true);
        initModality(Modality.APPLICATION_MODAL);

        configureFields();
        ColumnConstraints lbl1 = cc(115), fld1 = ccGrow(), lbl2 = cc(90), fld2 = ccGrow();

        // ============================================================
        // Section 1: Case Information — always open
        // ============================================================
        GridPane g1 = grid(lbl1, fld1, lbl2, fld2);
        int r = 0;
        g1.add(lbl("Case Number *"), 0, r); g1.add(caseNumberField, 1, r);
        g1.add(lbl("Case Type *"),   2, r); g1.add(categoryBox,      3, r++);
        g1.add(lbl("Case Title"),    0, r);
        GridPane.setColumnSpan(caseTitleField, 3);
        g1.add(caseTitleField, 1, r++);
        g1.add(lbl("Status *"),     0, r); g1.add(statusBox,        1, r);
        g1.add(lbl("Date Filed *"), 2, r); g1.add(filingDatePicker,  3, r);

        r++;
        g1.add(transitionNotesTitleLabel, 0, r);
        GridPane.setColumnSpan(transitionNotesContainer, 3);
        g1.add(transitionNotesContainer, 1, r);

        if (existing != null) {
            transitionNotesTitleLabel.setVisible(false);
            transitionNotesTitleLabel.setManaged(false);
            transitionNotesContainer.setVisible(false);
            transitionNotesContainer.setManaged(false);

            statusBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                boolean changed = !java.util.Objects.equals(existing.getCaseStatus(), newVal);
                transitionNotesTitleLabel.setVisible(changed);
                transitionNotesTitleLabel.setManaged(changed);
                transitionNotesContainer.setVisible(changed);
                transitionNotesContainer.setManaged(changed);

                if (changed) {
                    transitionNotesContainer.getChildren().clear();
                    transitionNotesGroup.getToggles().clear();
                    java.util.List<String> notes = com.courttrack.util.WorkflowPredefs.getShuffledNotesFor(newVal);
                    for (String note : notes) {
                        RadioButton rb = new RadioButton(note);
                        rb.setWrapText(true);
                        rb.setMaxWidth(480);
                        rb.setToggleGroup(transitionNotesGroup);
                        rb.setStyle("-fx-font-size: 12px;");
                        transitionNotesContainer.getChildren().add(rb);
                    }
                    if (!transitionNotesContainer.getChildren().isEmpty()) {
                        ((RadioButton) transitionNotesContainer.getChildren().get(0)).setSelected(true);
                    }
                }

                if (getDialogPane().getScene() != null && getDialogPane().getScene().getWindow() != null) {
                    getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        } else {
            transitionNotesTitleLabel.setVisible(false);
            transitionNotesTitleLabel.setManaged(false);
            transitionNotesContainer.setVisible(false);
            transitionNotesContainer.setManaged(false);
        }

        VBox sec1 = buildAlwaysOpenCard("Case Information", Feather.BRIEFCASE, g1, tm.accentBlue());

        // ============================================================
        // Section 2: Case Description
        // ============================================================
        GridPane g2 = grid(lbl1, fld1, lbl2, fld2);
        r = 0;
        g2.add(lbl("Description"), 0, r);
        GridPane.setColumnSpan(descriptionArea, 3);   g2.add(descriptionArea, 1, r++);
        g2.add(lbl("Evidence"),    0, r);
        GridPane.setColumnSpan(evidenceSummaryArea, 3); g2.add(evidenceSummaryArea, 1, r++);
        g2.add(lbl("Charges"),     0, r);
        GridPane.setColumnSpan(chargeDescArea, 3);    g2.add(chargeDescArea, 1, r++);
        g2.add(lbl("Applicable Law"), 0, r);
        GridPane.setColumnSpan(applicableLawField, 3); g2.add(applicableLawField, 1, r);
        VBox sec2 = buildCollapsibleCard("Case Description", Feather.FILE_TEXT, g2, existing != null, tm.accentGreen());

        // ============================================================
        // Section 3: Parties
        // ============================================================
        GridPane g3 = grid(lbl1, fld1, lbl2, fld2);
        r = 0;
        g3.add(lbl("Accused Name"), 0, r);
        GridPane.setColumnSpan(accusedNameField, 3);  g3.add(accusedNameField, 1, r++);
        g3.add(lbl("Complainant"), 0, r);
        GridPane.setColumnSpan(complainantNameField, 3); g3.add(complainantNameField, 1, r++);
        g3.add(lbl("Prosecutor"), 0, r);
        GridPane.setColumnSpan(prosecutionCounselField, 3); g3.add(prosecutionCounselField, 1, r++);
        g3.add(lbl("Defense Witnesses"), 0, r);
        GridPane.setColumnSpan(defenseWitnessesField, 3); g3.add(defenseWitnessesField, 1, r++);
        g3.add(lbl("Prosecution Witnesses"), 0, r);
        GridPane.setColumnSpan(prosecutionWitnessesField, 3); g3.add(prosecutionWitnessesField, 1, r);
        VBox sec3 = buildCollapsibleCard("Parties", Feather.USERS, g3, existing != null, tm.accentOrange());

        // ============================================================
        // Section 4: Court Information
        // ============================================================
        GridPane g4 = grid(lbl1, fld1, lbl2, fld2);
        r = 0;
        g4.add(lbl("Court Name"),      0, r); g4.add(courtNameBox,      1, r);
        g4.add(lbl("Judge / Magistrate"), 2, r); g4.add(judgeNameBox,  3, r++);
        g4.add(lbl("Date of Judgment"), 0, r); g4.add(judgmentDatePicker, 1, r);
        g4.add(lbl("Court Assistant"),  2, r); g4.add(courtAssistantField, 3, r);
        VBox sec4 = buildCollapsibleCard("Court Information", Feather.AWARD, g4, existing != null, tm.accentPurple());

        // ============================================================
        // Section 5: Hearing
        // ============================================================
        GridPane g5 = grid(lbl1, fld1, lbl2, fld2);
        g5.add(lbl("Hearing Dates"), 0, 0);
        GridPane.setColumnSpan(hearingDatesField, 3); g5.add(hearingDatesField, 1, 0);
        VBox sec5 = buildCollapsibleCard("Hearing", Feather.CALENDAR, g5, existing != null, "#c9a227");

        // ============================================================
        // Section 6: Outcome
        // ============================================================
        GridPane g6 = grid(lbl1, fld1, lbl2, fld2);
        r = 0;
        g6.add(lbl("Verdict"), 0, r); g6.add(verdictBox, 1, r);
        g6.add(lbl("Appeal"),  2, r); g6.add(appealStatusBox, 3, r++);
        g6.add(lbl("Sentence"), 0, r);
        GridPane.setColumnSpan(sentenceArea, 3); g6.add(sentenceArea, 1, r++);
        g6.add(lbl("Mitigation"), 0, r);
        GridPane.setColumnSpan(mitigationArea, 3); g6.add(mitigationArea, 1, r++);
        g6.add(lbl("Offender History"), 0, r);
        GridPane.setColumnSpan(offenderHistoryArea, 3); g6.add(offenderHistoryArea, 1, r);
        VBox sec6 = buildCollapsibleCard("Case Outcome", Feather.CHECK_SQUARE, g6, existing != null, tm.accentRed());

        // ============================================================
        // Section 7: Documents
        // ============================================================
        documentsListBox = new VBox(6);
        Label docHint = new Label("Attach PDF, images, or Word documents.");
        docHint.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + "; -fx-font-size: 12;");
        Button browseBtn = new Button("+ Attach Files");
        browseBtn.setStyle(String.format(
                "-fx-background-color: transparent; -fx-text-fill: %s; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 12; -fx-cursor: hand;",
                tm.accentBlue(), tm.accentBlue()));
        browseBtn.setOnAction(e -> openFilePicker());
        VBox docContent = new VBox(10, docHint, browseBtn, documentsListBox);
        docContent.setPadding(new Insets(14, 20, 16, 20));
        VBox sec7 = buildCollapsibleCard("Documents", Feather.PAPERCLIP, docContent, false, tm.accentBlue());

        // ============================================================
        // Participants
        // ============================================================
        participantsBox = new VBox(8);
        Button addPartBtn = new Button("+ Add Participant");
        addPartBtn.setOnAction(e -> addParticipantRow());
        addPartBtn.setStyle(String.format(
                "-fx-background-color: transparent; -fx-text-fill: %s; -fx-border-color: %s;" +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 12; -fx-cursor: hand;",
                tm.accentBlue(), tm.accentBlue()));
        VBox partContent = new VBox(10, participantsBox, addPartBtn);
        partContent.setPadding(new Insets(14, 20, 16, 20));
        VBox sec8 = buildCollapsibleCard("Participants", Feather.USER_PLUS, partContent, false, "#0f7b6c");

        // ============================================================
        // Assemble scroll content
        // ============================================================
        String bg = tm.isDark() ? "#161616" : "#f4f4f4";
        VBox content = new VBox(10, sec1, sec2, sec3, sec4, sec5, sec6, sec7, sec8);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + bg + ";");

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(720);
        scroll.setPrefHeight(560);
        scroll.setStyle("-fx-background-color: " + bg + "; -fx-background: " + bg + ";");

        // ============================================================
        // Populate fields
        // ============================================================
        if (existing != null) {
            populate(existing);
        } else {
            statusBox.setValue("Active");
            filingDatePicker.setValue(LocalDate.now());
            courtNameBox.setValue("Kilungu Law Courts");
            appealStatusBox.setValue("None");
        }

        // Auto-prefix case number when category changes (new cases only)
        if (existing == null) {
            categoryBox.valueProperty().addListener((obs, old, nv) -> {
                if (nv == null) return;
                String cur = caseNumberField.getText();
                boolean blank = cur.isEmpty() || CATEGORY_PREFIXES.values().stream().anyMatch(cur::equals);
                if (blank) {
                    String p = CATEGORY_PREFIXES.getOrDefault(nv, "");
                    caseNumberField.setText(p);
                    caseNumberField.positionCaret(p.length());
                }
            });
        }

        // ============================================================
        // Dialog chrome
        // ============================================================
        getDialogPane().setContent(scroll);

        ButtonType ADD_ANOTHER = new ButtonType("Save & Add Another", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ADD_ANOTHER, ButtonType.CANCEL);

        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        stage.setMinWidth(720);
        stage.setMinHeight(620);

        // Style button bar, scrollbar, and fill full width after skin is applied
        setOnShown(ev -> {
            Node buttonBar = getDialogPane().lookup(".button-bar");
            if (buttonBar != null) {
                String btnBg  = tm.isDark() ? "#1c1c1c" : "#f0f0f0";
                String btnBrd = tm.isDark() ? "#333333" : "#d0d0d0";
                buttonBar.setStyle(String.format(
                        "-fx-background-color: %s; -fx-border-color: %s transparent transparent transparent;" +
                        "-fx-border-width: 1 0 0 0; -fx-padding: 10 16;", btnBg, btnBrd));
            }
            String thumbClr = tm.isDark() ? "#505050" : "#b0b0b0";
            String trackClr = tm.isDark() ? "#222222" : "#e0e0e0";
            scroll.lookupAll(".scroll-bar").forEach(sb -> {
                sb.setStyle("-fx-background-color: transparent;");
                sb.lookupAll(".track").forEach(t ->
                        t.setStyle("-fx-background-color: " + trackClr + "; -fx-background-radius: 0;"));
                sb.lookupAll(".thumb").forEach(t ->
                        t.setStyle("-fx-background-color: " + thumbClr + "; -fx-background-radius: 4;"));
                sb.lookupAll(".increment-button, .decrement-button").forEach(b ->
                        b.setStyle("-fx-background-color: transparent; -fx-padding: 0;"));
            });
        });

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText(existing == null ? "Add Case" : "Save Changes");
        okBtn.getStyleClass().add("accent");

        javafx.event.EventHandler<javafx.event.ActionEvent> validate = ev -> {
            if (caseNumberField.getText().trim().isEmpty()) {
                alert("Case number is required."); ev.consume();
            } else if (categoryBox.getValue() == null) {
                alert("Case type is required."); ev.consume();
            } else if (statusBox.getValue() == null) {
                alert("Status is required."); ev.consume();
            } else if (filingDatePicker.getValue() == null) {
                alert("Filing date is required."); ev.consume();
            }
        };
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, validate);
        ((Button) getDialogPane().lookupButton(ADD_ANOTHER))
                .addEventFilter(javafx.event.ActionEvent.ACTION, validate);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK || bt == ADD_ANOTHER) {
                addAnother = bt == ADD_ANOTHER;
                return buildCase(existing);
            }
            return null;
        });

        // Ctrl+Enter → save
        getDialogPane().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) {
                okBtn.fire(); e.consume();
            }
        });

        Platform.runLater(() -> {
            caseNumberField.requestFocus();
            setupEnterNav();
        });
    }

    // ================================================================
    // Field configuration
    // ================================================================

    private void configureFields() {
        caseNumberField.setPromptText("e.g. CR-009/2024");
        categoryBox.setMaxWidth(Double.MAX_VALUE);
        categoryBox.setPromptText("Select type");
        caseTitleField.setPromptText("e.g. Republic v. John Doe");
        statusBox.setMaxWidth(Double.MAX_VALUE);
        filingDatePicker.setMaxWidth(Double.MAX_VALUE);

        descriptionArea.setPromptText("Detailed case description...");
        descriptionArea.setPrefRowCount(3); descriptionArea.setWrapText(true);
        evidenceSummaryArea.setPromptText("Summary of evidence...");
        evidenceSummaryArea.setPrefRowCount(3); evidenceSummaryArea.setWrapText(true);
        chargeDescArea.setPromptText("Describe the charge...");
        chargeDescArea.setPrefRowCount(3); chargeDescArea.setWrapText(true);
        applicableLawField.setPromptText("e.g. Penal Code s. 304");

        accusedNameField.setPromptText("Full name, including aliases");
        complainantNameField.setPromptText("Complainant full name");
        prosecutionCounselField.setPromptText("Prosecution counsel name");
        defenseWitnessesField.setPromptText("Names separated by commas");
        prosecutionWitnessesField.setPromptText("Names separated by commas");

        courtNameBox.setEditable(true); courtNameBox.setMaxWidth(Double.MAX_VALUE);
        judgeNameBox.setEditable(true); judgeNameBox.setMaxWidth(Double.MAX_VALUE);
        judgeNameBox.setPromptText("Select or type name");
        judgmentDatePicker.setMaxWidth(Double.MAX_VALUE);
        courtAssistantField.setPromptText("Court assistant full name");

        hearingDatesField.setPromptText("e.g. 2024-01-15, 2024-02-10");

        verdictBox.setEditable(true); verdictBox.setMaxWidth(Double.MAX_VALUE);
        verdictBox.setPromptText("Select or type verdict");
        sentenceArea.setPromptText("Sentence details...");
        sentenceArea.setPrefRowCount(2); sentenceArea.setWrapText(true);
        mitigationArea.setPromptText("Mitigation notes...");
        mitigationArea.setPrefRowCount(2); mitigationArea.setWrapText(true);
        appealStatusBox.setMaxWidth(Double.MAX_VALUE);
        offenderHistoryArea.setPromptText("Offender history...");
        offenderHistoryArea.setPrefRowCount(2); offenderHistoryArea.setWrapText(true);
    }

    private void populate(CourtCase c) {
        set(caseNumberField, c.getCaseNumber());
        categoryBox.setValue(c.getCaseCategory());
        set(caseTitleField, c.getCaseTitle());
        statusBox.setValue(mapStatus(c.getCaseStatus()));
        filingDatePicker.setValue(c.getFilingDate());

        set(descriptionArea, c.getDescription());
        set(evidenceSummaryArea, c.getEvidenceSummary());
        set(chargeDescArea, c.getChargeParticulars());
        set(applicableLawField, c.getApplicableLaw());

        set(accusedNameField, c.getAccusedName());
        set(complainantNameField, c.getComplainantName());
        set(prosecutionCounselField, c.getProsecutionCounsel());
        set(defenseWitnessesField, c.getDefenseWitnesses());
        set(prosecutionWitnessesField, c.getProsecutionWitnesses());

        courtNameBox.setValue(nvl(c.getCourtName(), "Kilungu Law Courts"));
        judgeNameBox.setValue(c.getJudgeName());
        judgmentDatePicker.setValue(c.getDateOfJudgment());
        set(courtAssistantField, c.getCourtAssistant());

        set(hearingDatesField, c.getHearingDates());

        verdictBox.setValue(mapVerdict(c.getChargeVerdict()));
        set(sentenceArea, c.getSentence());
        set(mitigationArea, c.getMitigationNotes());
        appealStatusBox.setValue(mapAppeal(c.getAppealStatus()));
        set(offenderHistoryArea, c.getOffenderHistory());
    }

    private CourtCase buildCase(CourtCase existing) {
        CourtCase c = existing != null ? existing : new CourtCase();
        c.setCaseNumber(caseNumberField.getText().trim());
        c.setCaseCategory(categoryBox.getValue());
        c.setCaseTitle(trim(caseTitleField.getText()));
        c.setCaseStatus(statusBox.getValue());

        if (existing != null && !java.util.Objects.equals(existing.getCaseStatus(), statusBox.getValue())) {
            RadioButton selectedRb = (RadioButton) transitionNotesGroup.getSelectedToggle();
            if (selectedRb != null) {
                c.setTransitionNotes(selectedRb.getText());
            } else {
                c.setTransitionNotes("Stage transitioned to " + statusBox.getValue());
            }
        } else {
            c.setTransitionNotes(null);
        }

        c.setFilingDate(filingDatePicker.getValue());

        c.setDescription(trim(descriptionArea.getText()));
        c.setEvidenceSummary(trim(evidenceSummaryArea.getText()));
        c.setChargeParticulars(trim(chargeDescArea.getText()));
        c.setApplicableLaw(trim(applicableLawField.getText()));

        c.setAccusedName(trim(accusedNameField.getText()));
        c.setComplainantName(trim(complainantNameField.getText()));
        c.setProsecutionCounsel(trim(prosecutionCounselField.getText()));
        c.setDefenseWitnesses(trim(defenseWitnessesField.getText()));
        c.setProsecutionWitnesses(trim(prosecutionWitnessesField.getText()));

        c.setCourtName(nvl(courtNameBox.getEditor().getText().trim(), "Kilungu Law Courts"));
        String judgeText = judgeNameBox.getEditor().getText().trim();
        c.setJudgeName(judgeText.isEmpty() ? null : judgeText);
        c.setDateOfJudgment(judgmentDatePicker.getValue());
        c.setCourtAssistant(trim(courtAssistantField.getText()));

        c.setHearingDates(trim(hearingDatesField.getText()));

        String v = verdictBox.getEditor().getText().trim();
        c.setChargeVerdict(v.isEmpty() ? null : v);
        c.setSentence(trim(sentenceArea.getText()));
        c.setMitigationNotes(trim(mitigationArea.getText()));
        String appeal = appealStatusBox.getValue();
        c.setAppealStatus("None".equals(appeal) || appeal == null ? null : appeal);
        c.setOffenderHistory(trim(offenderHistoryArea.getText()));

        return c;
    }

    // ================================================================
    // Enter key navigation (TextFields only — TextAreas keep Enter=newline)
    // ================================================================

    private void setupEnterNav() {
        List<TextField> nav = List.of(
                caseNumberField,
                caseTitleField,
                filingDatePicker.getEditor(),
                applicableLawField,
                accusedNameField, complainantNameField, prosecutionCounselField,
                defenseWitnessesField, prosecutionWitnessesField,
                courtNameBox.getEditor(),
                judgeNameBox.getEditor(),
                judgmentDatePicker.getEditor(),
                courtAssistantField,
                hearingDatesField,
                verdictBox.getEditor()
        );
        for (int i = 0; i < nav.size() - 1; i++) {
            final TextField next = nav.get(i + 1);
            nav.get(i).setOnAction(e -> next.requestFocus());
        }
    }

    // ================================================================
    // Document picker
    // ================================================================

    private void openFilePicker() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Case Documents");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported", "*.pdf", "*.png", "*.jpg", "*.jpeg", "*.docx", "*.doc"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.doc")
        );
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files == null) return;
        for (File f : files) {
            if (!selectedDocuments.contains(f)) {
                selectedDocuments.add(f);
                addDocRow(f);
            }
        }
    }

    private void addDocRow(File f) {
        FontIcon icon = new FontIcon(Feather.FILE);
        icon.setIconSize(12); icon.setIconColor(Color.web(tm.accentBlue()));
        Label name = new Label(f.getName());
        name.setStyle("-fx-font-size: 12;");
        HBox.setHgrow(name, Priority.ALWAYS);
        FontIcon x = new FontIcon(Feather.X);
        x.setIconSize(11); x.setIconColor(Color.web("#eb5757"));
        Button removeBtn = new Button(); removeBtn.setGraphic(x);
        removeBtn.setStyle("-fx-background-color: transparent; -fx-padding: 2 4; -fx-cursor: hand;");
        HBox row = new HBox(6, icon, name, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        removeBtn.setOnAction(e -> {
            selectedDocuments.remove(f);
            documentsListBox.getChildren().remove(row);
        });
        documentsListBox.getChildren().add(row);
    }

    // ================================================================
    // Participant rows
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

    private VBox buildAlwaysOpenCard(String title, Feather icon, Node content, String accentColor) {
        String cardBg  = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String border  = tm.isDark() ? "#383838" : "#dedede";

        VBox card = new VBox(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s;" +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;", cardBg, border));

        Region accent = accentBar(accentColor);
        FontIcon fi = new FontIcon(icon); fi.setIconSize(14); fi.setIconColor(Color.web(accentColor));
        Label label = new Label(title); label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        HBox inner = new HBox(8, fi, label);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(11, 14, 9, 12));
        HBox.setHgrow(inner, Priority.ALWAYS);

        HBox header = new HBox(0, accent, inner);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(String.format(
                "-fx-border-color: transparent transparent %s transparent;" +
                "-fx-border-width: 0 0 1 0; -fx-background-radius: 8 8 0 0;", border));

        card.getChildren().addAll(header, content);
        return card;
    }

    private VBox buildCollapsibleCard(String title, Feather icon, Node content, boolean expanded, String accentColor) {
        String cardBg  = tm.isDark() ? "#1e1e1e" : "#ffffff";
        String border  = tm.isDark() ? "#383838" : "#dedede";
        String muted   = tm.isDark() ? "#5a5a5a" : "#aaaaaa";
        String hover   = tm.isDark() ? "#ffffff07" : "#0000000a";

        VBox card = new VBox(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: %s;" +
                "-fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;", cardBg, border));

        Region accent = accentBar(accentColor);
        FontIcon fi = new FontIcon(icon); fi.setIconSize(14); fi.setIconColor(Color.web(accentColor));
        Label titleLabel = new Label(title); titleLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        FontIcon arrow = new FontIcon(expanded ? Feather.CHEVRON_UP : Feather.CHEVRON_DOWN);
        arrow.setIconSize(13); arrow.setIconColor(Color.web(muted));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox inner = new HBox(8, fi, titleLabel, spacer, arrow);
        inner.setAlignment(Pos.CENTER_LEFT);
        inner.setPadding(new Insets(11, 14, 9, 12));
        HBox.setHgrow(inner, Priority.ALWAYS);

        HBox header = new HBox(0, accent, inner);
        header.setAlignment(Pos.CENTER_LEFT);

        String expStyle = String.format(
                "-fx-cursor: hand; -fx-border-color: transparent transparent %s transparent;" +
                "-fx-border-width: 0 0 1 0; -fx-background-radius: 8 8 0 0;", border);
        String colStyle = "-fx-cursor: hand; -fx-border-color: transparent; -fx-background-radius: 8;";

        boolean[] open = {expanded};
        content.setVisible(open[0]); content.setManaged(open[0]);
        header.setStyle(open[0] ? expStyle : colStyle);

        header.setOnMouseClicked(e -> {
            open[0] = !open[0];
            content.setVisible(open[0]); content.setManaged(open[0]);
            arrow.setIconCode(open[0] ? Feather.CHEVRON_UP : Feather.CHEVRON_DOWN);
            header.setStyle(open[0] ? expStyle : colStyle);
        });
        header.setOnMouseEntered(e -> header.setStyle(
                (open[0] ? expStyle : colStyle) + " -fx-background-color: " + hover + ";"));
        header.setOnMouseExited(e -> header.setStyle(open[0] ? expStyle : colStyle));

        card.getChildren().addAll(header, content);
        return card;
    }

    // ================================================================
    // Layout helpers
    // ================================================================

    private GridPane grid(ColumnConstraints... cols) {
        GridPane g = new GridPane();
        g.setHgap(14); g.setVgap(11);
        g.setPadding(new Insets(14, 20, 16, 20));
        g.getColumnConstraints().addAll(cols);
        return g;
    }

    private ColumnConstraints cc(double w) {
        ColumnConstraints c = new ColumnConstraints();
        c.setPrefWidth(w); c.setMinWidth(w); return c;
    }

    private ColumnConstraints ccGrow() {
        ColumnConstraints c = new ColumnConstraints();
        c.setHgrow(Priority.ALWAYS); return c;
    }

    private Label lbl(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.NORMAL, 12));
        l.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + ";");
        return l;
    }

    private Region accentBar(String color) {
        Region r = new Region();
        r.setPrefWidth(3); r.setMinWidth(3);
        r.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 2 0 0 0;", color));
        return r;
    }

    // ================================================================
    // Value mapping (old → new format)
    // ================================================================

    private static String mapStatus(String s) {
        if (s == null) return "Registered";
        return switch (s) {
            case "OPEN"      -> "Registered";
            case "CLOSED"    -> "Closed";
            case "Active"    -> "Mention";
            case "Pending"   -> "Hearing";
            case "Review"    -> "Ruling";
            case "ADJOURNED" -> "Hearing";
            case "DISMISSED" -> "Closed";
            case "SETTLED"   -> "Closed";
            default          -> s; // already new format
        };
    }

    private static String mapVerdict(String v) {
        if (v == null) return "";
        return switch (v) {
            case "CONVICTED"               -> "Convicted";
            case "ACQUITTED"               -> "Acquitted";
            case "JUDGEMENT_FOR_PLAINTIFF" -> "Judgment for Plaintiff";
            case "JUDGEMENT_FOR_DEFENDANT" -> "Judgment for Defendant";
            case "DISMISSED"               -> "Dismissed";
            default                        -> v;
        };
    }

    private static String mapAppeal(String a) {
        if (a == null || a.isBlank()) return "None";
        return switch (a) {
            case "NONE"    -> "None";
            case "FILED"   -> "Pending";
            case "HEARD"   -> "Pending";
            case "ALLOWED" -> "Allowed";
            case "DISMISSED" -> "Dismissed";
            default        -> a;
        };
    }

    // ================================================================
    // String helpers
    // ================================================================

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    private static String nvl(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private static void set(TextInputControl t, String v) {
        if (v != null) t.setText(v);
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Validation"); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
