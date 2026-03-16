package com.studyplanner.ui;

import com.studyplanner.dto.SubjectStudyBreakdown;
import com.studyplanner.dto.TopicAnalyticsSnapshot;
import com.studyplanner.dto.WeeklyStudyPoint;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class AnalyticsPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;

    private final Label completionRateLabel = new Label();
    private final Label pomodorosLabel = new Label();
    private final Label averageQualityLabel = new Label();
    private final Label modelAccuracyLabel = new Label();

    private final LineChart<String, Number> consistencyChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final BarChart<String, Number> subjectChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final TableView<TopicAnalyticsSnapshot> topicTable = new TableView<>();

    public AnalyticsPane(ApplicationContext context) {
        this.context = context;
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
    }

    @Override
    public void refresh() {
        completionRateLabel.setText(FormatUtils.percentage(
            context.getPerformanceAnalyticsService().getCompletionRate(LocalDate.now().minusDays(6), LocalDate.now())
        ));
        pomodorosLabel.setText(String.valueOf(context.getPerformanceAnalyticsService().getTotalPomodorosCompleted()));
        averageQualityLabel.setText(FormatUtils.score(context.getPerformanceAnalyticsService().getAverageSessionQuality()) + "/5");
        modelAccuracyLabel.setText(
            context.getRetentionPredictionService().isModelTrained()
                ? FormatUtils.percentage(context.getRetentionPredictionService().getTrainingAccuracy())
                : "Seeded"
        );

        rebuildConsistencyChart(context.getPerformanceAnalyticsService().getWeeklyStudyConsistency(7));
        rebuildSubjectChart(context.getPerformanceAnalyticsService().getSubjectBreakdown());
        topicTable.setItems(FXCollections.observableArrayList(context.getPerformanceAnalyticsService().getTopicAnalytics()));
    }

    private VBox buildContent() {
        configureCharts();
        configureTopicTable();

        HBox summaryRow = new HBox(
            14,
            UiFactory.createSummaryCard("Completion Rate", completionRateLabel, "Last 7 days across all logged sessions."),
            UiFactory.createSummaryCard("Pomodoros Completed", pomodorosLabel, "Estimated from logged session duration."),
            UiFactory.createSummaryCard("Average Quality", averageQualityLabel, "Mean focus quality on the 1-5 scale."),
            UiFactory.createSummaryCard("Model Accuracy", modelAccuracyLabel, "Training-set accuracy for the logistic model.")
        );

        VBox consistencyCard = UiFactory.createCard("Weekly Consistency", consistencyChart);
        VBox subjectCard = UiFactory.createCard("Subject-Wise Minutes", subjectChart);
        HBox.setHgrow(consistencyCard, Priority.ALWAYS);
        HBox.setHgrow(subjectCard, Priority.ALWAYS);

        HBox chartRow = new HBox(14, consistencyCard, subjectCard);
        VBox tableCard = UiFactory.createCard("Topic Analytics", topicTable);

        VBox container = new VBox(16, summaryRow, chartRow, tableCard);
        container.setPadding(new Insets(8));
        return container;
    }

    private void configureCharts() {
        consistencyChart.setAnimated(false);
        consistencyChart.setLegendVisible(false);
        consistencyChart.setCreateSymbols(true);
        consistencyChart.setPrefHeight(300);

        subjectChart.setAnimated(false);
        subjectChart.setLegendVisible(false);
        subjectChart.setPrefHeight(300);
    }

    private void configureTopicTable() {
        topicTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TopicAnalyticsSnapshot, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().topicName()));

        TableColumn<TopicAnalyticsSnapshot, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().subjectName()));

        TableColumn<TopicAnalyticsSnapshot, String> recallColumn = new TableColumn<>("Recall");
        recallColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.percentage(data.getValue().recallProbability())));

        TableColumn<TopicAnalyticsSnapshot, Object> nextReviewColumn = new TableColumn<>("Next Review");
        nextReviewColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().nextReviewDate()));

        TableColumn<TopicAnalyticsSnapshot, Number> sessionsColumn = new TableColumn<>("Sessions");
        sessionsColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalSessions()));

        TableColumn<TopicAnalyticsSnapshot, Number> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().totalTimeSpentMinutes()));

        TableColumn<TopicAnalyticsSnapshot, String> revisionColumn = new TableColumn<>("Revision Success");
        revisionColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.percentage(data.getValue().revisionSuccessRate())));

        TableColumn<TopicAnalyticsSnapshot, String> neglectColumn = new TableColumn<>("Neglect");
        neglectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().neglectSummary()));

        topicTable.getColumns().addAll(
            topicColumn,
            subjectColumn,
            recallColumn,
            nextReviewColumn,
            sessionsColumn,
            timeColumn,
            revisionColumn,
            neglectColumn
        );
    }

    private void rebuildConsistencyChart(List<WeeklyStudyPoint> points) {
        consistencyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (WeeklyStudyPoint point : points) {
            series.getData().add(new XYChart.Data<>(point.date().getDayOfWeek().name().substring(0, 3), point.minutesStudied()));
        }
        consistencyChart.getData().add(series);
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
