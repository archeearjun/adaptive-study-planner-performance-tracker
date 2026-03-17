package com.studyplanner.ui;

import com.studyplanner.dto.SessionLogRequest;
import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.service.ApplicationContext;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;

public class SessionLoggerPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable afterMutation;
    private final Runnable openPlanner;

    private final ComboBox<TopicOverview> topicCombo = new ComboBox<>();
    private final ComboBox<PlanItem> planItemCombo = new ComboBox<>();
    private final DatePicker sessionDatePicker = new DatePicker(LocalDate.now());
    private final Spinner<Integer> plannedMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 180, 45, 5));
    private final Spinner<Integer> actualMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 240, 45, 5));
    private final ComboBox<SessionStatus> statusCombo = new ComboBox<>();
    private final Spinner<Integer> focusQualitySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 4, 1));
    private final Spinner<Integer> confidenceSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 65, 1));
    private final TextField quizScoreField = new TextField();
    private final CheckBox addReviewUpdateBox = new CheckBox("Update spaced repetition");
    private final Spinner<Integer> reviewQualitySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 5, 4, 1));
    private final CheckBox reviewSessionBox = new CheckBox("This was a review block");
    private final TextArea notesArea = new TextArea();

    private final VBox container = new VBox(24);
    private final VBox todayQueueList = new VBox(10);
    private final TableView<StudySession> recentSessionsTable = new TableView<>();
    private final ProgressBar dayProgressBar = new ProgressBar(0);
    private final Label dayProgressLabel = new Label();
    private final Label helperLabel = new Label();
    private final Label statusBanner = new Label();

    public SessionLoggerPane(ApplicationContext context, Runnable afterMutation, Runnable openPlanner) {
        this.context = context;
        this.afterMutation = afterMutation;
        this.openPlanner = openPlanner;

        getStyleClass().add("screen-scroller");
        setFitToWidth(true);
        setPadding(new Insets(0));

        configureCombos();
        configureRecentSessionsTable();

        statusCombo.setItems(FXCollections.observableArrayList(
            SessionStatus.COMPLETED,
            SessionStatus.PARTIALLY_COMPLETED,
            SessionStatus.SKIPPED
        ));
        statusCombo.setValue(SessionStatus.COMPLETED);
        notesArea.setPrefRowCount(4);
        reviewQualitySpinner.setDisable(true);
        helperLabel.getStyleClass().add("support-label");
        helperLabel.setWrapText(true);

        statusBanner.getStyleClass().add("inline-banner");
        statusBanner.setVisible(false);
        statusBanner.setManaged(false);

        addReviewUpdateBox.selectedProperty().addListener((obs, oldValue, selected) -> reviewQualitySpinner.setDisable(!selected));
        topicCombo.valueProperty().addListener((obs, oldValue, selected) -> refreshRecentSessions());
        planItemCombo.valueProperty().addListener((obs, oldValue, item) -> applyPlanItemSelection(item));

        container.getStyleClass().add("screen-root");
        container.setPadding(new Insets(8, 8, 24, 8));
        setContent(container);
    }

    @Override
    public void refresh() {
        container.getChildren().clear();
        container.getChildren().add(buildHeader());

        List<TopicOverview> topics = context.getTopicService().getTopicOverviews().stream()
            .filter(topic -> !topic.archived())
            .toList();
        topicCombo.setItems(FXCollections.observableArrayList(topics));

        List<PlanItem> todayItems = context.getSchedulerService().getPlan(LocalDate.now())
            .map(plan -> plan.getItems())
            .orElse(List.of());
        planItemCombo.setItems(FXCollections.observableArrayList(todayItems));

        if (topics.isEmpty()) {
            Button plannerButton = new Button("Open Planner");
            plannerButton.getStyleClass().add("secondary-button");
            plannerButton.setOnAction(event -> openPlanner.run());

            container.getChildren().add(new EmptyStatePane(
                "No topics available",
                "Create at least one topic before logging study work.",
                plannerButton
            ));
            return;
        }

        if (topicCombo.getValue() == null) {
            topicCombo.setValue(topics.get(0));
        }

        rebuildTodayQueue(todayItems);
        refreshRecentSessions();
        updateDayProgress(todayItems);
        helperLabel.setText(
            "Quick log the outcome now. Review quality, quiz score, and notes stay available in the advanced section when you need them."
        );

        container.getChildren().add(buildMainRow(todayItems));
    }

    private VBox buildHeader() {
        Label title = new Label("Session");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Capture progress fast, then let the planner react to what actually happened.");
        subtitle.getStyleClass().add("screen-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private HBox buildMainRow(List<PlanItem> todayItems) {
        VBox formCard = UiFactory.createCard(
            "Quick Log",
            buildProgressStrip(),
            buildQuickForm(),
            buildAdvancedPane(),
            helperLabel,
            statusBanner,
            buildFormActions()
        );
        formCard.getStyleClass().add("content-card");
        HBox.setHgrow(formCard, Priority.ALWAYS);

        VBox contextCard = UiFactory.createCard(
            "Today's Queue",
            todayQueueList,
            buildQueueFooter(todayItems),
            createSection("Recent Sessions", recentSessionsTable)
        );
        contextCard.getStyleClass().add("content-card");
        contextCard.setPrefWidth(420);

        return new HBox(14, formCard, contextCard);
    }

    private HBox buildProgressStrip() {
        dayProgressBar.setMaxWidth(Double.MAX_VALUE);
        dayProgressBar.getStyleClass().add("progress-track");
        HBox.setHgrow(dayProgressBar, Priority.ALWAYS);

        HBox row = new HBox(12, dayProgressBar, dayProgressLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private GridPane buildQuickForm() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(new Label("Planned Item"), 0, 0);
        grid.add(planItemCombo, 1, 0);
        grid.add(new Label("Topic"), 2, 0);
        grid.add(topicCombo, 3, 0);
        grid.add(new Label("Actual Minutes"), 0, 1);
        grid.add(actualMinutesSpinner, 1, 1);
        grid.add(new Label("Status"), 2, 1);
        grid.add(statusCombo, 3, 1);
        grid.add(new Label("Focus Quality"), 0, 2);
        grid.add(focusQualitySpinner, 1, 2);
        grid.add(new Label("Confidence After"), 2, 2);
        grid.add(confidenceSpinner, 3, 2);
        GridPane.setHgrow(planItemCombo, Priority.ALWAYS);
        GridPane.setHgrow(topicCombo, Priority.ALWAYS);
        return grid;
    }

    private TitledPane buildAdvancedPane() {
        GridPane advanced = new GridPane();
        advanced.setHgap(12);
        advanced.setVgap(12);
        advanced.add(new Label("Session Date"), 0, 0);
        advanced.add(sessionDatePicker, 1, 0);
        advanced.add(new Label("Planned Minutes"), 2, 0);
        advanced.add(plannedMinutesSpinner, 3, 0);
        advanced.add(new Label("Quiz Score"), 0, 1);
        advanced.add(quizScoreField, 1, 1);
        advanced.add(addReviewUpdateBox, 2, 1);
        advanced.add(reviewQualitySpinner, 3, 1);
        advanced.add(reviewSessionBox, 1, 2);
        advanced.add(new Label("Notes"), 0, 3);
        advanced.add(notesArea, 1, 3, 3, 1);
        GridPane.setHgrow(notesArea, Priority.ALWAYS);

        TitledPane pane = new TitledPane("Advanced Details", advanced);
        pane.setExpanded(false);
        pane.getStyleClass().add("advanced-pane");
        return pane;
    }

    private HBox buildFormActions() {
        Button logButton = new Button("Log Session");
        logButton.getStyleClass().add("primary-button");
        logButton.setOnAction(event -> logSession());

        Button plannerButton = new Button("Back To Planner");
        plannerButton.getStyleClass().add("secondary-button");
        plannerButton.setOnAction(event -> openPlanner.run());

        return new HBox(10, logButton, plannerButton);
    }

    private VBox buildQueueFooter(List<PlanItem> todayItems) {
        if (todayItems.isEmpty()) {
            return new VBox(new EmptyStatePane(
                "No plan items for today",
                "Generate a plan if you want queue-based logging. You can still log directly against any topic."
            ));
        }

        Label footer = new Label("Pick a plan item to prefill the log and keep the day in sync.");
        footer.getStyleClass().add("support-label");
        footer.setWrapText(true);
        return new VBox(footer);
    }

    private VBox createSection(String title, TableView<StudySession> table) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        return new VBox(10, titleLabel, table);
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
        recentSessionsTable.setPlaceholder(new Label("Pick a topic to see recent work."));

        TableColumn<StudySession, Object> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getSessionDate()));

        TableColumn<StudySession, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getStatus().name()));

        TableColumn<StudySession, Number> minutesColumn = new TableColumn<>("Minutes");
        minutesColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getActualMinutes()));

        TableColumn<StudySession, Number> qualityColumn = new TableColumn<>("Focus");
        qualityColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getFocusQuality()));

        recentSessionsTable.getColumns().addAll(dateColumn, statusColumn, minutesColumn, qualityColumn);
    }

    private void rebuildTodayQueue(List<PlanItem> todayItems) {
        todayQueueList.getChildren().clear();
        if (todayItems.isEmpty()) {
            todayQueueList.getChildren().add(new EmptyStatePane(
                "Nothing scheduled today",
                "You can still log work directly against a topic, or generate a plan for a queue-driven workflow."
            ));
            return;
        }

        todayItems.forEach(item -> todayQueueList.getChildren().add(createQueueCard(item)));
    }

    private HBox createQueueCard(PlanItem item) {
        Label titleLabel = new Label("#" + item.getRecommendedOrder() + " " + item.getTopicName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(item.getPlannedMinutes() + " min • " + item.getItemType() + " • " + item.getStatus());
        metaLabel.getStyleClass().add("row-copy");

        Label chip = UiFactory.createChip(item.getItemType().name(), item.getItemType().name().equals("REVIEW")
            ? "chip-warning"
            : "chip-neutral");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, copy, spacer, chip);
        row.getStyleClass().add("list-row-card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOnMouseClicked(event -> planItemCombo.setValue(item));
        return row;
    }

    private void updateDayProgress(List<PlanItem> todayItems) {
        if (todayItems.isEmpty()) {
            dayProgressBar.setProgress(0);
            dayProgressLabel.setText("No plan loaded for today");
            return;
        }

        long doneCount = todayItems.stream()
            .filter(item -> item.getStatus() == SessionStatus.COMPLETED || item.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .count();
        dayProgressBar.setProgress(doneCount / (double) todayItems.size());
        dayProgressLabel.setText(doneCount + " of " + todayItems.size() + " planned items touched today");
    }

    private void applyPlanItemSelection(PlanItem item) {
        if (item == null) {
            return;
        }
        plannedMinutesSpinner.getValueFactory().setValue(item.getPlannedMinutes());
        reviewSessionBox.setSelected(item.getItemType().name().equals("REVIEW"));
        context.getTopicService().getTopicOverviews().stream()
            .filter(topic -> topic.topicId() == item.getTopicId())
            .findFirst()
            .ifPresent(topicCombo::setValue);
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

            showStatus("Session logged for " + topic.topicName() + ".");
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

    private void showStatus(String message) {
        statusBanner.setText(message);
        statusBanner.setVisible(true);
        statusBanner.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
        pause.setOnFinished(event -> {
            statusBanner.setVisible(false);
            statusBanner.setManaged(false);
        });
        pause.playFromStart();
    }
}
