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
        if (example.getSourceSessionId() != null) {
            return upsertBySourceSession(example);
        }
        String sql = """
            INSERT INTO retention_training_data(
                topic_id, source_session_id, captured_on, days_since_last_revision, difficulty, previous_review_quality,
                confidence_score, completion_consistency, repetitions, average_session_quality, label
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    public RetentionTrainingExample save(Connection connection, RetentionTrainingExample example) {
        try {
            if (example.getSourceSessionId() != null) {
                return upsertBySourceSession(connection, example);
            }
            String sql = """
                INSERT INTO retention_training_data(
                    topic_id, source_session_id, captured_on, days_since_last_revision, difficulty, previous_review_quality,
                    confidence_score, completion_consistency, repetitions, average_session_quality, label
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindExample(statement, example);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        example.setId(keys.getLong(1));
                    }
                }
                return example;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save retention training example", exception);
        }
    }

    public boolean existsForSourceSessionId(long sourceSessionId) {
        String sql = "SELECT 1 FROM retention_training_data WHERE source_session_id = ? LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceSessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to look up training example for source session " + sourceSessionId, exception);
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
                long sourceSessionId = resultSet.getLong("source_session_id");
                example.setSourceSessionId(resultSet.wasNull() ? null : sourceSessionId);
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
        JdbcUtils.setNullableLong(statement, 2, example.getSourceSessionId());
        JdbcUtils.setLocalDate(statement, 3, example.getCapturedOn());
        statement.setDouble(4, example.getDaysSinceLastRevision());
        statement.setDouble(5, example.getDifficulty());
        statement.setDouble(6, example.getPreviousReviewQuality());
        statement.setDouble(7, example.getConfidenceScore());
        statement.setDouble(8, example.getCompletionConsistency());
        statement.setDouble(9, example.getRepetitions());
        statement.setDouble(10, example.getAverageSessionQuality());
        statement.setInt(11, example.getLabel());
    }

    private RetentionTrainingExample upsertBySourceSession(RetentionTrainingExample example) {
        String sql = """
            INSERT INTO retention_training_data(
                topic_id, source_session_id, captured_on, days_since_last_revision, difficulty, previous_review_quality,
                confidence_score, completion_consistency, repetitions, average_session_quality, label
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_session_id) DO UPDATE SET
                topic_id = excluded.topic_id,
                captured_on = excluded.captured_on,
                days_since_last_revision = excluded.days_since_last_revision,
                difficulty = excluded.difficulty,
                previous_review_quality = excluded.previous_review_quality,
                confidence_score = excluded.confidence_score,
                completion_consistency = excluded.completion_consistency,
                repetitions = excluded.repetitions,
                average_session_quality = excluded.average_session_quality,
                label = excluded.label
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindExample(statement, example);
            statement.executeUpdate();
            try (PreparedStatement lookup = connection.prepareStatement(
                "SELECT id FROM retention_training_data WHERE source_session_id = ?")) {
                lookup.setLong(1, example.getSourceSessionId());
                try (ResultSet resultSet = lookup.executeQuery()) {
                    if (resultSet.next()) {
                        example.setId(resultSet.getLong(1));
                    }
                }
            }
            return example;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to upsert retention training example", exception);
        }
    }

    private RetentionTrainingExample upsertBySourceSession(Connection connection, RetentionTrainingExample example)
        throws SQLException {
        String sql = """
            INSERT INTO retention_training_data(
                topic_id, source_session_id, captured_on, days_since_last_revision, difficulty, previous_review_quality,
                confidence_score, completion_consistency, repetitions, average_session_quality, label
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(source_session_id) DO UPDATE SET
                topic_id = excluded.topic_id,
                captured_on = excluded.captured_on,
                days_since_last_revision = excluded.days_since_last_revision,
                difficulty = excluded.difficulty,
                previous_review_quality = excluded.previous_review_quality,
                confidence_score = excluded.confidence_score,
                completion_consistency = excluded.completion_consistency,
                repetitions = excluded.repetitions,
                average_session_quality = excluded.average_session_quality,
                label = excluded.label
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindExample(statement, example);
            statement.executeUpdate();
        }
        try (PreparedStatement lookup = connection.prepareStatement(
            "SELECT id FROM retention_training_data WHERE source_session_id = ?")) {
            lookup.setLong(1, example.getSourceSessionId());
            try (ResultSet resultSet = lookup.executeQuery()) {
                if (resultSet.next()) {
                    example.setId(resultSet.getLong(1));
                }
            }
        }
        return example;
    }
}
