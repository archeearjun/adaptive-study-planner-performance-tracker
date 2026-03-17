package com.studyplanner.ui;

import com.studyplanner.dto.DashboardSummary;
import com.studyplanner.service.ApplicationContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class MainView extends BorderPane {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, d MMMM");

    private final ApplicationContext context;
    private final List<RefreshableView> refreshableViews = new ArrayList<>();
    private final Map<AppSection, Node> sectionViews = new EnumMap<>(AppSection.class);
    private final Map<AppSection, Button> navButtons = new EnumMap<>(AppSection.class);

    private final Label shellDateLabel = new Label();
    private final Label shellStatusLabel = new Label();

    private VBox sidebar;
    private HBox topBar;
    private LibraryPane libraryPane;
    private AppSection currentSection = AppSection.TODAY;
    private boolean workspaceInitialized;

    public MainView(ApplicationContext context) {
        this.context = context;
        getStyleClass().add("app-shell");
        setPadding(new Insets(18));

        if (context.isWorkspaceEmpty()) {
            showOnboarding();
        } else {
            showWorkspace(AppSection.TODAY);
        }
    }

    public void refreshAll() {
        refreshableViews.forEach(RefreshableView::refresh);
        updateShellStatus();
    }

    public void selectTab(String title) {
        switch (title) {
            case "Dashboard" -> showWorkspace(AppSection.TODAY);
            case "Daily Planner" -> showWorkspace(AppSection.PLANNER);
            case "Session Logger" -> showWorkspace(AppSection.SESSION);
            case "Analytics" -> showWorkspace(AppSection.ANALYTICS);
            case "Subjects/Topics" -> {
                showWorkspace(AppSection.LIBRARY);
                libraryPane.showSection("Subjects & Topics");
            }
            case "Topic Details" -> {
                showWorkspace(AppSection.LIBRARY);
                libraryPane.showSection("Topic Details");
            }
            default -> showWorkspace(AppSection.TODAY);
        }
    }

    private void showOnboarding() {
        setLeft(null);
        setTop(null);
        setCenter(new OnboardingPane(
            () -> {
                context.seedDemoWorkspace();
                showWorkspace(AppSection.TODAY);
            },
            () -> showWorkspace(AppSection.LIBRARY)
        ));
    }

    private void showWorkspace(AppSection section) {
        initializeWorkspace();
        setLeft(sidebar);
        setTop(topBar);
        selectSection(section);
        refreshAll();
    }

    private void initializeWorkspace() {
        if (workspaceInitialized) {
            return;
        }

        sidebar = buildSidebar();
        topBar = buildTopBar();

        DashboardPane dashboardPane = register(new DashboardPane(
            context,
            () -> selectSection(AppSection.PLANNER),
            () -> selectSection(AppSection.SESSION),
            () -> {
                selectSection(AppSection.LIBRARY);
                libraryPane.showSection("Subjects & Topics");
            }
        ));
        PlannerPane plannerPane = register(new PlannerPane(
            context,
            this::refreshAll,
            () -> selectSection(AppSection.SESSION)
        ));
        SessionLoggerPane sessionLoggerPane = register(new SessionLoggerPane(
            context,
            this::refreshAll,
            () -> selectSection(AppSection.PLANNER)
        ));
        AnalyticsPane analyticsPane = register(new AnalyticsPane(context));
        libraryPane = register(new LibraryPane(context, this::refreshAll));

        sectionViews.put(AppSection.TODAY, dashboardPane);
        sectionViews.put(AppSection.PLANNER, plannerPane);
        sectionViews.put(AppSection.SESSION, sessionLoggerPane);
        sectionViews.put(AppSection.ANALYTICS, analyticsPane);
        sectionViews.put(AppSection.LIBRARY, libraryPane);

        workspaceInitialized = true;
    }

    private VBox buildSidebar() {
        Label title = new Label("Adaptive Study Planner");
        title.getStyleClass().add("sidebar-title");

        Label subtitle = new Label("A study workspace built around daily decisions, review recovery, and progress.");
        subtitle.getStyleClass().add("sidebar-copy");
        subtitle.setWrapText(true);

        VBox navGroup = new VBox(8,
            createNavButton(AppSection.TODAY),
            createNavButton(AppSection.PLANNER),
            createNavButton(AppSection.SESSION),
            createNavButton(AppSection.ANALYTICS),
            createNavButton(AppSection.LIBRARY)
        );
        navGroup.getStyleClass().add("sidebar-nav");

        Label databasePath = new Label("SQLite: " + context.getDatabaseManager().getDatabaseLocationLabel());
        databasePath.getStyleClass().add("sidebar-meta");
        databasePath.setWrapText(true);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(20, title, subtitle, navGroup, spacer, databasePath);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        sidebar.setPadding(new Insets(22));
        return sidebar;
    }

    private HBox buildTopBar() {
        shellDateLabel.getStyleClass().add("topbar-title");
        shellStatusLabel.getStyleClass().add("topbar-copy");

        VBox copy = new VBox(4, shellDateLabel, shellStatusLabel);

        Button plannerButton = new Button("Open Planner");
        plannerButton.getStyleClass().add("ghost-button");
        plannerButton.setOnAction(event -> selectSection(AppSection.PLANNER));

        Button sessionButton = new Button("Log Session");
        sessionButton.getStyleClass().add("primary-button");
        sessionButton.setOnAction(event -> selectSection(AppSection.SESSION));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, copy, spacer, plannerButton, sessionButton);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Button createNavButton(AppSection section) {
        Button button = new Button(section.label());
        button.getStyleClass().add("sidebar-nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> selectSection(section));
        navButtons.put(section, button);
        return button;
    }

    private void selectSection(AppSection section) {
        currentSection = section;
        setCenter(sectionViews.get(section));
        navButtons.forEach((appSection, button) -> {
            if (appSection == section) {
                if (!button.getStyleClass().contains("sidebar-nav-button-active")) {
                    button.getStyleClass().add("sidebar-nav-button-active");
                }
            } else {
                button.getStyleClass().remove("sidebar-nav-button-active");
            }
        });
    }

    private void updateShellStatus() {
        if (!workspaceInitialized) {
            return;
        }

        shellDateLabel.setText(DATE_FORMATTER.format(LocalDate.now()));
        if (context.isWorkspaceEmpty()) {
            shellStatusLabel.setText("Create your first subject and topic to generate a plan.");
            return;
        }

        DashboardSummary summary = context.getPerformanceAnalyticsService().getDashboardSummary(LocalDate.now());
        shellStatusLabel.setText(
            summary.tasksDueToday() + " tasks due today, "
                + summary.overdueReviews() + " overdue reviews, "
                + summary.studyStreak() + " day streak"
        );
    }

    private <T extends RefreshableView> T register(T view) {
        refreshableViews.add(view);
        return view;
    }

    private enum AppSection {
        TODAY("Today"),
        PLANNER("Planner"),
        SESSION("Session"),
        ANALYTICS("Analytics"),
        LIBRARY("Library");

        private final String label;

        AppSection(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
