package com.studyplanner.persistence;

import com.studyplanner.model.RetentionTrainingExample;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class RetentionTrainingDataRepository {
    private final DatabaseManager databaseManager;

    public RetentionTrainingDataRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM retention_training_data";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to count retention training rows", exception);
        }
    }

    public RetentionTrainingExample save(RetentionTrainingExample example) {
        String sql = """
            INSERT INTO retention_training_data(
                topic_id, captured_on, days_since_last_revision, difficulty, previous_review_quality,
                confidence_score, completion_consistency, repetitions, average_session_quality, label
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindExample(statement, example);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    example.setId(keys.getLong(1));
                }
            }
            return example;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save retention training example", exception);
        }
    }

    public List<RetentionTrainingExample> findAll() {
        String sql = "SELECT * FROM retention_training_data ORDER BY captured_on";
        List<RetentionTrainingExample> examples = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                RetentionTrainingExample example = new RetentionTrainingExample();
                example.setId(resultSet.getLong("id"));
                example.setTopicId(resultSet.getLong("topic_id"));
                example.setCapturedOn(JdbcUtils.getLocalDate(resultSet, "captured_on"));
                example.setDaysSinceLastRevision(resultSet.getDouble("days_since_last_revision"));
                example.setDifficulty(resultSet.getDouble("difficulty"));
                example.setPreviousReviewQuality(resultSet.getDouble("previous_review_quality"));
                example.setConfidenceScore(resultSet.getDouble("confidence_score"));
                example.setCompletionConsistency(resultSet.getDouble("completion_consistency"));
                example.setRepetitions(resultSet.getDouble("repetitions"));
                example.setAverageSessionQuality(resultSet.getDouble("average_session_quality"));
                example.setLabel(resultSet.getInt("label"));
                examples.add(example);
            }
            return examples;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load retention training data", exception);
        }
    }

    private void bindExample(PreparedStatement statement, RetentionTrainingExample example) throws SQLException {
        statement.setLong(1, example.getTopicId());
        JdbcUtils.setLocalDate(statement, 2, example.getCapturedOn());
        statement.setDouble(3, example.getDaysSinceLastRevision());
        statement.setDouble(4, example.getDifficulty());
        statement.setDouble(5, example.getPreviousReviewQuality());
        statement.setDouble(6, example.getConfidenceScore());
        statement.setDouble(7, example.getCompletionConsistency());
        statement.setDouble(8, example.getRepetitions());
        statement.setDouble(9, example.getAverageSessionQuality());
        statement.setInt(10, example.getLabel());
    }
}
