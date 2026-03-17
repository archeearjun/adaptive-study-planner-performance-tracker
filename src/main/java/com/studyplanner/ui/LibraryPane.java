package com.studyplanner.ui;

import com.studyplanner.service.ApplicationContext;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class LibraryPane extends BorderPane implements RefreshableView {
    private final ManagementPane managementPane;
    private final TopicDetailsPane topicDetailsPane;
    private final TabPane tabPane = new TabPane();

    public LibraryPane(ApplicationContext context, Runnable afterMutation) {
        getStyleClass().add("screen-root");
        setPadding(new Insets(0));

        this.managementPane = new ManagementPane(context, afterMutation);
        this.topicDetailsPane = new TopicDetailsPane(context);

        tabPane.getStyleClass().add("library-tabs");
        tabPane.getTabs().addAll(
            createTab("Subjects & Topics", managementPane),
            createTab("Topic Details", topicDetailsPane)
        );

        Label title = new Label("Library");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Manage the syllabus structure and inspect topic-level learning history.");
        subtitle.getStyleClass().add("screen-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.getStyleClass().add("screen-header");

        setTop(header);
        setCenter(tabPane);
    }

    @Override
    public void refresh() {
        managementPane.refresh();
        topicDetailsPane.refresh();
    }

    public void showSection(String title) {
        tabPane.getTabs().stream()
            .filter(tab -> tab.getText().equals(title))
            .findFirst()
            .ifPresent(tab -> tabPane.getSelectionModel().select(tab));
    }

    private Tab createTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }
}
