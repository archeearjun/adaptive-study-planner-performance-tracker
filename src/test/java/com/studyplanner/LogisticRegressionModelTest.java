package com.studyplanner;

import com.studyplanner.ml.LogisticRegressionModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogisticRegressionModelTest {
    @Test
    void learnsSimpleBinaryBoundary() {
        LogisticRegressionModel model = new LogisticRegressionModel(2);
        model.setParameters(new double[] {0.0, 0.0}, 0.0);

        List<double[]> features = List.of(
            new double[] {0.0, 0.0},
            new double[] {0.0, 1.0},
            new double[] {1.0, 0.0},
            new double[] {1.0, 1.0}
        );
        List<Integer> labels = List.of(0, 0, 0, 1);

        model.train(features, labels, 5000, 0.4, 0.0);

        assertTrue(model.predictProbability(new double[] {0.0, 0.0}) < 0.35);
        assertTrue(model.predictProbability(new double[] {1.0, 1.0}) > 0.65);
    }
}
