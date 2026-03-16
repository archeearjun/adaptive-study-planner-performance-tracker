package com.studyplanner.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class UiFactory {
    private UiFactory() {
    }

    public static VBox createCard(String title, Node... body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        card.getChildren().add(titleLabel);
        card.getChildren().addAll(body);
        return card;
    }

    public static VBox createSummaryCard(String title, Label valueLabel, String supportingText) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("summary-title");

        valueLabel.getStyleClass().add("summary-value");

        Label supportLabel = new Label(supportingText);
        supportLabel.getStyleClass().add("summary-support");
        supportLabel.setWrapText(true);

        VBox card = new VBox(8, titleLabel, valueLabel, supportLabel);
        card.getStyleClass().add("summary-card");
        card.setPadding(new Insets(18));
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    public static Region spacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
