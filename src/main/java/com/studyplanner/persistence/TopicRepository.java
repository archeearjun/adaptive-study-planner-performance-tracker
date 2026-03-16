package com.studyplanner.persistence;

import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.Topic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TopicRepository {
    private final DatabaseManager databaseManager;

    public TopicRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<Topic> findAll() {
        String sql = "SELECT * FROM topics ORDER BY archived, next_review_date, priority DESC, name";
        List<Topic> topics = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                topics.add(mapTopic(resultSet));
            }
            return topics;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load topics", exception);
        }
    }

    public List<TopicOverview> findAllOverviews() {
        String sql = """
            SELECT t.id AS topic_id, t.subject_id, s.name AS subject_name, t.name AS topic_name,
                   t.priority, t.difficulty, t.estimated_study_minutes, t.target_exam_date,
                   t.confidence_level, t.last_studied_date, t.next_review_date, t.archived
            FROM topics t
            JOIN subjects s ON s.id = t.subject_id
            ORDER BY t.archived, s.name, t.name
            """;
        List<TopicOverview> topics = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                topics.add(new TopicOverview(
                    resultSet.getLong("topic_id"),
                    resultSet.getLong("subject_id"),
                    resultSet.getString("subject_name"),
                    resultSet.getString("topic_name"),
                    resultSet.getInt("priority"),
                    resultSet.getInt("difficulty"),
                    resultSet.getInt("estimated_study_minutes"),
                    JdbcUtils.getLocalDate(resultSet, "target_exam_date"),
                    resultSet.getDouble("confidence_level"),
                    JdbcUtils.getLocalDate(resultSet, "last_studied_date"),
                    JdbcUtils.getLocalDate(resultSet, "next_review_date"),
                    resultSet.getInt("archived") == 1
                ));
            }
            return topics;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load topic overviews", exception);
        }
    }

    public Optional<Topic> findById(long id) {
        String sql = "SELECT * FROM topics WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapTopic(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load topic " + id, exception);
        }
    }

    public List<Topic> findBySubjectId(long subjectId) {
        String sql = "SELECT * FROM topics WHERE subject_id = ? ORDER BY archived, name";
        List<Topic> topics = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, subjectId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    topics.add(mapTopic(resultSet));
                }
            }
            return topics;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load topics for subject " + subjectId, exception);
        }
    }

    public Topic save(Topic topic) {
        if (topic.getId() == 0) {
            return insert(topic);
        }
        return update(topic);
    }

    public void delete(long id) {
        String sql = "DELETE FROM topics WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to delete topic " + id, exception);
        }
    }

    private Topic insert(Topic topic) {
        String sql = """
            INSERT INTO topics(
                subject_id, name, notes, priority, difficulty, estimated_study_minutes,
                target_exam_date, confidence_level, last_studied_date, easiness_factor,
                repetition_count, interval_days, next_review_date, archived
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindTopic(statement, topic);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    topic.setId(keys.getLong(1));
                }
            }
            return topic;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to insert topic", exception);
        }
    }

    private Topic update(Topic topic) {
        String sql = """
            UPDATE topics
            SET subject_id = ?, name = ?, notes = ?, priority = ?, difficulty = ?, estimated_study_minutes = ?,
                target_exam_date = ?, confidence_level = ?, last_studied_date = ?, easiness_factor = ?,
                repetition_count = ?, interval_days = ?, next_review_date = ?, archived = ?
            WHERE id = ?
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTopic(statement, topic);
            statement.setLong(15, topic.getId());
            statement.executeUpdate();
            return topic;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update topic " + topic.getId(), exception);
        }
    }

    private void bindTopic(PreparedStatement statement, Topic topic) throws SQLException {
        statement.setLong(1, topic.getSubjectId());
        statement.setString(2, topic.getName());
        statement.setString(3, topic.getNotes());
        statement.setInt(4, topic.getPriority());
        statement.setInt(5, topic.getDifficulty());
        statement.setInt(6, topic.getEstimatedStudyMinutes());
        JdbcUtils.setLocalDate(statement, 7, topic.getTargetExamDate());
        statement.setDouble(8, topic.getConfidenceLevel());
        JdbcUtils.setLocalDate(statement, 9, topic.getLastStudiedDate());
        statement.setDouble(10, topic.getEasinessFactor());
        statement.setInt(11, topic.getRepetitionCount());
        statement.setInt(12, topic.getIntervalDays());
        JdbcUtils.setLocalDate(statement, 13, topic.getNextReviewDate());
        statement.setInt(14, topic.isArchived() ? 1 : 0);
    }

    private Topic mapTopic(ResultSet resultSet) throws SQLException {
        return new Topic(
            resultSet.getLong("id"),
            resultSet.getLong("subject_id"),
            resultSet.getString("name"),
            resultSet.getString("notes"),
            resultSet.getInt("priority"),
            resultSet.getInt("difficulty"),
            resultSet.getInt("estimated_study_minutes"),
            JdbcUtils.getLocalDate(resultSet, "target_exam_date"),
            resultSet.getDouble("confidence_level"),
            JdbcUtils.getLocalDate(resultSet, "last_studied_date"),
            resultSet.getDouble("easiness_factor"),
            resultSet.getInt("repetition_count"),
            resultSet.getInt("interval_days"),
            JdbcUtils.getLocalDate(resultSet, "next_review_date"),
            resultSet.getInt("archived") == 1
        );
    }
}
