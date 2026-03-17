package com.studyplanner.ui;

import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
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

public class ManagementPane extends ScrollPane implements RefreshableView {
    private final ApplicationContext context;
    private final Runnable afterMutation;

    private final TableView<Subject> subjectTable = new TableView<>();
    private final TableView<TopicOverview> topicTable = new TableView<>();

    private final TextField subjectNameField = new TextField();
    private final TextArea subjectDescriptionArea = new TextArea();
    private final TextField subjectColorField = new TextField("#2563EB");

    private final ComboBox<Subject> topicSubjectCombo = new ComboBox<>();
    private final TextField topicNameField = new TextField();
    private final TextArea topicNotesArea = new TextArea();
    private final Spinner<Integer> topicPrioritySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 3));
    private final Spinner<Integer> topicDifficultySpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 3));
    private final Spinner<Integer> topicMinutesSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(15, 180, 45, 5));
    private final Spinner<Integer> topicConfidenceSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 60, 1));
    private final DatePicker topicExamDatePicker = new DatePicker();
    private final DatePicker topicLastStudiedPicker = new DatePicker();
    private final DatePicker topicNextReviewPicker = new DatePicker();
    private final CheckBox topicArchivedBox = new CheckBox("Archived");

    public ManagementPane(ApplicationContext context, Runnable afterMutation) {
        this.context = context;
        this.afterMutation = afterMutation;
        getStyleClass().add("screen-scroller");
        setFitToWidth(true);
        setPadding(new Insets(4));
        setContent(buildContent());
        configureSelectionBindings();
    }

    @Override
    public void refresh() {
        long selectedSubjectId = subjectTable.getSelectionModel().getSelectedItem() == null
            ? -1
            : subjectTable.getSelectionModel().getSelectedItem().getId();
        long selectedTopicId = topicTable.getSelectionModel().getSelectedItem() == null
            ? -1
            : topicTable.getSelectionModel().getSelectedItem().topicId();

        subjectTable.setItems(FXCollections.observableArrayList(context.getSubjectService().getAllSubjects()));
        topicSubjectCombo.setItems(FXCollections.observableArrayList(context.getSubjectService().getAllSubjects()));
        topicTable.setItems(FXCollections.observableArrayList(context.getTopicService().getTopicOverviews()));

        if (!subjectTable.getItems().isEmpty()) {
            Subject subject = subjectTable.getItems().stream()
                .filter(item -> item.getId() == selectedSubjectId)
                .findFirst()
                .orElse(subjectTable.getItems().get(0));
            subjectTable.getSelectionModel().select(subject);
        }

        if (!topicTable.getItems().isEmpty()) {
            TopicOverview topic = topicTable.getItems().stream()
                .filter(item -> item.topicId() == selectedTopicId)
                .findFirst()
                .orElse(topicTable.getItems().get(0));
            topicTable.getSelectionModel().select(topic);
        }
    }

    private SplitPane buildContent() {
        configureSubjectTable();
        configureTopicTable();
        configureSubjectCombo();

        VBox subjectPane = new VBox(14, UiFactory.createCard("Subjects", subjectTable), buildSubjectForm());
        VBox topicPane = new VBox(14, UiFactory.createCard("Topics", topicTable), buildTopicForm());
        VBox.setVgrow(subjectPane, Priority.ALWAYS);
        VBox.setVgrow(topicPane, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(subjectPane, topicPane);
        splitPane.setDividerPositions(0.36);
        splitPane.setPadding(new Insets(8));
        return splitPane;
    }

    private VBox buildSubjectForm() {
        subjectDescriptionArea.setPrefRowCount(4);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name"), 0, 0);
        grid.add(subjectNameField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(subjectDescriptionArea, 1, 1);
        grid.add(new Label("Accent Color"), 0, 2);
        grid.add(subjectColorField, 1, 2);
        GridPane.setHgrow(subjectNameField, Priority.ALWAYS);
        GridPane.setHgrow(subjectDescriptionArea, Priority.ALWAYS);
        GridPane.setHgrow(subjectColorField, Priority.ALWAYS);

        Button saveButton = new Button("Save Subject");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> saveSubject());

        Button newButton = new Button("New");
        newButton.setOnAction(event -> clearSubjectForm());

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> deleteSubject());

        HBox actions = new HBox(10, saveButton, newButton, deleteButton);
        return UiFactory.createCard("Subject Editor", grid, actions);
    }

    private VBox buildTopicForm() {
        topicNotesArea.setPrefRowCount(5);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Subject"), 0, 0);
        grid.add(topicSubjectCombo, 1, 0);
        grid.add(new Label("Topic Name"), 0, 1);
        grid.add(topicNameField, 1, 1);
        grid.add(new Label("Notes"), 0, 2);
        grid.add(topicNotesArea, 1, 2);
        grid.add(new Label("Priority"), 0, 3);
        grid.add(topicPrioritySpinner, 1, 3);
        grid.add(new Label("Difficulty"), 0, 4);
        grid.add(topicDifficultySpinner, 1, 4);
        grid.add(new Label("Estimated Minutes"), 0, 5);
        grid.add(topicMinutesSpinner, 1, 5);
        grid.add(new Label("Confidence %"), 0, 6);
        grid.add(topicConfidenceSpinner, 1, 6);
        grid.add(new Label("Target Exam"), 0, 7);
        grid.add(topicExamDatePicker, 1, 7);
        grid.add(new Label("Last Studied"), 0, 8);
        grid.add(topicLastStudiedPicker, 1, 8);
        grid.add(new Label("Next Review"), 0, 9);
        grid.add(topicNextReviewPicker, 1, 9);
        grid.add(topicArchivedBox, 1, 10);
        GridPane.setHgrow(topicSubjectCombo, Priority.ALWAYS);
        GridPane.setHgrow(topicNameField, Priority.ALWAYS);
        GridPane.setHgrow(topicNotesArea, Priority.ALWAYS);

        Button saveButton = new Button("Save Topic");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(event -> saveTopic());

        Button newButton = new Button("New");
        newButton.setOnAction(event -> clearTopicForm());

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> deleteTopic());

        HBox actions = new HBox(10, saveButton, newButton, deleteButton);
        return UiFactory.createCard("Topic Editor", grid, actions);
    }

    private void configureSelectionBindings() {
        subjectTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, subject) -> {
            if (subject == null) {
                return;
            }
            subjectNameField.setText(subject.getName());
            subjectDescriptionArea.setText(subject.getDescription());
            subjectColorField.setText(subject.getAccentColor());
        });

        topicTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, overview) -> {
            if (overview == null) {
                return;
            }
            Topic topic = context.getTopicService().getTopic(overview.topicId());
            topicNameField.setText(topic.getName());
            topicNotesArea.setText(topic.getNotes());
            topicPrioritySpinner.getValueFactory().setValue(topic.getPriority());
            topicDifficultySpinner.getValueFactory().setValue(topic.getDifficulty());
            topicMinutesSpinner.getValueFactory().setValue(topic.getEstimatedStudyMinutes());
            topicConfidenceSpinner.getValueFactory().setValue((int) Math.round(topic.getConfidenceLevel()));
            topicExamDatePicker.setValue(topic.getTargetExamDate());
            topicLastStudiedPicker.setValue(topic.getLastStudiedDate());
            topicNextReviewPicker.setValue(topic.getNextReviewDate());
            topicArchivedBox.setSelected(topic.isArchived());
            context.getSubjectService().getAllSubjects().stream()
                .filter(subject -> subject.getId() == topic.getSubjectId())
                .findFirst()
                .ifPresent(topicSubjectCombo::setValue);
        });
    }

    private void configureSubjectTable() {
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        subjectTable.setPlaceholder(new Label("No subjects yet."));
        TableColumn<Subject, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        TableColumn<Subject, String> descriptionColumn = new TableColumn<>("Description");
        descriptionColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDescription()));
        subjectTable.getColumns().addAll(nameColumn, descriptionColumn);
    }

    private void configureTopicTable() {
        topicTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        topicTable.setPlaceholder(new Label("No topics yet."));
        TableColumn<TopicOverview, String> subjectColumn = new TableColumn<>("Subject");
        subjectColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().subjectName()));
        TableColumn<TopicOverview, String> topicColumn = new TableColumn<>("Topic");
        topicColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().topicName()));
        TableColumn<TopicOverview, Number> priorityColumn = new TableColumn<>("Priority");
        priorityColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().priority()));
        TableColumn<TopicOverview, Number> difficultyColumn = new TableColumn<>("Difficulty");
        difficultyColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().difficulty()));
        TableColumn<TopicOverview, Number> confidenceColumn = new TableColumn<>("Confidence");
        confidenceColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(Math.round(data.getValue().confidenceLevel())));
        TableColumn<TopicOverview, Object> reviewColumn = new TableColumn<>("Next Review");
        reviewColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().nextReviewDate()));
        topicTable.getColumns().addAll(subjectColumn, topicColumn, priorityColumn, difficultyColumn, confidenceColumn, reviewColumn);
    }

    private void configureSubjectCombo() {
        topicSubjectCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Subject subject) {
                return subject == null ? "" : subject.getName();
            }

            @Override
            public Subject fromString(String string) {
                return null;
            }
        });
    }

    private void saveSubject() {
        try {
            Subject selected = subjectTable.getSelectionModel().getSelectedItem();
            Subject subject = selected == null ? new Subject() : selected;
            subject.setName(subjectNameField.getText().trim());
            subject.setDescription(subjectDescriptionArea.getText().trim());
            subject.setAccentColor(subjectColorField.getText().trim());
            context.getSubjectService().saveSubject(subject);
            clearSubjectForm();
            afterMutation.run();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void deleteSubject() {
        Subject selected = subjectTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        context.getSubjectService().deleteSubject(selected.getId());
        clearSubjectForm();
        afterMutation.run();
    }

    private void saveTopic() {
        try {
            Subject subject = topicSubjectCombo.getValue();
            if (subject == null) {
                throw new IllegalArgumentException("Select a subject before saving a topic.");
            }

            TopicOverview selected = topicTable.getSelectionModel().getSelectedItem();
            Topic topic = selected == null ? new Topic() : context.getTopicService().getTopic(selected.topicId());
            topic.setSubjectId(subject.getId());
            topic.setName(topicNameField.getText().trim());
            topic.setNotes(topicNotesArea.getText().trim());
            topic.setPriority(topicPrioritySpinner.getValue());
            topic.setDifficulty(topicDifficultySpinner.getValue());
            topic.setEstimatedStudyMinutes(topicMinutesSpinner.getValue());
            topic.setConfidenceLevel(topicConfidenceSpinner.getValue());
            topic.setTargetExamDate(topicExamDatePicker.getValue());
            topic.setLastStudiedDate(topicLastStudiedPicker.getValue());
            topic.setNextReviewDate(topicNextReviewPicker.getValue());
            topic.setArchived(topicArchivedBox.isSelected());
            context.getTopicService().saveTopic(topic);
            clearTopicForm();
            afterMutation.run();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void deleteTopic() {
        TopicOverview selected = topicTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        context.getTopicService().deleteTopic(selected.topicId());
        clearTopicForm();
        afterMutation.run();
    }

    private void clearSubjectForm() {
        subjectTable.getSelectionModel().clearSelection();
        subjectNameField.clear();
        subjectDescriptionArea.clear();
        subjectColorField.setText("#2563EB");
    }

    private void clearTopicForm() {
        topicTable.getSelectionModel().clearSelection();
        topicSubjectCombo.setValue(null);
        topicNameField.clear();
        topicNotesArea.clear();
        topicPrioritySpinner.getValueFactory().setValue(3);
        topicDifficultySpinner.getValueFactory().setValue(3);
        topicMinutesSpinner.getValueFactory().setValue(45);
        topicConfidenceSpinner.getValueFactory().setValue(60);
        topicExamDatePicker.setValue(null);
        topicLastStudiedPicker.setValue(null);
        topicNextReviewPicker.setValue(null);
        topicArchivedBox.setSelected(false);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Unable to save changes");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
