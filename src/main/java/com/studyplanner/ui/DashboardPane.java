package com.studyplanner.ui;

import com.studyplanner.dto.DashboardSummary;
import com.studyplanner.dto.SubjectStudyBreakdown;
import com.studyplanner.dto.TopicAnalyticsSnapshot;
import com.studyplanner.dto.WeeklyStudyPoint;
import com.studyplanner.model.PlanItem;
import com.studyplanner.service.ApplicationContext;
import com.studyplanner.utils.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class DashboardPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable openPlanner;
    private final Runnable openSession;
    private final Runnable openLibrary;

    private final VBox container = new VBox(24);

    private final Label plannedHoursLabel = new Label();
    private final Label loggedHoursLabel = new Label();
    private final Label overdueReviewsLabel = new Label();
    private final Label streakLabel = new Label();

    private final Label nextActionTitleLabel = new Label();
    private final Label nextActionCopyLabel = new Label();
    private final Label progressCopyLabel = new Label();
    private final ProgressBar dailyProgressBar = new ProgressBar(0);

    private final VBox agendaList = new VBox(10);
    private final VBox riskList = new VBox(10);
    private final VBox subjectHealthList = new VBox(10);
    private final LineChart<String, Number> weeklyChart = new LineChart<>(new CategoryAxis(), new NumberAxis());

    public DashboardPane(ApplicationContext context, Runnable openPlanner, Runnable openSession, Runnable openLibrary) {
        this.context = context;
        this.openPlanner = openPlanner;
        this.openSession = openSession;
        this.openLibrary = openLibrary;

        getStyleClass().add("screen-scroller");
        setFitToWidth(true);
        setPadding(new Insets(0));

        configureChart();
        container.getStyleClass().add("screen-root");
        container.setPadding(new Insets(8, 8, 24, 8));
        setContent(container);
    }

    @Override
    public void refresh() {
        container.getChildren().clear();
        container.getChildren().add(buildHeader());

        if (context.isWorkspaceEmpty()) {
            Button libraryButton = new Button("Open Library");
            libraryButton.getStyleClass().add("primary-button");
            libraryButton.setOnAction(event -> openLibrary.run());

            container.getChildren().add(new EmptyStatePane(
                "No study system yet",
                "Start by adding a subject and a few topics. Once the syllabus exists, the planner can build the first adaptive day.",
                libraryButton
            ));
            return;
        }

        DashboardSummary summary = context.getPerformanceAnalyticsService().getDashboardSummary(LocalDate.now());
        List<PlanItem> planItems = context.getSchedulerService().getPlan(LocalDate.now())
            .map(plan -> plan.getItems())
            .orElse(List.of());
        List<TopicAnalyticsSnapshot> riskItems = context.getPerformanceAnalyticsService().getTopicAnalytics().stream()
            .limit(4)
            .toList();
        List<SubjectStudyBreakdown> subjectBreakdowns = context.getPerformanceAnalyticsService().getSubjectBreakdown();

        plannedHoursLabel.setText(FormatUtils.hoursFromMinutes(summary.todayPlannedMinutes()));
        loggedHoursLabel.setText(FormatUtils.hoursFromMinutes(summary.todayLoggedMinutes()));
        overdueReviewsLabel.setText(String.valueOf(summary.overdueReviews()));
        streakLabel.setText(summary.studyStreak() + " days");

        updateActionCard(summary, planItems);
        rebuildAgenda(planItems);
        rebuildRisk(riskItems);
        rebuildSubjectHealth(subjectBreakdowns);
        rebuildWeeklyChart(context.getPerformanceAnalyticsService().getWeeklyStudyConsistency(7));

        container.getChildren().addAll(
            buildMetricRow(),
            buildActionRow(),
            buildInsightRow()
        );
    }

    private VBox buildHeader() {
        Label title = new Label("Today");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("See the next best block, understand why it matters, and keep momentum visible.");
        subtitle.getStyleClass().add("screen-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private HBox buildMetricRow() {
        HBox row = new HBox(
            14,
            UiFactory.createSummaryCard("Planned Today", plannedHoursLabel, "Time reserved in today's plan."),
            UiFactory.createSummaryCard("Logged Today", loggedHoursLabel, "Work already completed or partially done."),
            UiFactory.createSummaryCard("Overdue Reviews", overdueReviewsLabel, "Topics whose review date has already passed."),
            UiFactory.createSummaryCard("Current Streak", streakLabel, "Consecutive active study days.")
        );
        row.getStyleClass().add("metric-row");
        return row;
    }

    private HBox buildActionRow() {
        Button plannerButton = new Button("Review Plan");
        plannerButton.getStyleClass().add("primary-button");
        plannerButton.setOnAction(event -> openPlanner.run());

        Button sessionButton = new Button("Log Work");
        sessionButton.getStyleClass().add("secondary-button");
        sessionButton.setOnAction(event -> openSession.run());

        nextActionCopyLabel.setWrapText(true);
        nextActionCopyLabel.getStyleClass().add("support-label");
        progressCopyLabel.getStyleClass().add("support-label");

        dailyProgressBar.setMaxWidth(Double.MAX_VALUE);
        dailyProgressBar.getStyleClass().add("progress-track");

        VBox nextActionCard = UiFactory.createCard(
            "Next Best Action",
            nextActionTitleLabel,
            nextActionCopyLabel,
            dailyProgressBar,
            progressCopyLabel,
            new HBox(10, plannerButton, sessionButton)
        );
        nextActionCard.getStyleClass().add("action-card");
        HBox.setHgrow(nextActionCard, Priority.ALWAYS);

        VBox reviewCard = UiFactory.createCard("Review Pressure", riskList);
        reviewCard.getStyleClass().add("content-card");
        HBox.setHgrow(reviewCard, Priority.ALWAYS);

        return new HBox(14, nextActionCard, reviewCard);
    }

    private HBox buildInsightRow() {
        VBox agendaCard = UiFactory.createCard("Today's Agenda", agendaList);
        agendaCard.getStyleClass().add("content-card");
        HBox.setHgrow(agendaCard, Priority.ALWAYS);

        VBox consistencyCard = UiFactory.createCard("Weekly Momentum", weeklyChart);
        consistencyCard.getStyleClass().add("content-card");

        VBox subjectCard = UiFactory.createCard("Subject Health", subjectHealthList);
        subjectCard.getStyleClass().add("content-card");

        VBox rightColumn = new VBox(14, consistencyCard, subjectCard);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        return new HBox(14, agendaCard, rightColumn);
    }

    private void updateActionCard(DashboardSummary summary, List<PlanItem> planItems) {
        double progress = summary.todayPlannedMinutes() <= 0
            ? 0.0
            : Math.min(1.0, summary.todayLoggedMinutes() / (double) summary.todayPlannedMinutes());
        dailyProgressBar.setProgress(progress);
        progressCopyLabel.setText(
            summary.todayPlannedMinutes() <= 0
                ? "Create a plan to start tracking today's execution."
                : summary.todayLoggedMinutes() + " of " + summary.todayPlannedMinutes() + " minutes completed today."
        );

        if (planItems.isEmpty()) {
            nextActionTitleLabel.setText("Generate today's plan");
            nextActionCopyLabel.setText("You have topics in the library, but nothing has been scheduled for today yet.");
            return;
        }

        PlanItem next = planItems.get(0);
        nextActionTitleLabel.setText(next.getTopicName() + " (" + next.getItemType().name() + ")");
        nextActionCopyLabel.setText(next.getReason() + " Expected recall: "
            + FormatUtils.percentage(next.getRecallProbability()) + ".");
    }

    private void rebuildAgenda(List<PlanItem> planItems) {
        agendaList.getChildren().clear();
        if (planItems.isEmpty()) {
            agendaList.getChildren().add(new EmptyStatePane(
                "Nothing queued yet",
                "Generate a daily plan to turn your syllabus into ordered study and review blocks."
            ));
            return;
        }

        planItems.stream()
            .limit(5)
            .forEach(item -> agendaList.getChildren().add(createAgendaRow(item)));
    }

    private void rebuildRisk(List<TopicAnalyticsSnapshot> riskItems) {
        riskList.getChildren().clear();
        if (riskItems.isEmpty()) {
            riskList.getChildren().add(new Label("No active topics yet."));
            return;
        }
        riskItems.forEach(item -> riskList.getChildren().add(createRiskRow(item)));
    }

    private void rebuildSubjectHealth(List<SubjectStudyBreakdown> subjectBreakdowns) {
        subjectHealthList.getChildren().clear();
        if (subjectBreakdowns.isEmpty()) {
            subjectHealthList.getChildren().add(new Label("No subject activity yet."));
            return;
        }
        subjectBreakdowns.stream()
            .limit(4)
            .forEach(item -> subjectHealthList.getChildren().add(createSubjectRow(item)));
    }

    private HBox createAgendaRow(PlanItem item) {
        Label orderLabel = new Label("#" + item.getRecommendedOrder());
        orderLabel.getStyleClass().add("eyebrow-label");

        Label titleLabel = new Label(item.getSubjectName() + " / " + item.getTopicName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(item.getPlannedMinutes() + " min • "
            + item.getPomodoroCount() + " blocks • "
            + FormatUtils.percentage(item.getRecallProbability()) + " recall");
        metaLabel.getStyleClass().add("row-copy");

        Label typeChip = UiFactory.createChip(item.getItemType().name(), item.getItemType().name().equals("REVIEW")
            ? "chip-warning"
            : "chip-neutral");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, orderLabel, copy, spacer, typeChip);
        row.getStyleClass().add("list-row-card");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox createRiskRow(TopicAnalyticsSnapshot item) {
        Label titleLabel = new Label(item.topicName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(item.subjectName() + " • " + item.neglectSummary());
        metaLabel.getStyleClass().add("row-copy");

        Label recallChip = UiFactory.createChip(FormatUtils.percentage(item.recallProbability()) + " recall", "chip-danger");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, copy, spacer, recallChip);
        row.getStyleClass().add("list-row-card");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox createSubjectRow(SubjectStudyBreakdown item) {
        Label titleLabel = new Label(item.subjectName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(item.totalMinutes() + " min • "
            + item.totalPomodoros() + " pomodoros • "
            + FormatUtils.percentage(item.completionRate()) + " completion");
        metaLabel.getStyleClass().add("row-copy");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        HBox row = new HBox(copy);
        row.getStyleClass().add("list-row-card");
        return row;
    }

    private void configureChart() {
        weeklyChart.setLegendVisible(false);
        weeklyChart.setAnimated(false);
        weeklyChart.setCreateSymbols(true);
        weeklyChart.setPrefHeight(230);
    }

    private void rebuildWeeklyChart(List<WeeklyStudyPoint> points) {
        weeklyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (WeeklyStudyPoint point : points) {
            series.getData().add(new XYChart.Data<>(point.date().getDayOfWeek().name().substring(0, 3), point.minutesStudied()));
        }
        weeklyChart.getData().add(series);
    }
}
