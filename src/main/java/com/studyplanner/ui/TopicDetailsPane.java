package com.studyplanner.ui;

import com.studyplanner.dto.TimeSeriesPoint;
import com.studyplanner.dto.TopicDetailSnapshot;
import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.StudySession;
import com.studyplanner.service.ApplicationContext;
import com.studyplanner.utils.FormatUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class TopicDetailsPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;

    private final TableView<TopicOverview> topicTable = new TableView<>();
    private final LineChart<Number, Number> confidenceChart = new LineChart<>(new NumberAxis(), new NumberAxis());
    private final TableView<StudySession> sessionTable = new TableView<>();
    private final TableView<ReviewRecord> reviewTable = new TableView<>();

    private final Label titleLabel = new Label("Select a topic");
    private final Label totalSessionsLabel = new Label();
    private final Label totalTimeLabel = new Label();
    private final Label recallLabel = new Label();
    private final Label nextReviewLabel = new Label();
    private final Label revisionSuccessLabel = new Label();

    public TopicDetailsPane(ApplicationContext context) {
        this.context = context;
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
        topicTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, topic) -> loadTopic(topic));
    }

    @Override
    public void refresh() {
        long selectedTopicId = topicTable.getSelectionModel().getSelectedItem() == null
            ? -1
            : topicTable.getSelectionModel().getSelectedItem().topicId();

        topicTable.setItems(FXCollections.observableArrayList(context.getTopicService().getTopicOverviews()));
        if (!topicTable.getItems().isEmpty()) {
            TopicOverview selected = topicTable.getItems().stream()
                .filter(topic -> topic.topicId() == selectedTopicId)
                .findFirst()
                .orElse(topicTable.getItems().get(0));
            topicTable.getSelectionModel().select(selected);
            loadTopic(selected);
        }
    }

    private SplitPane buildContent() {
        configureTopicTable();
        configureSessionTable();
        configureReviewTable();
        configureChart();

        VBox left = UiFactory.createCard("Topics", topicTable);
        VBox right = new VBox(
            14,
            buildSummaryCard(),
            UiFactory.createCard("Confidence Trend", confidenceChart),
            UiFactory.createCard("Recent Sessions", sessionTable),
            UiFactory.createCard("Review History", reviewTable)
        );
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.32);
        splitPane.setPadding(new Insets(8));
        return splitPane;
    }

    private VBox buildSummaryCard() {
        GridPane metrics = new GridPane();
        metrics.setHgap(16);
        metrics.setVgap(10);
        metrics.add(new Label("Total Sessions"), 0, 0);
        metrics.add(totalSessionsLabel, 1, 0);
        metrics.add(new Label("Total Time"), 0, 1);
        metrics.add(totalTimeLabel, 1, 1);
        metrics.add(new Label("Recall Probability"), 2, 0);
        metrics.add(recallLabel, 3, 0);
        metrics.add(new Label("Next Review"), 2, 1);
        metrics.add(nextReviewLabel, 3, 1);
        metrics.add(new Label("Revision Success"), 4, 0);
        metrics.add(revisionSuccessLabel, 5, 0);

        return UiFactory.createCard("Topic Snapshot", titleLabel, metrics);
    }

    private void configureTopicTable() {
        topicTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<TopicOverview, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().subjectName()));

        TableColumn<TopicOverview, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().topicName()));

        TableColumn<TopicOverview, String> nextReviewColumn = new TableColumn<>("Next Review");
        nextReviewColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(String.valueOf(data.getValue().nextReviewDate())));

        topicTable.getColumns().addAll(subjectColumn, topicColumn, nextReviewColumn);
    }

    private void configureSessionTable() {
        sessionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<StudySession, Object> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSessionDate()));
        TableColumn<StudySession, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));
        TableColumn<StudySession, Number> actualColumn = new TableColumn<>("Actual");
        actualColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getActualMinutes()));
        TableColumn<StudySession, Number> confidenceColumn = new TableColumn<>("Confidence");
        confidenceColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(Math.round(data.getValue().getConfidenceAfter())));
        sessionTable.getColumns().addAll(dateColumn, statusColumn, actualColumn, confidenceColumn);
    }

    private void configureReviewTable() {
        reviewTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<ReviewRecord, Object> dateColumn = new TableColumn<>("Review Date");
        dateColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getReviewDate()));
        TableColumn<ReviewRecord, Number> qualityColumn = new TableColumn<>("Quality");
        qualityColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getQuality()));
        TableColumn<ReviewRecord, Number> intervalColumn = new TableColumn<>("Next Interval");
        intervalColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getIntervalAfter()));
        TableColumn<ReviewRecord, Object> nextReviewColumn = new TableColumn<>("Next Review");
        nextReviewColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getNextReviewDate()));
        reviewTable.getColumns().addAll(dateColumn, qualityColumn, intervalColumn, nextReviewColumn);
    }

    private void configureChart() {
        confidenceChart.setAnimated(false);
        confidenceChart.setLegendVisible(false);
        confidenceChart.setCreateSymbols(true);
        confidenceChart.setPrefHeight(280);
        confidenceChart.getXAxis().setLabel("Study Events");
        confidenceChart.getYAxis().setLabel("Confidence %");
    }

    private void loadTopic(TopicOverview topicOverview) {
        if (topicOverview == null) {
            return;
        }

        TopicDetailSnapshot snapshot = context.getTopicService().getTopicDetail(topicOverview.topicId());
        titleLabel.setText(snapshot.subjectName() + " / " + snapshot.topic().getName());
        totalSessionsLabel.setText(String.valueOf(snapshot.totalSessions()));
        totalTimeLabel.setText(snapshot.totalTimeSpentMinutes() + " min");
        recallLabel.setText(FormatUtils.percentage(snapshot.recallProbability()));
        nextReviewLabel.setText(String.valueOf(snapshot.topic().getNextReviewDate()));
        revisionSuccessLabel.setText(FormatUtils.percentage(snapshot.revisionSuccessRate()));
        sessionTable.setItems(FXCollections.observableArrayList(snapshot.recentSessions()));
        reviewTable.setItems(FXCollections.observableArrayList(snapshot.reviewHistory()));
        rebuildConfidenceChart(snapshot.confidenceTrend());
    }

    private void rebuildConfidenceChart(java.util.List<TimeSeriesPoint> points) {
        confidenceChart.getData().clear();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        for (int index = 0; index < points.size(); index++) {
            series.getData().add(new XYChart.Data<>(index + 1, points.get(index).value()));
        }
        confidenceChart.getData().add(series);
    }
}
