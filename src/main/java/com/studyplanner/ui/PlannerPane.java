package com.studyplanner.ui;

import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.PlanItemType;
import com.studyplanner.model.PomodoroBlock;
import com.studyplanner.service.ApplicationContext;
import com.studyplanner.utils.FormatUtils;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.List;

public class PlannerPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable afterMutation;
    private final Runnable openSession;

    private final DatePicker planDatePicker = new DatePicker(LocalDate.now());
    private final Spinner<Integer> availableMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 720, 180, 15));
    private final Spinner<Integer> focusMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 60, 25, 5));
    private final Spinner<Integer> shortBreakSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 5, 5));

    private final Label totalTimeLabel = new Label();
    private final Label totalPomodorosLabel = new Label();
    private final Label summaryLabel = new Label();
    private final Label statusLabel = new Label();

    private final VBox container = new VBox(24);
    private final VBox reviewList = new VBox(10);
    private final VBox studyList = new VBox(10);
    private final Label detailTopicLabel = new Label("Select a plan item");
    private final Label detailReasonLabel = new Label("Why this was scheduled will appear here.");
    private final Label detailRecallLabel = new Label();
    private final Label detailScoreLabel = new Label();
    private final ListView<String> pomodoroList = new ListView<>();

    private HBox selectedCard;

    public PlannerPane(ApplicationContext context, Runnable afterMutation, Runnable openSession) {
        this.context = context;
        this.afterMutation = afterMutation;
        this.openSession = openSession;

        getStyleClass().add("screen-scroller");
        setFitToWidth(true);
        setPadding(new Insets(0));

        summaryLabel.getStyleClass().add("support-label");
        summaryLabel.setWrapText(true);
        statusLabel.getStyleClass().add("inline-banner");
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        detailReasonLabel.setWrapText(true);
        pomodoroList.setPrefHeight(200);

        container.getStyleClass().add("screen-root");
        container.setPadding(new Insets(8, 8, 24, 8));
        setContent(container);
    }

    @Override
    public void refresh() {
        container.getChildren().clear();
        container.getChildren().add(buildHeader());

        if (context.isWorkspaceEmpty()) {
            container.getChildren().add(new EmptyStatePane(
                "Nothing to schedule yet",
                "Build the syllabus first, then return here to generate a day that balances study depth and overdue reviews."
            ));
            return;
        }

        DailyPlan plan = context.getSchedulerService().getPlan(resolvePlanDate()).orElse(null);
        container.getChildren().addAll(
            buildSummaryRow(plan),
            buildWorkbench(plan)
        );
    }

    private VBox buildHeader() {
        Label title = new Label("Planner");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label("Generate a day, inspect the adaptive reasoning, and move straight into execution.");
        subtitle.getStyleClass().add("screen-subtitle");
        subtitle.setWrapText(true);

        VBox header = new VBox(6, title, subtitle);
        header.getStyleClass().add("screen-header");
        return header;
    }

    private HBox buildSummaryRow(DailyPlan plan) {
        totalTimeLabel.setText(plan == null ? "0 min" : plan.getTotalPlannedMinutes() + " min");
        int pomodoros = plan == null ? 0 : plan.getItems().stream().mapToInt(PlanItem::getPomodoroCount).sum();
        totalPomodorosLabel.setText(String.valueOf(pomodoros));

        return new HBox(
            14,
            UiFactory.createSummaryCard("Planned Time", totalTimeLabel, "Total minutes allocated for the selected day."),
            UiFactory.createSummaryCard("Focus Blocks", totalPomodorosLabel, "Pomodoro blocks derived from the plan.")
        );
    }

    private HBox buildWorkbench(DailyPlan plan) {
        VBox builderCard = UiFactory.createCard(
            "Plan Builder",
            buildControls(),
            statusLabel,
            summaryLabel,
            createSection("Review First", reviewList),
            createSection("Study Next", studyList)
        );
        builderCard.getStyleClass().add("content-card");
        HBox.setHgrow(builderCard, Priority.ALWAYS);

        Button sessionButton = new Button("Open Session View");
        sessionButton.getStyleClass().add("secondary-button");
        sessionButton.setOnAction(event -> openSession.run());

        VBox detailCard = UiFactory.createCard(
            "Selection Detail",
            detailTopicLabel,
            detailReasonLabel,
            detailRecallLabel,
            detailScoreLabel,
            pomodoroList,
            sessionButton
        );
        detailCard.getStyleClass().add("content-card");
        detailCard.setPrefWidth(360);

        rebuildLists(plan);
        return new HBox(14, builderCard, detailCard);
    }

    private GridPane buildControls() {
        Button generateButton = new Button("Generate Plan");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setOnAction(event -> generatePlan());

        GridPane controls = new GridPane();
        controls.setHgap(12);
        controls.setVgap(12);
        controls.add(new Label("Plan Date"), 0, 0);
        controls.add(planDatePicker, 1, 0);
        controls.add(new Label("Available Minutes"), 2, 0);
        controls.add(availableMinutesSpinner, 3, 0);
        controls.add(new Label("Focus"), 4, 0);
        controls.add(focusMinutesSpinner, 5, 0);
        controls.add(new Label("Short Break"), 6, 0);
        controls.add(shortBreakSpinner, 7, 0);
        controls.add(generateButton, 8, 0);
        return controls;
    }

    private VBox createSection(String title, VBox content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        return new VBox(10, titleLabel, content);
    }

    private void rebuildLists(DailyPlan plan) {
        reviewList.getChildren().clear();
        studyList.getChildren().clear();
        selectedCard = null;
        summaryLabel.setText(plan == null ? "No stored plan for the selected date yet." : plan.getSummary());

        if (plan == null || plan.getItems().isEmpty()) {
            EmptyStatePane emptyState = new EmptyStatePane(
                "No plan generated",
                "Adjust the time budget and generate a plan to see review-first and study-next recommendations."
            );
            reviewList.getChildren().add(emptyState);
            studyList.getChildren().add(new Label("Study blocks will appear after plan generation."));
            updateDetail(null);
            return;
        }

        List<PlanItem> reviewItems = plan.getItems().stream()
            .filter(item -> item.getItemType() == PlanItemType.REVIEW)
            .toList();
        List<PlanItem> studyItems = plan.getItems().stream()
            .filter(item -> item.getItemType() != PlanItemType.REVIEW)
            .toList();

        if (reviewItems.isEmpty()) {
            reviewList.getChildren().add(new Label("No review blocks are urgent today."));
        } else {
            reviewItems.forEach(item -> reviewList.getChildren().add(createPlanCard(item)));
        }

        if (studyItems.isEmpty()) {
            studyList.getChildren().add(new Label("No study-only blocks were selected for this day."));
        } else {
            studyItems.forEach(item -> studyList.getChildren().add(createPlanCard(item)));
        }

        updateDetail(plan.getItems().get(0));
        highlightFirstCard();
    }

    private HBox createPlanCard(PlanItem item) {
        Label titleLabel = new Label("#" + item.getRecommendedOrder() + " " + item.getTopicName());
        titleLabel.getStyleClass().add("row-title");

        Label metaLabel = new Label(item.getPlannedMinutes() + " min • "
            + item.getPomodoroCount() + " blocks • "
            + FormatUtils.percentage(item.getRecallProbability()) + " recall");
        metaLabel.getStyleClass().add("row-copy");

        Label chip = UiFactory.createChip(item.getItemType().name(), item.getItemType() == PlanItemType.REVIEW
            ? "chip-warning"
            : "chip-success");

        VBox copy = new VBox(4, titleLabel, metaLabel);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Label reasonLabel = new Label(item.getReason());
        reasonLabel.getStyleClass().add("support-label");
        reasonLabel.setWrapText(true);

        HBox header = new HBox(12, copy, chip);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox card = new HBox(new VBox(8, header, reasonLabel));
        card.getStyleClass().add("interactive-card");
        card.setOnMouseClicked(event -> {
            setSelectedCard(card);
            updateDetail(item);
        });
        return card;
    }

    private void highlightFirstCard() {
        HBox firstCard = reviewList.getChildren().stream()
            .filter(HBox.class::isInstance)
            .map(HBox.class::cast)
            .findFirst()
            .orElseGet(() -> studyList.getChildren().stream()
                .filter(HBox.class::isInstance)
                .map(HBox.class::cast)
                .findFirst()
                .orElse(null));

        if (firstCard != null) {
            setSelectedCard(firstCard);
        }
    }

    private void setSelectedCard(HBox card) {
        if (selectedCard != null) {
            selectedCard.getStyleClass().remove("interactive-card-selected");
        }
        selectedCard = card;
        if (!selectedCard.getStyleClass().contains("interactive-card-selected")) {
            selectedCard.getStyleClass().add("interactive-card-selected");
        }
    }

    private void generatePlan() {
        try {
            context.getSchedulerService().generateDailyPlan(new PlanGenerationRequest(
                resolvePlanDate(),
                availableMinutesSpinner.getValue(),
                focusMinutesSpinner.getValue(),
                shortBreakSpinner.getValue()
            ));
            showStatus("Plan regenerated for " + resolvePlanDate() + ".");
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
            detailReasonLabel.setText("Why this was scheduled will appear here.");
            detailRecallLabel.setText("Recall: n/a");
            detailScoreLabel.setText("Score: n/a");
            pomodoroList.getItems().clear();
            return;
        }

        detailTopicLabel.setText(item.getSubjectName() + " / " + item.getTopicName());
        detailReasonLabel.setText(item.getReason());
        detailRecallLabel.setText("Recall probability: " + FormatUtils.percentage(item.getRecallProbability()));
        detailScoreLabel.setText("Weighted score: " + FormatUtils.score(item.getScore()));
        pomodoroList.getItems().setAll(item.getPomodoroBlocks().stream().map(this::formatPomodoro).toList());
    }

    private void showStatus(String message) {
        statusLabel.setText(message);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        PauseTransition pause = new PauseTransition(Duration.seconds(2.5));
        pause.setOnFinished(event -> {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        });
        pause.playFromStart();
    }

    private String formatPomodoro(PomodoroBlock block) {
        return "Block " + block.getBlockIndex() + ": "
            + block.getFocusMinutes() + "m focus / "
            + block.getBreakMinutes() + "m break";
    }

    private LocalDate resolvePlanDate() {
        return planDatePicker.getValue() != null ? planDatePicker.getValue() : LocalDate.now();
    }
}
