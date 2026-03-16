package com.studyplanner.ui;

import com.studyplanner.service.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApplication extends Application {
    @Override
    public void start(Stage stage) {
        ApplicationContext context = new ApplicationContext();
        MainView root = new MainView(context);
        Scene scene = new Scene(root, 1520, 960);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Adaptive Study Planner & Performance Tracker");
        stage.setMinWidth(1280);
        stage.setMinHeight(820);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
