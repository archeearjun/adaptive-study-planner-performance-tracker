package com.studyplanner.ml;

import java.util.Arrays;
import java.util.List;

public class LogisticRegressionModel {
    private final int featureCount;
    private double[] weights;
    private double bias;
    private boolean trained;
    private double trainingAccuracy;

    public LogisticRegressionModel(int featureCount) {
        this.featureCount = featureCount;
        this.weights = new double[featureCount];
    }

    public void setParameters(double[] weights, double bias) {
        if (weights.length != featureCount) {
            throw new IllegalArgumentException("Unexpected weight vector length");
        }
        this.weights = Arrays.copyOf(weights, weights.length);
        this.bias = bias;
        this.trained = false;
        this.trainingAccuracy = 0.0;
    }

    public void train(List<double[]> features, List<Integer> labels, int epochs, double learningRate, double l2Penalty) {
        if (features.isEmpty() || features.size() != labels.size()) {
            return;
        }

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradients = new double[featureCount];
            double biasGradient = 0.0;

            for (int row = 0; row < features.size(); row++) {
                double[] x = features.get(row);
                double prediction = predictProbability(x);
                double error = prediction - labels.get(row);

                for (int column = 0; column < featureCount; column++) {
                    gradients[column] += error * x[column];
                }
                biasGradient += error;
            }

            for (int column = 0; column < featureCount; column++) {
                double regularizedGradient = (gradients[column] / features.size()) + l2Penalty * weights[column];
                weights[column] -= learningRate * regularizedGradient;
            }
            bias -= learningRate * (biasGradient / features.size());
        }

        trained = true;
        trainingAccuracy = accuracy(features, labels);
    }

    public double predictProbability(double[] features) {
        if (features.length != featureCount) {
            throw new IllegalArgumentException("Unexpected feature vector length");
        }

        double linear = bias;
        for (int index = 0; index < featureCount; index++) {
            linear += weights[index] * features[index];
        }
        return sigmoid(linear);
    }

    public boolean isTrained() {
        return trained;
    }

    public double getTrainingAccuracy() {
        return trainingAccuracy;
    }

    public double[] getWeights() {
        return Arrays.copyOf(weights, weights.length);
    }

    public double getBias() {
        return bias;
    }

    private double accuracy(List<double[]> features, List<Integer> labels) {
        int correct = 0;
        for (int index = 0; index < features.size(); index++) {
            int prediction = predictProbability(features.get(index)) >= 0.5 ? 1 : 0;
            if (prediction == labels.get(index)) {
                correct++;
            }
        }
        return features.isEmpty() ? 0.0 : (double) correct / features.size();
    }

    private double sigmoid(double value) {
        if (value >= 0) {
            double exp = Math.exp(-value);
            return 1.0 / (1.0 + exp);
        }
        double exp = Math.exp(value);
        return exp / (1.0 + exp);
    }
}
