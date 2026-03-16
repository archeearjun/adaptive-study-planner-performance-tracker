package com.studyplanner.ui;

import com.studyplanner.dto.DashboardSummary;
import com.studyplanner.dto.SubjectStudyBreakdown;
import com.studyplanner.dto.TopicAnalyticsSnapshot;
import com.studyplanner.dto.WeeklyStudyPoint;
import com.studyplanner.model.PlanItem;
import com.studyplanner.service.ApplicationContext;
import com.studyplanner.utils.FormatUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class DashboardPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;

    private final Label plannedHoursLabel = new Label();
    private final Label loggedHoursLabel = new Label();
    private final Label overdueReviewsLabel = new Label();
    private final Label streakLabel = new Label();
    private final Label recallLabel = new Label();
    private final Label tasksDueLabel = new Label();
    private final Label modelInfoLabel = new Label();

    private final TableView<PlanItem> todayPlanTable = new TableView<>();
    private final TableView<TopicAnalyticsSnapshot> riskTable = new TableView<>();
    private final LineChart<String, Number> weeklyChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final BarChart<String, Number> subjectChart = new BarChart<>(new CategoryAxis(), new NumberAxis());

    public DashboardPane(ApplicationContext context) {
        this.context = context;
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
    }

    @Override
    public void refresh() {
        DashboardSummary summary = context.getPerformanceAnalyticsService().getDashboardSummary(LocalDate.now());
        plannedHoursLabel.setText(FormatUtils.hoursFromMinutes(summary.todayPlannedMinutes()));
        loggedHoursLabel.setText(FormatUtils.hoursFromMinutes(summary.todayLoggedMinutes()));
        overdueReviewsLabel.setText(String.valueOf(summary.overdueReviews()));
        streakLabel.setText(summary.studyStreak() + " days");
        recallLabel.setText(FormatUtils.percentage(summary.averageRecallProbability()));
        tasksDueLabel.setText(String.valueOf(summary.tasksDueToday()));
        modelInfoLabel.setText(
            context.getRetentionPredictionService().isModelTrained()
                ? "Retention model retrained from local study history. Accuracy: "
                    + FormatUtils.percentage(context.getRetentionPredictionService().getTrainingAccuracy())
                : "Retention model is using deterministic seed coefficients until more history is collected."
        );

        List<PlanItem> planItems = context.getSchedulerService().getPlan(LocalDate.now())
            .map(plan -> plan.getItems())
            .orElse(List.of());
        todayPlanTable.setItems(FXCollections.observableArrayList(planItems));

        List<TopicAnalyticsSnapshot> riskItems = context.getPerformanceAnalyticsService().getTopicAnalytics().stream()
            .limit(6)
            .toList();
        riskTable.setItems(FXCollections.observableArrayList(riskItems));

        rebuildWeeklyChart(context.getPerformanceAnalyticsService().getWeeklyStudyConsistency(7));
        rebuildSubjectChart(context.getPerformanceAnalyticsService().getSubjectBreakdown());
    }

    private VBox buildContent() {
        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(14);
        summaryGrid.setVgap(14);
        summaryGrid.add(UiFactory.createSummaryCard("Today's Planned Hours", plannedHoursLabel, "Pulled from the stored plan for today."), 0, 0);
        summaryGrid.add(UiFactory.createSummaryCard("Today's Logged Time", loggedHoursLabel, "Actual completed or partial work logged today."), 1, 0);
        summaryGrid.add(UiFactory.createSummaryCard("Overdue Reviews", overdueReviewsLabel, "Topics whose SM-2 review date already passed."), 2, 0);
        summaryGrid.add(UiFactory.createSummaryCard("Study Streak", streakLabel, "Consecutive days with completed or partial sessions."), 3, 0);
        summaryGrid.add(UiFactory.createSummaryCard("Average Recall", recallLabel, "Mean recall probability across active topics."), 4, 0);
        summaryGrid.add(UiFactory.createSummaryCard("Tasks Due Today", tasksDueLabel, "Either today's plan items or all due reviews."), 5, 0);

        configurePlanTable();
        configureRiskTable();
        configureCharts();

        Label modelHeading = new Label("Model Status");
        modelHeading.getStyleClass().add("section-title");
        modelInfoLabel.getStyleClass().add("support-label");
        modelInfoLabel.setWrapText(true);

        VBox topLeft = UiFactory.createCard("Today's Queue", todayPlanTable);
        VBox topRight = UiFactory.createCard("At-Risk Topics", riskTable);
        HBox.setHgrow(topLeft, Priority.ALWAYS);
        HBox.setHgrow(topRight, Priority.ALWAYS);

        HBox tablesRow = new HBox(14, topLeft, topRight);
        VBox.setVgrow(tablesRow, Priority.ALWAYS);

        VBox chartCard = UiFactory.createCard("Consistency Trend", weeklyChart);
        VBox subjectCard = UiFactory.createCard("Subject Breakdown", subjectChart);
        HBox.setHgrow(chartCard, Priority.ALWAYS);
        HBox.setHgrow(subjectCard, Priority.ALWAYS);
        HBox chartsRow = new HBox(14, chartCard, subjectCard);

        VBox container = new VBox(18, summaryGrid, modelHeading, modelInfoLabel, tablesRow, chartsRow);
        container.setPadding(new Insets(8));
        return container;
    }

    private void configurePlanTable() {
        todayPlanTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PlanItem, Number> orderColumn = new TableColumn<>("Order");
        orderColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getRecommendedOrder()));

        TableColumn<PlanItem, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTopicName()));

        TableColumn<PlanItem, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getItemType().name()));

        TableColumn<PlanItem, Number> minutesColumn = new TableColumn<>("Minutes");
        minutesColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPlannedMinutes()));

        TableColumn<PlanItem, String> recallColumn = new TableColumn<>("Recall");
        recallColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.percentage(data.getValue().getRecallProbability())));

        TableColumn<PlanItem, String> reasonColumn = new TableColumn<>("Reason");
        reasonColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getReason()));

        todayPlanTable.getColumns().addAll(orderColumn, topicColumn, typeColumn, minutesColumn, recallColumn, reasonColumn);
    }

    private void configureRiskTable() {
        riskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TopicAnalyticsSnapshot, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().topicName()));

        TableColumn<TopicAnalyticsSnapshot, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().subjectName()));

        TableColumn<TopicAnalyticsSnapshot, String> recallColumn = new TableColumn<>("Recall");
        recallColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.percentage(data.getValue().recallProbability())));

        TableColumn<TopicAnalyticsSnapshot, String> neglectColumn = new TableColumn<>("Neglect");
        neglectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().neglectSummary()));

        riskTable.getColumns().addAll(topicColumn, subjectColumn, recallColumn, neglectColumn);
    }

    private void configureCharts() {
        weeklyChart.setLegendVisible(false);
        weeklyChart.setCreateSymbols(true);
        weeklyChart.setAnimated(false);
        weeklyChart.setPrefHeight(280);

        subjectChart.setLegendVisible(false);
        subjectChart.setAnimated(false);
        subjectChart.setPrefHeight(280);
    }

    private void rebuildWeeklyChart(List<WeeklyStudyPoint> points) {
        weeklyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (WeeklyStudyPoint point : points) {
            series.getData().add(new XYChart.Data<>(point.date().getDayOfWeek().name().substring(0, 3), point.minutesStudied()));
        }
        weeklyChart.getData().add(series);
    }

    private void rebuildSubjectChart(List<SubjectStudyBreakdown> breakdowns) {
        subjectChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (SubjectStudyBreakdown breakdown : breakdowns) {
            series.getData().add(new XYChart.Data<>(breakdown.subjectName(), breakdown.totalMinutes()));
        }
        subjectChart.getData().add(series);
    }
}
