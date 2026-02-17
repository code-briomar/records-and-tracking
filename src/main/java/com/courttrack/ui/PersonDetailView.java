package com.courttrack.ui;

import com.courttrack.dao.PersonDao;
import com.courttrack.model.Person;
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

import com.courttrack.sync.SyncCoordinator;
import javafx.stage.Modality;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class PersonDetailView {
    private final VBox root;
    private final Person person;
    private final PersonDao personDao = new PersonDao();
    private final ThemeManager tm = ThemeManager.getInstance();
    private final Runnable onBack;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public PersonDetailView(Person person, Runnable onBack) {
        this.person = person;
        this.onBack = onBack;
        this.root = new VBox(0);
        buildUI();
    }

    private void buildUI() {
        Person p = personDao.findById(person.getPersonId());
        if (p == null) p = person;

        VBox content = new VBox(0);

        // --- Top bar ---
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(24, 40, 0, 40));

        Button backBtn = new Button();
        FontIcon backIcon = new FontIcon(Feather.ARROW_LEFT);
        backIcon.setIconSize(16);
        backIcon.setIconColor(Color.web(tm.accentBlue()));
        backBtn.setGraphic(backIcon);
        backBtn.setText("Back to Persons");
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

        Button editBtn = new Button("Edit Person");
        editBtn.getStyleClass().add("accent");
        final Person ref = p;
        editBtn.setOnAction(e -> handleEdit(ref));

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
        deleteBtn.setOnAction(e -> handleDelete(ref));

        topBar.getChildren().addAll(backBtn, topSpacer, deleteBtn, editBtn);
        content.getChildren().add(topBar);

        // --- Header ---
        VBox header = new VBox(6);
        header.setPadding(new Insets(20, 40, 16, 40));

        Label nameLabel = new Label(p.getFullName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 28));
        header.getChildren().add(nameLabel);

        if (p.getNationalId() != null && !p.getNationalId().isBlank()) {
            Label idLabel = new Label("National ID: " + p.getNationalId());
            idLabel.setFont(Font.font("System", 14));
            idLabel.getStyleClass().add("text-muted");
            header.getChildren().add(idLabel);
        }

        // Badges
        HBox badges = new HBox(8);
        badges.setPadding(new Insets(8, 0, 0, 0));

        if (p.getGender() != null) {
            badges.getChildren().add(buildBadge(p.getGender(), tm.badgeCivilBg(), tm.badgeCivilText()));
        }
        if (p.getStatus() != null) {
            String color = switch (p.getStatus()) {
                case "Active" -> tm.accentGreen();
                case "Released" -> tm.accentBlue();
                default -> tm.accentOrange();
            };
            badges.getChildren().add(buildBadge(p.getStatus(), color + "18", color));
        }
        if (p.getRiskLevel() != null) {
            String color = switch (p.getRiskLevel()) {
                case "HIGH" -> tm.accentRed();
                case "MEDIUM" -> tm.accentOrange();
                default -> tm.accentGreen();
            };
            badges.getChildren().add(buildBadge(p.getRiskLevel() + " Risk", color + "18", color));
        }
        if (p.getType() != null) {
            badges.getChildren().add(buildBadge(p.getType(), tm.accentPurple() + "18", tm.accentPurple()));
        }

        header.getChildren().add(badges);
        content.getChildren().add(header);

        // --- Cards ---
        FlowPane cards = new FlowPane();
        cards.setHgap(20);
        cards.setVgap(20);
        cards.setPadding(new Insets(8, 40, 32, 40));

        // Card 1: Personal Information
        VBox personalCard = buildCard("Personal Information", Feather.USER, tm.accentBlue());
        GridPane pg = buildDetailGrid();
        int row = 0;
        addDetailRow(pg, row++, "First Name", or(p.getFirstName()));
        addDetailRow(pg, row++, "Last Name", or(p.getLastName()));
        addDetailRow(pg, row++, "Other Names", or(p.getOtherNames()));
        addDetailRow(pg, row++, "Alias", or(p.getAlias()));
        addDetailRow(pg, row++, "Date of Birth", p.getDob() != null ? p.getDob().format(DATE_FMT) : "\u2014");
        addDetailRow(pg, row++, "Gender", or(p.getGender()));
        addDetailRow(pg, row++, "Nationality", or(p.getNationality()));
        addDetailRow(pg, row++, "Marital Status", or(p.getMaritalStatus()));
        addDetailRow(pg, row++, "Occupation", or(p.getOccupation()));
        personalCard.getChildren().add(pg);

        // Card 2: Contact Details
        VBox contactCard = buildCard("Contact Details", Feather.PHONE, tm.accentGreen());
        GridPane cg = buildDetailGrid();
        int crow = 0;
        addDetailRow(cg, crow++, "Phone", or(p.getPhoneNumber()));
        addDetailRow(cg, crow++, "Email", or(p.getEmail()));
        addDetailRow(cg, crow++, "Address", or(p.getAddress()));
        contactCard.getChildren().add(cg);

        // Card 3: Emergency Contact
        VBox emergencyCard = buildCard("Emergency Contact", Feather.ALERT_CIRCLE, tm.accentRed());
        GridPane eg = buildDetailGrid();
        int erow = 0;
        addDetailRow(eg, erow++, "Name", or(p.getEmergencyContactName()));
        addDetailRow(eg, erow++, "Phone", or(p.getEmergencyContactPhone()));
        addDetailRow(eg, erow++, "Relationship", or(p.getEmergencyContactRelationship()));
        emergencyCard.getChildren().add(eg);

        // Card 4: Physical Description
        VBox physicalCard = buildCard("Physical Description", Feather.EYE, tm.accentPurple());
        GridPane phg = buildDetailGrid();
        int phrow = 0;
        addDetailRow(phg, phrow++, "Eye Color", or(p.getEyeColor()));
        addDetailRow(phg, phrow++, "Hair Color", or(p.getHairColor()));
        addDetailRow(phg, phrow++, "Marks", or(p.getDistinguishingMarks()));
        physicalCard.getChildren().add(phg);

        // Card 5: Arrest Information
        VBox arrestCard = buildCard("Arrest Information", Feather.SHIELD, tm.accentOrange());
        GridPane ag = buildDetailGrid();
        int arow = 0;
        addDetailRow(ag, arow++, "Arrest Date", p.getArrestDate() != null ? p.getArrestDate().format(DATE_FMT) : "\u2014");
        addDetailRow(ag, arow++, "Officer", or(p.getArrestingOfficer()));
        addDetailRow(ag, arow++, "Place", or(p.getPlaceOfArrest()));
        addDetailRow(ag, arow++, "First Offender", p.isFirstOffender() ? "Yes" : "No");
        arrestCard.getChildren().add(ag);

        // Card 6: Legal & Criminal
        VBox legalCard = buildCard("Legal & Criminal History", Feather.BOOK_OPEN, tm.accentBlue());
        GridPane lg = buildDetailGrid();
        int lrow = 0;
        addDetailRow(lg, lrow++, "Offense Type", or(p.getOffenseType()));
        addDetailRow(lg, lrow++, "Penalty", or(p.getPenalty()));
        addDetailRow(lg, lrow++, "Legal Rep.", or(p.getLegalRepresentation()));
        addDetailRow(lg, lrow++, "Facility", or(p.getFacility()));
        addDetailRow(lg, lrow++, "Criminal History", or(p.getCriminalHistory()));
        addDetailRow(lg, lrow++, "Associates", or(p.getKnownAssociates()));
        legalCard.getChildren().add(lg);

        // Card 7: Medical & Notes
        VBox medCard = buildCard("Medical & Notes", Feather.HEART, tm.accentRed());
        GridPane mg = buildDetailGrid();
        int mrow = 0;
        addDetailRow(mg, mrow++, "Medical", or(p.getMedicalConditions()));
        addDetailRow(mg, mrow++, "Notes", or(p.getNotes()));
        medCard.getChildren().add(mg);

        cards.getChildren().addAll(personalCard, contactCard, emergencyCard, physicalCard,
            arrestCard, legalCard, medCard);

        for (var node : cards.getChildren()) {
            if (node instanceof VBox card) {
                card.setPrefWidth(420);
                card.setMinWidth(300);
                card.setMaxWidth(500);
            }
        }

        content.getChildren().add(cards);

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

    private String or(String s) {
        return (s != null && !s.isBlank()) ? s : "\u2014";
    }

    private void handleEdit(Person p) {
        OffenderFormDialog dialog = new OffenderFormDialog(p);
        Optional<OffenderFormDialog.PersonCaseLink> result = dialog.showAndWait();
        result.ifPresent(link -> {
            personDao.update(link.getPerson());
            root.getChildren().clear();
            buildUI();
        });
    }

    private void handleDelete(Person p) {
        Dialog<Boolean> confirm = new Dialog<>();
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText(null);
        confirm.initModality(Modality.APPLICATION_MODAL);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16, 24, 8, 24));
        box.setPrefWidth(400);

        FontIcon warnIcon = new FontIcon(Feather.ALERT_TRIANGLE);
        warnIcon.setIconSize(28);
        warnIcon.setIconColor(Color.web(tm.accentRed()));

        Label titleLabel = new Label("Delete " + p.getFullName() + "?");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        HBox titleRow = new HBox(12, warnIcon, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label detailLabel = new Label("This person will be soft-deleted. The record can be restored later if needed.");
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
            personDao.softDelete(p.getPersonId());
            SyncCoordinator.getInstance().queuePersonSync(p.getPersonId(), "DELETE");
            onBack.run();
        }
    }

    public Parent getRoot() { return root; }
}
