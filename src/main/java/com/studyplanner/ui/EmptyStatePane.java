package com.studyplanner.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class EmptyStatePane extends VBox {
    public EmptyStatePane(String title, String message, Node... actions) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("empty-copy");
        messageLabel.setWrapText(true);

        getChildren().addAll(titleLabel, messageLabel);
        if (actions.length > 0) {
            HBox actionRow = new HBox(10, actions);
            actionRow.getStyleClass().add("empty-actions");
            getChildren().add(actionRow);
        }
    }
}
