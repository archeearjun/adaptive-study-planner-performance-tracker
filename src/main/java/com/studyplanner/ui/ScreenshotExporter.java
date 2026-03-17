package com.studyplanner.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScreenshotExporter {
    private ScreenshotExporter() {
    }

    public static void export(Stage stage, MainView mainView, Path outputDir) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create screenshot directory: " + outputDir, exception);
        }

        Map<String, String> shots = new LinkedHashMap<>();
        shots.put("Dashboard", "dashboard.png");
        shots.put("Subjects/Topics", "management.png");
        shots.put("Daily Planner", "planner.png");
        shots.put("Session Logger", "session-logger.png");
        shots.put("Analytics", "analytics.png");
        shots.put("Topic Details", "topic-details.png");

        exportNext(stage, mainView, stage.getScene(), outputDir, shots.entrySet().stream().toList(), 0);
    }

    private static void exportNext(Stage stage, MainView mainView, Scene scene, Path outputDir,
                                   java.util.List<Map.Entry<String, String>> shots, int index) {
        if (index >= shots.size()) {
            Platform.exit();
            return;
        }

        Map.Entry<String, String> shot = shots.get(index);
        mainView.selectTab(shot.getKey());
        mainView.refreshAll();

        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(event -> {
            WritableImage image = scene.snapshot(null);
            Path file = outputDir.resolve(shot.getValue());
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to export screenshot: " + file, exception);
            }
            exportNext(stage, mainView, scene, outputDir, shots, index + 1);
        });
        pause.play();
    }
}
