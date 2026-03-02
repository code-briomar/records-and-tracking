package com.courttrack.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;

public class ReleaseNotesDialog {
    private final Dialog<Void> dialog;
    private final String version;
    private final String releaseNotes;

    public ReleaseNotesDialog(String version, String releaseNotes) {
        this.version = version;
        this.releaseNotes = releaseNotes != null ? releaseNotes : "No release notes available.";

        dialog = new Dialog<>();
        dialog.setTitle("What's New in " + version);
        dialog.setHeaderText("Welcome to " + version);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().setPrefWidth(700);
        dialog.getDialogPane().setPrefHeight(500);

        ButtonType closeButton = new ButtonType("Got it!", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(closeButton);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20));

        Label icon = new Label("\u2713");
        icon.setFont(Font.font("System", FontWeight.BOLD, 32));
        icon.setTextFill(Color.web("#0f7b6c"));

        Label title = new Label("Update Complete!");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        Label versionLabel = new Label("You're now running " + version);
        versionLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));

        Separator separator = new Separator();

        Label notesTitle = new Label("Release Notes");
        notesTitle.setFont(Font.font("System", FontWeight.BOLD, 14));

        TextArea notesArea = new TextArea(releaseNotes);
        notesArea.setEditable(false);
        notesArea.setWrapText(true);
        notesArea.setPrefHeight(200);
        notesArea.getStyleClass().add("text-area");
        notesArea.setFont(Font.font("System", 13));

        content.getChildren().addAll(
            icon, title, versionLabel,
            separator,
            notesTitle, notesArea
        );

        content.setAlignment(Pos.CENTER);
        dialog.getDialogPane().setContent(content);
    }

    public void showAndWait() {
        dialog.showAndWait();
    }
}
