package com.courttrack.ui;

import com.courttrack.dao.CaseDao;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.Charge;
import com.courttrack.model.CourtCase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class CaseDetailView {
    private final VBox root;
    private final CourtCase courtCase;
    private final CaseDao caseDao = new CaseDao();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Runnable onBack;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public CaseDetailView(CourtCase courtCase, Runnable onBack) {
        this.courtCase = courtCase;
        this.onBack = onBack;
        this.root = new VBox(0);
        buildUI();
    }

    private void buildUI() {
        // Reload full case data
        CourtCase c = caseDao.findById(courtCase.getCaseId());
        if (c == null) c = courtCase;

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

        topBar.getChildren().addAll(backBtn, topSpacer, editBtn);
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
            boolean isOpen = "OPEN".equals(c.getCaseStatus());
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
        List<Charge> charges = caseDao.getCharges(c.getCaseId());
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

        // Card 7: Participants
        List<CaseParticipant> participants = caseDao.getParticipants(c.getCaseId());
        if (!participants.isEmpty()) {
            VBox partCard = buildCard("Participants", Feather.USERS, tm.accentBlue());
            GridPane partGrid = buildDetailGrid();
            int prow = 0;
            for (CaseParticipant cp : participants) {
                addDetailRow(partGrid, prow++, cp.getRoleType(), cp.getPersonId());
            }
            partCard.getChildren().add(partGrid);
            cards.getChildren().add(partCard);
        }

        cards.getChildren().addAll(0, List.of(caseInfoCard, chargeCard, judgmentCard, detailCard, evidenceCard));

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
            default -> "#eee";
        };
    }

    private String categoryColor(String cat) {
        return switch (cat) {
            case "Criminal" -> tm.badgeCriminalText();
            case "Traffic" -> tm.badgeTrafficText();
            case "Civil" -> tm.badgeCivilText();
            default -> "#888";
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
            // Rebuild the view with fresh data
            root.getChildren().clear();
            buildUI();
        });
    }

    public Parent getRoot() { return root; }
}
