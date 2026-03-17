package com.studyplanner.ui;

import com.studyplanner.service.ApplicationContext;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;

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

        if (Boolean.getBoolean("studyplanner.exportScreenshots")) {
            Path outputDir = Paths.get(System.getProperty(
                "studyplanner.screenshots.dir",
                "docs/screenshots"
            ));
            ScreenshotExporter.export(stage, root, outputDir);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
