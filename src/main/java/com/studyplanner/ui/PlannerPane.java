package com.studyplanner.ui;

import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.PomodoroBlock;
import com.studyplanner.service.ApplicationContext;
import com.studyplanner.utils.FormatUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class PlannerPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable afterMutation;

    private final DatePicker planDatePicker = new DatePicker(LocalDate.now());
    private final Spinner<Integer> availableMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 720, 180, 15));
    private final Spinner<Integer> focusMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 60, 25, 5));
    private final Spinner<Integer> shortBreakSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 5, 5));

    private final Label totalTimeLabel = new Label();
    private final Label totalPomodorosLabel = new Label();
    private final Label summaryLabel = new Label();

    private final TableView<PlanItem> planTable = new TableView<>();
    private final Label detailTopicLabel = new Label("Select a plan item");
    private final Label detailReasonLabel = new Label();
    private final Label detailRecallLabel = new Label();
    private final Label detailScoreLabel = new Label();
    private final ListView<String> pomodoroList = new ListView<>();

    public PlannerPane(ApplicationContext context, Runnable afterMutation) {
        this.context = context;
        this.afterMutation = afterMutation;
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
        planTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> updateDetail(newValue));
    }

    @Override
    public void refresh() {
        LocalDate planDate = planDatePicker.getValue() != null ? planDatePicker.getValue() : LocalDate.now();
        DailyPlan plan = context.getSchedulerService().getPlan(planDate).orElse(null);

        if (plan == null) {
            planTable.setItems(FXCollections.observableArrayList());
            totalTimeLabel.setText("0m");
            totalPomodorosLabel.setText("0");
            summaryLabel.setText("No plan generated for this date yet.");
            updateDetail(null);
            return;
        }

        planTable.setItems(FXCollections.observableArrayList(plan.getItems()));
        totalTimeLabel.setText(plan.getTotalPlannedMinutes() + " min");
        totalPomodorosLabel.setText(String.valueOf(plan.getItems().stream().mapToInt(PlanItem::getPomodoroCount).sum()));
        summaryLabel.setText(plan.getSummary());
        if (!plan.getItems().isEmpty()) {
            planTable.getSelectionModel().selectFirst();
        }
    }

    private VBox buildContent() {
        configurePlanTable();

        Button generateButton = new Button("Generate / Regenerate Plan");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setOnAction(event -> generatePlan());

        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(10);
        controls.add(new Label("Plan Date"), 0, 0);
        controls.add(planDatePicker, 1, 0);
        controls.add(new Label("Available Minutes"), 2, 0);
        controls.add(availableMinutesSpinner, 3, 0);
        controls.add(new Label("Focus Minutes"), 4, 0);
        controls.add(focusMinutesSpinner, 5, 0);
        controls.add(new Label("Short Break"), 6, 0);
        controls.add(shortBreakSpinner, 7, 0);
        controls.add(generateButton, 8, 0);

        Label timeValue = totalTimeLabel;
        Label pomodoroValue = totalPomodorosLabel;
        HBox summaryRow = new HBox(
            12,
            UiFactory.createSummaryCard("Total Time", timeValue, "All selected plan blocks combined."),
            UiFactory.createSummaryCard("Pomodoros", pomodoroValue, "Count derived from the generated focus blocks.")
        );

        VBox left = new VBox(14, UiFactory.createCard("Planner Controls", controls, summaryLabel), UiFactory.createCard("Plan Items", planTable));
        VBox right = UiFactory.createCard("Recommendation Detail", detailTopicLabel, detailReasonLabel, detailRecallLabel, detailScoreLabel, pomodoroList);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(left, right);
        splitPane.setDividerPositions(0.68);

        VBox container = new VBox(16, summaryRow, splitPane);
        container.setPadding(new Insets(8));
        return container;
    }

    private void configurePlanTable() {
        planTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<PlanItem, Number> orderColumn = new TableColumn<>("Order");
        orderColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getRecommendedOrder()));

        TableColumn<PlanItem, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTopicName()));

        TableColumn<PlanItem, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getItemType().name()));

        TableColumn<PlanItem, Number> minutesColumn = new TableColumn<>("Minutes");
        minutesColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPlannedMinutes()));

        TableColumn<PlanItem, Number> pomodoroColumn = new TableColumn<>("Pomodoros");
        pomodoroColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPomodoroCount()));

        TableColumn<PlanItem, String> recallColumn = new TableColumn<>("Recall");
        recallColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.percentage(data.getValue().getRecallProbability())));

        TableColumn<PlanItem, String> scoreColumn = new TableColumn<>("Score");
        scoreColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(FormatUtils.score(data.getValue().getScore())));

        TableColumn<PlanItem, String> reasonColumn = new TableColumn<>("Reason");
        reasonColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getReason()));

        planTable.getColumns().addAll(orderColumn, topicColumn, typeColumn, minutesColumn, pomodoroColumn, recallColumn, scoreColumn, reasonColumn);
    }

    private void generatePlan() {
        try {
            context.getSchedulerService().generateDailyPlan(new PlanGenerationRequest(
                planDatePicker.getValue(),
                availableMinutesSpinner.getValue(),
                focusMinutesSpinner.getValue(),
                shortBreakSpinner.getValue()
            ));
            afterMutation.run();
        } catch (Exception exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Plan generation failed");
            alert.setContentText(exception.getMessage());
            alert.showAndWait();
        }
    }

    private void updateDetail(PlanItem item) {
        if (item == null) {
            detailTopicLabel.setText("Select a plan item");
            detailReasonLabel.setText("Reason: n/a");
            detailRecallLabel.setText("Recall: n/a");
            detailScoreLabel.setText("Score: n/a");
            pomodoroList.setItems(FXCollections.observableArrayList());
            return;
        }

        detailTopicLabel.setText(item.getSubjectName() + " / " + item.getTopicName());
        detailReasonLabel.setText("Reason: " + item.getReason());
        detailReasonLabel.setWrapText(true);
        detailRecallLabel.setText("Recall probability: " + FormatUtils.percentage(item.getRecallProbability()));
        detailScoreLabel.setText("Weighted score: " + FormatUtils.score(item.getScore()));

        List<String> pomodoroLines = item.getPomodoroBlocks().stream()
            .map(this::formatPomodoro)
            .toList();
        pomodoroList.setItems(FXCollections.observableArrayList(pomodoroLines));
    }

    private String formatPomodoro(PomodoroBlock block) {
        return "Block " + block.getBlockIndex() + ": " + block.getFocusMinutes() + "m focus / " + block.getBreakMinutes() + "m break";
    }
}
