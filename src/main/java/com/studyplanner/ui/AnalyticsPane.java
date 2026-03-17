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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsPane extends ScrollPane implements RefreshableView {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM");

    private final ApplicationContext context;

    private final VBox container = new VBox(24);
    private final Map<Integer, Button> rangeButtons = new LinkedHashMap<>();

    private final Label completionRateLabel = new Label();
    private final Label pomodorosLabel = new Label();
    private final Label averageQualityLabel = new Label();
    private final Label reviewPressureLabel = new Label();

    private final LineChart<String, Number> consistencyChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
    private final BarChart<String, Number> subjectChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
    private final VBox insightList = new VBox(10);
    private final TableView<TopicAnalyticsSnapshot> topicTable = new TableView<>();

    private int selectedRangeDays = 7;

    public AnalyticsPane(ApplicationContext context) {
        this.context = context;

        getStyleClass().add("screen-scroller");
        setFitToWidth(true);
        setPadding(new Insets(0));

        container.getStyleClass().add("screen-root");
        container.setPadding(new Insets(8, 8, 24, 8));
        setContent(container);

        configureCharts();
        configureTopicTable();
    }

    @Override
    public void refresh() {
        container.getChildren().clear();
        container.getChildren().add(buildHeader());

        if (context.isWorkspaceEmpty()) {
            container.getChildren().add(new EmptyStatePane(
                "Analytics wake up after the first sessions",
                "Once topics and study logs exist, this screen will highlight consistency, review pressure, and weak spots."
            ));
            return;
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(Math.max(0, selectedRangeDays - 1L));
        List<TopicAnalyticsSnapshot> topics = context.getPerformanceAnalyticsService().getTopicAnalytics();
        long reviewPressure = topics.stream()
            .filter(topic -> topic.nextReviewDate() != null && !topic.nextReviewDate().isAfter(endDate))
            .count();

        completionRateLabel.setText(FormatUtils.percentage(
            context.getPerformanceAnalyticsService().getCompletionRate(startDate, endDate)
        ));
        pomodorosLabel.setText(String.valueOf(context.getPerformanceAnalyticsService().getTotalPomodorosCompleted()));
        averageQualityLabel.setText(FormatUtils.score(context.getPerformanceAnalyticsService().getAverageSessionQuality()) + "/5");
        reviewPressureLabel.setText(String.valueOf(reviewPressure));

        rebuildConsistencyChart(context.getPerformanceAnalyticsService().getWeeklyStudyConsistency(selectedRangeDays));
        rebuildSubjectChart(context.getPerformanceAnalyticsService().getSubjectBreakdown());
        rebuildInsights(topics);
        topicTable.setItems(FXCollections.observableArrayList(topics));

        container.getChildren().addAll(
            buildFilterBar(),
            buildSummaryRow(),
            buildInsightRow(),
            UiFactory.createCard("Topic Health", topicTable)
        );
    }

    private VBox buildHeader() {
        Label title = new Label("Analytics");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Read the story behind your study behavior, not just a set of charts.");
        subtitle.getStyleClass().add("screen-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private HBox buildFilterBar() {
        Label prompt = new Label("Time range");
        prompt.getStyleClass().add("eyebrow-label");

        HBox filters = new HBox(8, prompt);
        filters.getStyleClass().add("filter-row");

        filters.getChildren().addAll(
            createRangeButton(7, "7D"),
            createRangeButton(30, "30D"),
            createRangeButton(90, "90D")
        );

        Label note = new Label(
            context.getRetentionPredictionService().isModelTrained()
                ? "Retention model is trained from local history."
                : "Retention predictions are still relying on seeded coefficients."
        );
        note.getStyleClass().add("support-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        filters.getChildren().addAll(spacer, note);
        return filters;
    }

    private HBox buildSummaryRow() {
        return new HBox(
            14,
            UiFactory.createSummaryCard("Completion", completionRateLabel, "Observed completion across the selected range."),
            UiFactory.createSummaryCard("Pomodoros", pomodorosLabel, "Estimated completed focus blocks."),
            UiFactory.createSummaryCard("Average Quality", averageQualityLabel, "Mean focus quality on the 1-5 scale."),
            UiFactory.createSummaryCard("Review Pressure", reviewPressureLabel, "Topics due or overdue right now.")
        );
    }

    private HBox buildInsightRow() {
        VBox consistencyCard = UiFactory.createCard("Consistency Trend", consistencyChart);
        consistencyCard.getStyleClass().add("content-card");
        HBox.setHgrow(consistencyCard, Priority.ALWAYS);

        VBox subjectCard = UiFactory.createCard("Subject Focus", subjectChart);
        subjectCard.getStyleClass().add("content-card");
        HBox.setHgrow(subjectCard, Priority.ALWAYS);

        VBox insightCard = UiFactory.createCard("What Needs Attention", insightList);
        insightCard.getStyleClass().add("content-card");
        insightCard.setPrefWidth(360);

        VBox chartStack = new VBox(14, consistencyCard, subjectCard);
        HBox.setHgrow(chartStack, Priority.ALWAYS);
        return new HBox(14, chartStack, insightCard);
    }

    private Button createRangeButton(int days, String label) {
        return rangeButtons.computeIfAbsent(days, key -> {
            Button button = new Button(label);
            button.getStyleClass().add("filter-chip");
            if (key == selectedRangeDays) {
                button.getStyleClass().add("filter-chip-active");
            }
            button.setOnAction(event -> {
                selectedRangeDays = key;
                updateRangeButtons();
                refresh();
            });
            return button;
        });
    }

    private void updateRangeButtons() {
        rangeButtons.forEach((days, button) -> {
            if (days == selectedRangeDays) {
                if (!button.getStyleClass().contains("filter-chip-active")) {
                    button.getStyleClass().add("filter-chip-active");
                }
            } else {
                button.getStyleClass().remove("filter-chip-active");
            }
        });
    }

    private void rebuildInsights(List<TopicAnalyticsSnapshot> topics) {
        insightList.getChildren().clear();
        if (topics.isEmpty()) {
            insightList.getChildren().add(new Label("No topic-level analytics yet."));
            return;
        }

        topics.stream()
            .limit(4)
            .forEach(topic -> insightList.getChildren().add(createInsightRow(topic)));
    }

    private HBox createInsightRow(TopicAnalyticsSnapshot topic) {
        Label titleLabel = new Label(topic.topicName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(
            topic.subjectName() + " • " + FormatUtils.percentage(topic.recallProbability()) + " recall • " + topic.neglectSummary()
        );
        metaLabel.getStyleClass().add("row-copy");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        HBox row = new HBox(copy);
        row.getStyleClass().add("list-row-card");
        return row;
    }

    private void configureCharts() {
        consistencyChart.setAnimated(false);
        consistencyChart.setLegendVisible(false);
        consistencyChart.setCreateSymbols(true);
        consistencyChart.setPrefHeight(260);

        subjectChart.setAnimated(false);
        subjectChart.setLegendVisible(false);
        subjectChart.setPrefHeight(220);
    }

    private void configureTopicTable() {
        topicTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        topicTable.setPlaceholder(new Label("No topic analytics yet."));

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

        TableColumn<TopicAnalyticsSnapshot, String> neglectColumn = new TableColumn<>("Neglect");
        neglectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().neglectSummary()));

        topicTable.getColumns().addAll(
            topicColumn,
            subjectColumn,
            recallColumn,
            nextReviewColumn,
            sessionsColumn,
            neglectColumn
        );
    }

    private void rebuildConsistencyChart(List<WeeklyStudyPoint> points) {
        consistencyChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (WeeklyStudyPoint point : points) {
            String label = selectedRangeDays <= 14
                ? point.date().getDayOfWeek().name().substring(0, 3)
                : SHORT_DATE.format(point.date());
            series.getData().add(new XYChart.Data<>(label, point.minutesStudied()));
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
