package com.studyplanner.persistence;

import com.studyplanner.model.ReviewRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReviewRecordRepository {
    private final DatabaseManager databaseManager;

    public ReviewRecordRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ReviewRecord save(ReviewRecord record) {
        if (record.getId() == 0) {
            return insert(record);
        }
        return update(record);
    }

    public List<ReviewRecord> findAll() {
        String sql = "SELECT * FROM review_records ORDER BY review_date DESC";
        List<ReviewRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                records.add(mapRecord(resultSet));
            }
            return records;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load review records", exception);
        }
    }

    public List<ReviewRecord> findByTopicId(long topicId) {
        String sql = "SELECT * FROM review_records WHERE topic_id = ? ORDER BY review_date DESC";
        List<ReviewRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, topicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(mapRecord(resultSet));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load review records for topic " + topicId, exception);
        }
    }

    public Optional<ReviewRecord> findLatestByTopicId(long topicId) {
        String sql = "SELECT * FROM review_records WHERE topic_id = ? ORDER BY review_date DESC LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, topicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRecord(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load latest review for topic " + topicId, exception);
        }
    }

    private ReviewRecord insert(ReviewRecord record) {
        String sql = """
            INSERT INTO review_records(
                topic_id, review_date, quality, easiness_factor_before, easiness_factor_after,
                interval_before, interval_after, next_review_date
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindRecord(statement, record);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    record.setId(keys.getLong(1));
                }
            }
            return record;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to insert review record", exception);
        }
    }

    private ReviewRecord update(ReviewRecord record) {
        String sql = """
            UPDATE review_records
            SET topic_id = ?, review_date = ?, quality = ?, easiness_factor_before = ?, easiness_factor_after = ?,
                interval_before = ?, interval_after = ?, next_review_date = ?
            WHERE id = ?
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRecord(statement, record);
            statement.setLong(9, record.getId());
            statement.executeUpdate();
            return record;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update review record " + record.getId(), exception);
        }
    }

    private void bindRecord(PreparedStatement statement, ReviewRecord record) throws SQLException {
        statement.setLong(1, record.getTopicId());
        JdbcUtils.setLocalDate(statement, 2, record.getReviewDate());
        statement.setInt(3, record.getQuality());
        statement.setDouble(4, record.getEasinessFactorBefore());
        statement.setDouble(5, record.getEasinessFactorAfter());
        statement.setInt(6, record.getIntervalBefore());
        statement.setInt(7, record.getIntervalAfter());
        JdbcUtils.setLocalDate(statement, 8, record.getNextReviewDate());
    }

    private ReviewRecord mapRecord(ResultSet resultSet) throws SQLException {
        ReviewRecord record = new ReviewRecord();
        record.setId(resultSet.getLong("id"));
        record.setTopicId(resultSet.getLong("topic_id"));
        record.setReviewDate(JdbcUtils.getLocalDate(resultSet, "review_date"));
        record.setQuality(resultSet.getInt("quality"));
        record.setEasinessFactorBefore(resultSet.getDouble("easiness_factor_before"));
        record.setEasinessFactorAfter(resultSet.getDouble("easiness_factor_after"));
        record.setIntervalBefore(resultSet.getInt("interval_before"));
        record.setIntervalAfter(resultSet.getInt("interval_after"));
        record.setNextReviewDate(JdbcUtils.getLocalDate(resultSet, "next_review_date"));
        return record;
    }
}
