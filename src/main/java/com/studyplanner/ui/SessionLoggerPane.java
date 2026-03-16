package com.studyplanner.ui;

import com.studyplanner.dto.SessionLogRequest;
import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.service.ApplicationContext;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

public class SessionLoggerPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable afterMutation;

    private final ComboBox<TopicOverview> topicCombo = new ComboBox<>();
    private final ComboBox<PlanItem> planItemCombo = new ComboBox<>();
    private final DatePicker sessionDatePicker = new DatePicker(LocalDate.now());
    private final Spinner<Integer> plannedMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 180, 45, 5));
    private final Spinner<Integer> actualMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 240, 45, 5));
    private final ComboBox<SessionStatus> statusCombo = new ComboBox<>();
    private final Spinner<Integer> focusQualitySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 4, 1));
    private final Spinner<Integer> confidenceSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 65, 1));
    private final TextField quizScoreField = new TextField();
    private final CheckBox addReviewUpdateBox = new CheckBox("Update SM-2 review state");
    private final Spinner<Integer> reviewQualitySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 5, 4, 1));
    private final CheckBox reviewSessionBox = new CheckBox("Review session");
    private final TextArea notesArea = new TextArea();

    private final TableView<StudySession> recentSessionsTable = new TableView<>();
    private final Label helperLabel = new Label();

    public SessionLoggerPane(ApplicationContext context, Runnable afterMutation) {
        this.context = context;
        this.afterMutation = afterMutation;
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
        configureCombos();
    }

    @Override
    public void refresh() {
        List<TopicOverview> topics = context.getTopicService().getTopicOverviews().stream()
            .filter(topic -> !topic.archived())
            .toList();
        topicCombo.setItems(FXCollections.observableArrayList(topics));

        List<PlanItem> todayItems = context.getSchedulerService().getPlan(LocalDate.now())
            .map(plan -> plan.getItems())
            .orElse(List.of());
        planItemCombo.setItems(FXCollections.observableArrayList(todayItems));

        if (!topics.isEmpty() && topicCombo.getValue() == null) {
            topicCombo.setValue(topics.get(0));
        }
        refreshRecentSessions();
        helperLabel.setText("Logging a session updates confidence immediately. Adding a review quality also runs the SM-2 update and stores a retention training example.");
    }

    private VBox buildContent() {
        configureRecentSessionsTable();
        statusCombo.setItems(FXCollections.observableArrayList(SessionStatus.COMPLETED, SessionStatus.PARTIALLY_COMPLETED, SessionStatus.SKIPPED));
        statusCombo.setValue(SessionStatus.COMPLETED);
        notesArea.setPrefRowCount(5);
        reviewQualitySpinner.setDisable(true);

        addReviewUpdateBox.selectedProperty().addListener((obs, oldValue, selected) -> reviewQualitySpinner.setDisable(!selected));
        topicCombo.valueProperty().addListener((obs, oldValue, selected) -> refreshRecentSessions());
        planItemCombo.valueProperty().addListener((obs, oldValue, item) -> {
            if (item == null) {
                return;
            }
            plannedMinutesSpinner.getValueFactory().setValue(item.getPlannedMinutes());
            reviewSessionBox.setSelected(item.getItemType().name().equals("REVIEW"));
            context.getTopicService().getTopicOverviews().stream()
                .filter(topic -> topic.topicId() == item.getTopicId())
                .findFirst()
                .ifPresent(topicCombo::setValue);
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Topic"), 0, 0);
        grid.add(topicCombo, 1, 0);
        grid.add(new Label("Plan Item"), 2, 0);
        grid.add(planItemCombo, 3, 0);
        grid.add(new Label("Session Date"), 0, 1);
        grid.add(sessionDatePicker, 1, 1);
        grid.add(new Label("Planned Minutes"), 2, 1);
        grid.add(plannedMinutesSpinner, 3, 1);
        grid.add(new Label("Actual Minutes"), 0, 2);
        grid.add(actualMinutesSpinner, 1, 2);
        grid.add(new Label("Status"), 2, 2);
        grid.add(statusCombo, 3, 2);
        grid.add(new Label("Focus Quality"), 0, 3);
        grid.add(focusQualitySpinner, 1, 3);
        grid.add(new Label("Confidence After"), 2, 3);
        grid.add(confidenceSpinner, 3, 3);
        grid.add(new Label("Quiz Score"), 0, 4);
        grid.add(quizScoreField, 1, 4);
        grid.add(addReviewUpdateBox, 2, 4);
        grid.add(reviewQualitySpinner, 3, 4);
        grid.add(reviewSessionBox, 1, 5);
        grid.add(new Label("Notes"), 0, 6);
        grid.add(notesArea, 1, 6, 3, 1);
        GridPane.setHgrow(topicCombo, Priority.ALWAYS);
        GridPane.setHgrow(planItemCombo, Priority.ALWAYS);
        GridPane.setHgrow(notesArea, Priority.ALWAYS);

        Button logButton = new Button("Log Session");
        logButton.getStyleClass().add("primary-button");
        logButton.setOnAction(event -> logSession());

        VBox formCard = UiFactory.createCard("Session Logger", grid, helperLabel, new HBox(10, logButton));
        VBox tableCard = UiFactory.createCard("Recent Sessions For Topic", recentSessionsTable);

        SplitPane splitPane = new SplitPane(formCard, tableCard);
        splitPane.setDividerPositions(0.55);

        VBox container = new VBox(16, splitPane);
        container.setPadding(new Insets(8));
        return container;
    }

    private void configureCombos() {
        topicCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TopicOverview topic) {
                return topic == null ? "" : topic.subjectName() + " / " + topic.topicName();
            }

            @Override
            public TopicOverview fromString(String string) {
                return null;
            }
        });

        planItemCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlanItem item) {
                return item == null ? "" : "#" + item.getRecommendedOrder() + " " + item.getTopicName() + " (" + item.getItemType() + ")";
            }

            @Override
            public PlanItem fromString(String string) {
                return null;
            }
        });
    }

    private void configureRecentSessionsTable() {
        recentSessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<StudySession, Object> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSessionDate()));

        TableColumn<StudySession, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        TableColumn<StudySession, Number> minutesColumn = new TableColumn<>("Minutes");
        minutesColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getActualMinutes()));

        TableColumn<StudySession, Number> qualityColumn = new TableColumn<>("Focus");
        qualityColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFocusQuality()));

        TableColumn<StudySession, Number> confidenceColumn = new TableColumn<>("Confidence");
        confidenceColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(Math.round(data.getValue().getConfidenceAfter())));

        recentSessionsTable.getColumns().addAll(dateColumn, statusColumn, minutesColumn, qualityColumn, confidenceColumn);
    }

    private void refreshRecentSessions() {
        TopicOverview selectedTopic = topicCombo.getValue();
        if (selectedTopic == null) {
            recentSessionsTable.setItems(FXCollections.observableArrayList());
            return;
        }
        recentSessionsTable.setItems(FXCollections.observableArrayList(
            context.getTopicService().getTopicDetail(selectedTopic.topicId()).recentSessions()
        ));
    }

    private void logSession() {
        try {
            TopicOverview topic = topicCombo.getValue();
            if (topic == null) {
                throw new IllegalArgumentException("Select a topic before logging a session.");
            }

            Double quizScore = quizScoreField.getText().isBlank() ? null : Double.parseDouble(quizScoreField.getText().trim());
            Integer reviewQuality = addReviewUpdateBox.isSelected() ? reviewQualitySpinner.getValue() : null;
            Long planItemId = planItemCombo.getValue() == null ? null : planItemCombo.getValue().getId();

            context.getSessionLoggingService().logSession(new SessionLogRequest(
                planItemId,
                topic.topicId(),
                sessionDatePicker.getValue(),
                plannedMinutesSpinner.getValue(),
                actualMinutesSpinner.getValue(),
                statusCombo.getValue(),
                focusQualitySpinner.getValue(),
                confidenceSpinner.getValue(),
                quizScore,
                reviewQuality,
                reviewSessionBox.isSelected(),
                notesArea.getText().trim()
            ));

            clearForm();
            afterMutation.run();
        } catch (Exception exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Session log failed");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }

    private void clearForm() {
        planItemCombo.setValue(null);
        sessionDatePicker.setValue(LocalDate.now());
        plannedMinutesSpinner.getValueFactory().setValue(45);
        actualMinutesSpinner.getValueFactory().setValue(45);
        statusCombo.setValue(SessionStatus.COMPLETED);
        focusQualitySpinner.getValueFactory().setValue(4);
        confidenceSpinner.getValueFactory().setValue(65);
        quizScoreField.clear();
        addReviewUpdateBox.setSelected(false);
        reviewQualitySpinner.getValueFactory().setValue(4);
        reviewSessionBox.setSelected(false);
        notesArea.clear();
    }
}
