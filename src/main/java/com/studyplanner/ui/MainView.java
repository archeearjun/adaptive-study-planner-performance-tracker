package com.studyplanner.ui;

import com.studyplanner.service.ApplicationContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class MainView extends BorderPane {
    private final List<RefreshableView> refreshableViews = new ArrayList<>();
    private final TabPane tabPane;

    public MainView(ApplicationContext context) {
        getStyleClass().add("app-shell");
        setPadding(new Insets(18));

        DashboardPane dashboardPane = register(new DashboardPane(context));
        ManagementPane managementPane = register(new ManagementPane(context, this::refreshAll));
        PlannerPane plannerPane = register(new PlannerPane(context, this::refreshAll));
        SessionLoggerPane sessionLoggerPane = register(new SessionLoggerPane(context, this::refreshAll));
        AnalyticsPane analyticsPane = register(new AnalyticsPane(context));
        TopicDetailsPane topicDetailsPane = register(new TopicDetailsPane(context));
        this.tabPane = buildTabs(
            dashboardPane,
            managementPane,
            plannerPane,
            sessionLoggerPane,
            analyticsPane,
            topicDetailsPane
        );

        setTop(buildHeader(context));
        setCenter(tabPane);

        refreshAll();
    }

    private VBox buildHeader(ApplicationContext context) {
        Label title = new Label("Adaptive Study Planner");
        title.getStyleClass().add("hero-title");

        Label subtitle = new Label(
            "SM-2 reviews, priority-aware scheduling, Pomodoro execution, and a logistic regression retention predictor."
        );
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setWrapText(true);

        Label databasePath = new Label("SQLite storage: " + context.getDatabaseManager().getDatabaseLocationLabel());
        databasePath.getStyleClass().add("hero-meta");

        VBox copy = new VBox(8, title, subtitle, databasePath);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label badge = new Label("Portfolio Build");
        badge.getStyleClass().add("hero-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(16, copy, spacer, badge);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(row);
        hero.getStyleClass().add("hero-panel");
        hero.setPadding(new Insets(24));
        hero.setSpacing(14);
        return hero;
    }

    private TabPane buildTabs(DashboardPane dashboardPane, ManagementPane managementPane,
                              PlannerPane plannerPane, SessionLoggerPane sessionLoggerPane,
                              AnalyticsPane analyticsPane, TopicDetailsPane topicDetailsPane) {
        TabPane tabPane = new TabPane();
        tabPane.getStyleClass().add("main-tabs");
        tabPane.getTabs().addAll(
            createTab("Dashboard", dashboardPane),
            createTab("Subjects/Topics", managementPane),
            createTab("Daily Planner", plannerPane),
            createTab("Session Logger", sessionLoggerPane),
            createTab("Analytics", analyticsPane),
            createTab("Topic Details", topicDetailsPane)
        );
        return tabPane;
    }

    private Tab createTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private <T extends RefreshableView> T register(T view) {
        refreshableViews.add(view);
        return view;
    }

    public void refreshAll() {
        refreshableViews.forEach(RefreshableView::refresh);
    }

    public void selectTab(String title) {
        tabPane.getTabs().stream()
            .filter(tab -> tab.getText().equals(title))
            .findFirst()
            .ifPresent(tab -> tabPane.getSelectionModel().select(tab));
    }
}
