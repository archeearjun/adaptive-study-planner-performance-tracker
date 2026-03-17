package com.studyplanner.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class OnboardingPane extends StackPane {
    public OnboardingPane(Runnable useDemoWorkspace, Runnable startBlankWorkspace) {
        getStyleClass().add("onboarding-root");

        Label title = new Label("Build a study workspace that tells you what matters next.");
        title.getStyleClass().add("onboarding-title");
        title.setWrapText(true);

        Label subtitle = new Label(
            "Adaptive Study Planner combines planning, review recovery, and execution tracking in one desktop flow."
        );
        subtitle.getStyleClass().add("onboarding-copy");
        subtitle.setWrapText(true);

        VBox narrative = new VBox(14, title, subtitle, buildHighlights());
        narrative.setMaxWidth(560);

        Button demoButton = new Button("Use Demo Workspace");
        demoButton.getStyleClass().add("primary-button");
        demoButton.setOnAction(event -> useDemoWorkspace.run());

        Button blankButton = new Button("Start Blank");
        blankButton.getStyleClass().add("secondary-button");
        blankButton.setOnAction(event -> startBlankWorkspace.run());

        Label note = new Label("Demo data gives you a ready-made planner. Starting blank keeps the database empty.");
        note.getStyleClass().add("support-label");
        note.setWrapText(true);

        HBox actions = new HBox(12, demoButton, blankButton);

        VBox panel = new VBox(20, narrative, actions, note);
        panel.getStyleClass().add("onboarding-panel");
        panel.setPadding(new Insets(36));
        panel.setMaxWidth(760);

        getChildren().add(panel);
        StackPane.setAlignment(panel, Pos.CENTER);
        setPadding(new Insets(32));
    }

    private HBox buildHighlights() {
        HBox cards = new HBox(
            14,
            createHighlight("Today-first", "Open the app and see the next best study block, not a pile of tables."),
            createHighlight("Adaptive", "The plan reacts to recall risk, deadlines, and work you already logged."),
            createHighlight("Motivating", "Progress, streaks, and overdue reviews stay visible without taking over the screen.")
        );
        cards.setAlignment(Pos.TOP_LEFT);
        return cards;
    }

    private VBox createHighlight(String title, String copy) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("highlight-title");

        Label copyLabel = new Label(copy);
        copyLabel.getStyleClass().add("highlight-copy");
        copyLabel.setWrapText(true);

        Region spacer = UiFactory.spacer();
        VBox card = new VBox(10, titleLabel, copyLabel, spacer);
        card.getStyleClass().add("highlight-card");
        card.setPrefWidth(0);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }
}
