package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.dao.CaseStageHistoryDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CaseStageHistory;
import com.courttrack.model.Charge;
import com.courttrack.model.CourtCase;
import com.courttrack.model.Person;
import com.courttrack.repository.CaseRepository;
import com.courttrack.repository.PersonRepository;
import com.courttrack.sync.SyncCoordinator;
import com.courttrack.util.WorkflowPredefs;
import com.courttrack.util.DialogUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import com.courttrack.sync.SyncCoordinator;
import javafx.stage.Modality;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CaseDetailView {
    private final VBox root;
    private final CourtCase courtCase;
    private final CaseDao caseDao = new CaseDao();
    private final CaseStageHistoryDao stageHistoryDao = new CaseStageHistoryDao();
    private final CaseRepository caseRepo = CaseRepository.getInstance();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Runnable onBack;
    private final Consumer<Person> onViewPerson;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public CaseDetailView(CourtCase courtCase, Runnable onBack) {
        this(courtCase, onBack, null);
    }

    public CaseDetailView(CourtCase courtCase, Runnable onBack, Consumer<Person> onViewPerson) {
        this.courtCase = courtCase;
        this.onBack = onBack;
        this.onViewPerson = onViewPerson;
        this.root = new VBox(0);
        populateUI(courtCase);
    }

    private void populateUI(CourtCase c) {

        VBox content = new VBox(0);
        content.setPadding(new Insets(0));

        // --- Top bar: back + edit ---
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(24, 40, 0, 40));

        Button backBtn = new Button();
        FontIcon backIcon = new FontIcon(Feather.ARROW_LEFT);
        backIcon.setIconSize(16);
        backIcon.setIconColor(Color.web(tm.accentBlue()));
        backBtn.setGraphic(backIcon);
        backBtn.setText("Back to Cases");
        backBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 6 12;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
        """);
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(String.format("""
            -fx-background-color: %s18;
            -fx-cursor: hand;
            -fx-padding: 6 12;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
        """, tm.accentBlue())));
        backBtn.setOnMouseExited(e -> backBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-cursor: hand;
            -fx-padding: 6 12;
            -fx-font-size: 13px;
            -fx-background-radius: 6;
        """));
        backBtn.setOnAction(e -> onBack.run());

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Button editBtn = new Button("Edit Case");
        editBtn.getStyleClass().add("accent");
        final CourtCase caseRef = c;
        editBtn.setOnAction(e -> handleEdit(caseRef));

        Button transitionBtn = new Button("Transition Stage");
        transitionBtn.setStyle(String.format("""
            -fx-background-color: transparent;
            -fx-text-fill: %s;
            -fx-border-color: %s;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 6 12;
            -fx-cursor: hand;
        """, tm.accentBlue(), tm.accentBlue()));
        transitionBtn.setOnAction(e -> handleTransitionStage(caseRef));

        FontIcon delIcon = new FontIcon(Feather.TRASH_2);
        delIcon.setIconSize(14);
        delIcon.setIconColor(Color.web(tm.accentRed()));
        Button deleteBtn = new Button("Delete");
        deleteBtn.setGraphic(delIcon);
        deleteBtn.setStyle(String.format("""
            -fx-background-color: transparent;
            -fx-text-fill: %s;
            -fx-border-color: %s;
            -fx-border-radius: 6;
            -fx-background-radius: 6;
            -fx-padding: 6 12;
            -fx-cursor: hand;
        """, tm.accentRed(), tm.accentRed()));
        deleteBtn.setOnAction(e -> handleDelete(caseRef));

        topBar.getChildren().addAll(backBtn, topSpacer, deleteBtn, transitionBtn, editBtn);
        content.getChildren().add(topBar);

        // --- Header ---
        VBox header = new VBox(6);
        header.setPadding(new Insets(20, 40, 16, 40));

        Label caseNumber = new Label(or(c.getCaseNumber()));
        caseNumber.setFont(Font.font("System", FontWeight.BOLD, 28));

        if (c.getCaseTitle() != null && !c.getCaseTitle().isBlank()) {
            Label titleLabel = new Label(c.getCaseTitle());
            titleLabel.setFont(Font.font("System", 16));
            titleLabel.getStyleClass().add("text-muted");
            titleLabel.setWrapText(true);
            header.getChildren().addAll(caseNumber, titleLabel);
        } else {
            header.getChildren().add(caseNumber);
        }

        // Badges row
        HBox badges = new HBox(8);
        badges.setPadding(new Insets(8, 0, 0, 0));

        if (c.getCaseCategory() != null) {
            badges.getChildren().add(buildBadge(c.getCaseCategory(),
                categoryBg(c.getCaseCategory()), categoryColor(c.getCaseCategory())));
        }
        if (c.getCaseStatus() != null) {
            boolean isOpen = "Registered".equals(c.getCaseStatus()) || "Mention".equals(c.getCaseStatus()) || "Hearing".equals(c.getCaseStatus()) || "Ruling".equals(c.getCaseStatus()) || "Appeal".equals(c.getCaseStatus());
            badges.getChildren().add(buildBadge(c.getCaseStatus(),
                isOpen ? tm.badgeOpenBg() : tm.badgeClosedBg(),
                isOpen ? tm.badgeOpenText() : tm.badgeClosedText()));
        }
        if (c.getPriority() != null) {
            String pColor = switch (c.getPriority()) {
                case "HIGH" -> tm.accentRed();
                case "MEDIUM" -> tm.accentOrange();
                default -> tm.accentGreen();
            };
            badges.getChildren().add(buildBadge(c.getPriority() + " Priority",
                pColor + "18", pColor));
        }
        if (c.getAppealStatus() != null && !c.getAppealStatus().isBlank() && !"NONE".equals(c.getAppealStatus())) {
            badges.getChildren().add(buildBadge("Appeal: " + c.getAppealStatus(),
                tm.accentPurple() + "18", tm.accentPurple()));
        }

        header.getChildren().add(badges);
        content.getChildren().add(header);

        VBox timelineCard = buildTimelineCard(c);
        VBox.setMargin(timelineCard, new Insets(8, 40, 8, 40));
        content.getChildren().add(timelineCard);

        // --- Sections in a 2-column card layout ---
        FlowPane cards = new FlowPane();
        cards.setHgap(20);
        cards.setVgap(20);
        cards.setPadding(new Insets(8, 40, 32, 40));

        // Card 1: Case Information
        VBox caseInfoCard = buildCard("Case Information", Feather.BRIEFCASE, tm.accentBlue());
        GridPane caseGrid = buildDetailGrid();
        int row = 0;
        addDetailRow(caseGrid, row++, "Case Number", or(c.getCaseNumber()));
        addDetailRow(caseGrid, row++, "Category", or(c.getCaseCategory()));
        addDetailRow(caseGrid, row++, "Case Type", or(c.getCaseType()));
        addDetailRow(caseGrid, row++, "Court", or(c.getCourtName() != null ? c.getCourtName() : c.getCourtId()));
        addDetailRow(caseGrid, row++, "Filing Date", c.getFilingDate() != null ? c.getFilingDate().format(DATE_FMT) : "\u2014");
        addDetailRow(caseGrid, row++, "Status", or(c.getCaseStatus()));
        addDetailRow(caseGrid, row++, "Priority", or(c.getPriority()));
        caseInfoCard.getChildren().add(caseGrid);

        // Card 2: Charge Details
        VBox chargeCard = buildCard("Charge Details", Feather.FILE_TEXT, tm.accentOrange());
        GridPane chargeGrid = buildDetailGrid();
        int crow = 0;
        addDetailRow(chargeGrid, crow++, "Particulars", or(c.getChargeParticulars()));
        addDetailRow(chargeGrid, crow++, "Plea", or(c.getChargePlea()));
        addDetailRow(chargeGrid, crow++, "Verdict", c.getChargeVerdict() != null ? c.getChargeVerdict().replace("_", " ") : "\u2014");
        chargeCard.getChildren().add(chargeGrid);

        // Additional charges
        caseRepo.getCharges(c.getCaseId(), charges -> {
            Platform.runLater(() -> {
                if (charges.size() > 1) {
                    for (int i = 1; i < charges.size(); i++) {
                        Charge ch = charges.get(i);
                        Separator chSep = new Separator();
                        chSep.setPadding(new Insets(4, 0, 4, 0));
                        chargeCard.getChildren().add(chSep);

                        GridPane extraGrid = buildDetailGrid();
                        int erow = 0;
                        addDetailRow(extraGrid, erow++, "Charge " + (i + 1), or(ch.getParticulars()));
                        addDetailRow(extraGrid, erow++, "Plea", or(ch.getPlea()));
                        addDetailRow(extraGrid, erow++, "Verdict", or(ch.getVerdict()));
                        chargeCard.getChildren().add(extraGrid);
                    }
                }
            });
        });

        // Card 3: Judgment & Sentencing
        VBox judgmentCard = buildCard("Judgment & Sentencing", Feather.AWARD, tm.accentRed());
        GridPane judgGrid = buildDetailGrid();
        int jrow = 0;
        addDetailRow(judgGrid, jrow++, "Judgment Date", c.getDateOfJudgment() != null ? c.getDateOfJudgment().format(DATE_FMT) : "\u2014");
        addDetailRow(judgGrid, jrow++, "Sentence", or(c.getSentence()));
        addDetailRow(judgGrid, jrow++, "Mitigation", or(c.getMitigationNotes()));
        addDetailRow(judgGrid, jrow++, "Appeal Status", or(c.getAppealStatus()));
        judgmentCard.getChildren().add(judgGrid);

        // Card 4: Additional Details
        VBox detailCard = buildCard("Additional Details", Feather.INFO, tm.accentGreen());
        GridPane detGrid = buildDetailGrid();
        int drow = 0;
        addDetailRow(detGrid, drow++, "Prosecutor", or(c.getProsecutionCounsel()));
        addDetailRow(detGrid, drow++, "Court Assistant", or(c.getCourtAssistant()));
        addDetailRow(detGrid, drow++, "Location", or(c.getLocationOfOffence()));
        detailCard.getChildren().add(detGrid);

        // Card 5: Evidence & Hearings
        VBox evidenceCard = buildCard("Evidence & Hearings", Feather.CLIPBOARD, tm.accentPurple());
        GridPane evGrid = buildDetailGrid();
        int erow = 0;
        addDetailRow(evGrid, erow++, "Evidence", or(c.getEvidenceSummary()));
        addDetailRow(evGrid, erow++, "Hearings", or(c.getHearingDates()));
        evidenceCard.getChildren().add(evGrid);

        // Card 6: Description
        if (c.getDescription() != null && !c.getDescription().isBlank()) {
            VBox descCard = buildCard("Description", Feather.ALIGN_LEFT, tm.accentBlue());
            Label descLabel = new Label(c.getDescription());
            descLabel.setWrapText(true);
            descLabel.setFont(Font.font("System", 13));
            descLabel.setPadding(new Insets(4, 16, 12, 16));
            descCard.getChildren().add(descLabel);
            cards.getChildren().add(descCard);
        }

        // Card 7: Participants — loaded from CaseDao.getParticipants
        List<CaseParticipant> participants = caseDao.getParticipants(c.getCaseId());
        if (!participants.isEmpty()) {
            VBox partCard = buildCard("Participants", Feather.USERS, tm.accentBlue());
            VBox partList = new VBox(8);
            partList.setPadding(new Insets(10, 16, 14, 16));
            partCard.getChildren().add(partList);
            cards.getChildren().add(partCard);

            for (CaseParticipant cp : participants) {
                PersonRepository.getInstance().getById(cp.getPersonId(), person -> {
                    if (person == null) return;
                    Platform.runLater(() -> {
                        HBox personRow = new HBox(10);
                        personRow.setAlignment(Pos.CENTER_LEFT);

                        Hyperlink nameLink = new Hyperlink(person.getFirstName() + " " + person.getLastName());
                        nameLink.setFont(Font.font("System", 13));
                        if (onViewPerson != null) {
                            nameLink.setOnAction(e -> onViewPerson.accept(person));
                        }

                        if (cp.getRoleType() != null && !cp.getRoleType().isBlank()) {
                            Label roleLabel = new Label(cp.getRoleType());
                            roleLabel.setPadding(new Insets(2, 8, 2, 8));
                            roleLabel.setFont(Font.font("System", 10));
                            roleLabel.setStyle(String.format(
                                "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 4;",
                                tm.accentBlue() + "22", tm.accentBlue()));
                            personRow.getChildren().addAll(nameLink, roleLabel);
                        } else {
                            personRow.getChildren().add(nameLink);
                        }
                        partList.getChildren().add(personRow);
                    });
                });
            }
        }

        cards.getChildren().addAll(0, java.util.List.of(caseInfoCard, chargeCard, judgmentCard, detailCard, evidenceCard));

        // Set card widths to fill ~half
        for (var node : cards.getChildren()) {
            if (node instanceof VBox card) {
                card.setPrefWidth(420);
                card.setMinWidth(300);
                card.setMaxWidth(500);
            }
        }

        content.getChildren().add(cards);

        // Wrap in scroll
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().add(scrollPane);
    }

    private VBox buildCard(String title, Feather icon, String color) {
        VBox card = new VBox(0);
        card.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-border-color: %s;
            -fx-border-width: 1;
            -fx-border-radius: 8;
            -fx-background-radius: 8;
        """, tm.isDark() ? "#1e1e1e" : "#ffffff", tm.isDark() ? "#333" : "#e5e5e5"));

        FontIcon fi = new FontIcon(icon);
        fi.setIconSize(15);
        fi.setIconColor(Color.web(color));

        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));

        HBox header = new HBox(8, fi, label);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 10, 16));
        header.setStyle(String.format("-fx-border-color: transparent transparent %s transparent; -fx-border-width: 0 0 1 0;",
            tm.isDark() ? "#333" : "#e5e5e5"));

        card.getChildren().add(header);
        return card;
    }

    private VBox buildTimelineCard(CourtCase c) {
        VBox card = buildCard("Case Timeline", Feather.CLOCK, tm.accentBlue());
        List<CaseStageHistory> history = stageHistoryDao.findByCaseId(c.getCaseId());
        VBox list = new VBox(0);
        list.setPadding(new Insets(16));

        if (history.isEmpty()) {
            Label empty = new Label("No stage history has been recorded for this case yet.");
            empty.setWrapText(true);
            empty.setFont(Font.font("System", 12));
            empty.getStyleClass().add("text-muted");
            list.getChildren().add(empty);
            card.getChildren().add(list);
            return card;
        }

        for (int i = 0; i < history.size(); i++) {
            CaseStageHistory event = history.get(i);

            StackPane markerPane = new StackPane();
            markerPane.setPrefWidth(30);
            markerPane.setMinWidth(30);
            markerPane.setAlignment(Pos.TOP_CENTER);

            VBox lineBox = new VBox();
            lineBox.setAlignment(Pos.TOP_CENTER);

            Region topSegment = new Region();
            topSegment.setPrefWidth(2);
            topSegment.setMaxWidth(2);
            topSegment.setStyle("-fx-background-color: " + (tm.isDark() ? "#383838" : "#dedede") + ";");

            Region bottomSegment = new Region();
            bottomSegment.setPrefWidth(2);
            bottomSegment.setMaxWidth(2);
            bottomSegment.setStyle("-fx-background-color: " + (tm.isDark() ? "#383838" : "#dedede") + ";");

            topSegment.setMinHeight(10);
            topSegment.setPrefHeight(10);
            bottomSegment.setMinHeight(20);
            bottomSegment.setPrefHeight(20);
            VBox.setVgrow(bottomSegment, Priority.ALWAYS);

            if (history.size() > 1) {
                if (i == 0) {
                    Region topSpace = new Region();
                    topSpace.setMinHeight(10); topSpace.setPrefHeight(10);
                    lineBox.getChildren().addAll(topSpace, bottomSegment);
                } else if (i == history.size() - 1) {
                    Region bottomSpace = new Region();
                    bottomSpace.setMinHeight(20); bottomSpace.setPrefHeight(20);
                    VBox.setVgrow(bottomSpace, Priority.ALWAYS);
                    lineBox.getChildren().addAll(topSegment, bottomSpace);
                } else {
                    lineBox.getChildren().addAll(topSegment, bottomSegment);
                }
            }

            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(5);
            StackPane.setMargin(dot, new Insets(6, 0, 0, 0));

            if (i == history.size() - 1) {
                dot.setRadius(6);
                dot.setFill(Color.web(tm.accentBlue()));
                dot.setStroke(Color.web(tm.accentBlue() + "44"));
                dot.setStrokeWidth(3);
            } else {
                dot.setFill(Color.web(tm.isDark() ? "#505050" : "#b0b0b0"));
            }

            markerPane.getChildren().addAll(lineBox, dot);

            VBox contentBox = new VBox(4);
            contentBox.setPadding(new Insets(2, 0, 16, 8));
            HBox.setHgrow(contentBox, Priority.ALWAYS);

            String transition = event.getFromStatus() == null
                ? "Filed → " + event.getToStatus()
                : event.getFromStatus() + " → " + event.getToStatus();
            Label title = new Label(transition);
            title.setFont(Font.font("System", FontWeight.BOLD, 13));

            String actor = or(event.getChangedBy());
            String when = event.getChangedAt() != null
                ? event.getChangedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                : "Unknown time";
            Label meta = new Label(when + " • " + actor);
            meta.setFont(Font.font("System", 11));
            meta.getStyleClass().add("text-muted");

            contentBox.getChildren().addAll(title, meta);

            if (event.getNotes() != null && !event.getNotes().isBlank()) {
                Label notes = new Label(event.getNotes());
                notes.setWrapText(true);
                notes.setFont(Font.font("System", 12));
                notes.setPadding(new Insets(6, 10, 6, 10));

                String bubbleBg = tm.isDark() ? "#282828" : "#f5f5f5";
                String bubbleBorder = tm.isDark() ? "#383838" : "#e0e0e0";
                notes.setStyle(String.format(
                    "-fx-background-color: %s; -fx-border-color: %s; -fx-border-radius: 6; -fx-background-radius: 6; -fx-text-fill: %s;",
                    bubbleBg, bubbleBorder, tm.isDark() ? "#d0d0d0" : "#444444"
                ));
                contentBox.getChildren().add(notes);
            }

            HBox row = new HBox(0, markerPane, contentBox);
            list.getChildren().add(row);
        }

        card.getChildren().add(list);
        return card;
    }

    private GridPane buildDetailGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);
        grid.setPadding(new Insets(12, 16, 14, 16));

        ColumnConstraints keyCol = new ColumnConstraints();
        keyCol.setPrefWidth(110);
        keyCol.setMinWidth(90);
        ColumnConstraints valCol = new ColumnConstraints();
        valCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyCol, valCol);

        return grid;
    }

    private void addDetailRow(GridPane grid, int row, String label, String value) {
        Label keyLabel = new Label(label);
        keyLabel.setFont(Font.font("System", 12));
        keyLabel.getStyleClass().add("text-muted");

        Label valLabel = new Label(value);
        valLabel.setFont(Font.font("System", 13));
        valLabel.setWrapText(true);

        grid.add(keyLabel, 0, row);
        grid.add(valLabel, 1, row);
    }

    private Label buildBadge(String text, String bg, String textColor) {
        Label badge = new Label(text);
        badge.setPadding(new Insets(4, 12, 4, 12));
        badge.setFont(Font.font("System", FontWeight.MEDIUM, 11));
        badge.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 6;",
            bg, textColor));
        return badge;
    }

    private String categoryBg(String cat) {
        return switch (cat) {
            case "Criminal" -> tm.badgeCriminalBg();
            case "Traffic" -> tm.badgeTrafficBg();
            case "Civil" -> tm.badgeCivilBg();
            case "Succession" -> tm.badgeSuccessionBg();
            case "Children" -> tm.badgeChildrenBg();
            default -> tm.badgeOtherBg();
        };
    }

    private String categoryColor(String cat) {
        return switch (cat) {
            case "Criminal" -> tm.badgeCriminalText();
            case "Traffic" -> tm.badgeTrafficText();
            case "Civil" -> tm.badgeCivilText();
            case "Succession" -> tm.badgeSuccessionText();
            case "Children" -> tm.badgeChildrenText();
            default -> tm.badgeOtherText();
        };
    }

    private String or(String s) {
        return (s != null && !s.isBlank()) ? s : "\u2014";
    }

    private void handleEdit(CourtCase c) {
        CaseFormDialog dialog = new CaseFormDialog(c);
        Optional<CourtCase> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            CaseDao dao = new CaseDao();
            dao.update(updated);
            dao.upsertFirstCharge(updated.getCaseId(), updated.getChargeParticulars(),
                updated.getChargePlea(), updated.getChargeVerdict());
            caseRepo.getById(updated.getCaseId(), refreshed -> {
                if (refreshed != null) {
                    Platform.runLater(() -> {
                        root.getChildren().clear();
                        populateUI(refreshed);
                    });
                }
            });
        });
    }

    private void handleTransitionStage(CourtCase c) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Transition Stage");
        dialog.setHeaderText("Log a stage transition for case: " + c.getCaseNumber());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);
        DialogUtil.applyIcon(dialog);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 24, 16, 24));
        box.setPrefWidth(440);

        Label currentStatusLabel = new Label("Current Status: " + c.getCaseStatus());
        currentStatusLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        ComboBox<String> statusSelect = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                "Registered", "Mention", "Hearing", "Ruling", "Appeal", "Closed"
        ));
        statusSelect.setValue(c.getCaseStatus());
        statusSelect.setMaxWidth(Double.MAX_VALUE);

        Label notesTitleLabel = new Label("Select Transition Note *");
        notesTitleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        notesTitleLabel.setStyle("-fx-text-fill: " + (tm.isDark() ? "#8a8a8a" : "#5a5a5a") + ";");

        VBox notesContainer = new VBox(8);
        ToggleGroup notesGroup = new ToggleGroup();

        Runnable loadPredefNotes = () -> {
            notesContainer.getChildren().clear();
            notesGroup.getToggles().clear();
            String selStatus = statusSelect.getValue();
            if (selStatus != null) {
                List<String> notes = com.courttrack.util.WorkflowPredefs.getShuffledNotesFor(selStatus);
                for (String note : notes) {
                    RadioButton rb = new RadioButton(note);
                    rb.setWrapText(true);
                    rb.setMaxWidth(390);
                    rb.setToggleGroup(notesGroup);
                    rb.setStyle("-fx-font-size: 12px;");
                    notesContainer.getChildren().add(rb);
                }
                if (!notesContainer.getChildren().isEmpty()) {
                    ((RadioButton) notesContainer.getChildren().get(0)).setSelected(true);
                }
            }
            if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
            }
        };

        statusSelect.valueProperty().addListener((obs, oldV, newV) -> loadPredefNotes.run());
        loadPredefNotes.run(); // initial load

        box.getChildren().addAll(
                currentStatusLabel,
                new Label("New Status / Stage:"),
                statusSelect,
                notesTitleLabel,
                notesContainer
        );
        dialog.getDialogPane().setContent(box);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Save Transition");
        okBtn.getStyleClass().add("accent");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (statusSelect.getValue() == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Validation");
                alert.setHeaderText(null);
                alert.setContentText("Please select a status.");
                DialogUtil.applyIcon(alert);
                alert.showAndWait();
                ev.consume();
            }
        });

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                String newStatus = statusSelect.getValue();
                RadioButton selectedRb = (RadioButton) notesGroup.getSelectedToggle();
                String notes = selectedRb != null ? selectedRb.getText() : "Stage transitioned to " + newStatus;

                caseRepo.transitionStatus(c.getCaseId(), newStatus, notes, () -> {
                    caseRepo.getById(c.getCaseId(), refreshed -> {
                        if (refreshed != null) {
                            Platform.runLater(() -> {
                                root.getChildren().clear();
                                populateUI(refreshed);
                            });
                        }
                    });
                });
            }
        });
    }

    private void handleDelete(CourtCase c) {
        Dialog<Boolean> confirm = new Dialog<>();
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText(null);
        confirm.initModality(Modality.APPLICATION_MODAL);
        DialogUtil.applyIcon(confirm);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 24, 8, 24));
        box.setPrefWidth(400);

        FontIcon warnIcon = new FontIcon(Feather.ALERT_TRIANGLE);
        warnIcon.setIconSize(28);
        warnIcon.setIconColor(Color.web(tm.accentRed()));

        Label titleLabel = new Label("Delete case " + c.getCaseNumber() + "?");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox titleRow = new HBox(12, warnIcon, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label detailLabel = new Label("This case will be soft-deleted. The record can be restored later if needed.");
        detailLabel.setWrapText(true);
        detailLabel.getStyleClass().add("text-muted");

        box.getChildren().addAll(titleRow, detailLabel);
        confirm.getDialogPane().setContent(box);

        ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        confirm.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL);

        Button delBtn = (Button) confirm.getDialogPane().lookupButton(deleteType);
        delBtn.setStyle("-fx-background-color: " + tm.accentRed() + "; -fx-text-fill: white;");

        confirm.setResultConverter(bt -> bt == deleteType);
        Optional<Boolean> result = confirm.showAndWait();
        if (result.isPresent() && result.get()) {
            caseRepo.delete(c.getCaseId(), () -> Platform.runLater(onBack));
        }
    }

    public Parent getRoot() { return root; }
}
