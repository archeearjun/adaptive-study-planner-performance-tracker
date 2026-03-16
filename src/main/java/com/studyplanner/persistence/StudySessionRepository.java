package com.studyplanner.persistence;

import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StudySessionRepository {
    private final DatabaseManager databaseManager;

    public StudySessionRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public StudySession save(StudySession session) {
        if (session.getId() == 0) {
            return insert(session);
        }
        return update(session);
    }

    public List<StudySession> findAll() {
        String sql = "SELECT * FROM study_sessions ORDER BY session_date DESC, started_at DESC";
        List<StudySession> sessions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                sessions.add(mapSession(resultSet));
            }
            return sessions;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load study sessions", exception);
        }
    }

    public List<StudySession> findByTopicId(long topicId) {
        String sql = "SELECT * FROM study_sessions WHERE topic_id = ? ORDER BY session_date DESC, started_at DESC";
        List<StudySession> sessions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, topicId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(mapSession(resultSet));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load sessions for topic " + topicId, exception);
        }
    }

    public List<StudySession> findByDateRange(LocalDate start, LocalDate end) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE session_date BETWEEN ? AND ?
            ORDER BY session_date ASC, started_at ASC
            """;
        List<StudySession> sessions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcUtils.setLocalDate(statement, 1, start);
            JdbcUtils.setLocalDate(statement, 2, end);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(mapSession(resultSet));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load sessions for date range", exception);
        }
    }

    private StudySession insert(StudySession session) {
        String sql = """
            INSERT INTO study_sessions(
                plan_item_id, topic_id, session_date, started_at, planned_minutes, actual_minutes,
                status, focus_quality, confidence_after, quiz_score, review_session, notes
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSession(statement, session);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setId(keys.getLong(1));
                }
            }
            return session;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to insert study session", exception);
        }
    }

    private StudySession update(StudySession session) {
        String sql = """
            UPDATE study_sessions
            SET plan_item_id = ?, topic_id = ?, session_date = ?, started_at = ?, planned_minutes = ?, actual_minutes = ?,
                status = ?, focus_quality = ?, confidence_after = ?, quiz_score = ?, review_session = ?, notes = ?
            WHERE id = ?
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSession(statement, session);
            statement.setLong(13, session.getId());
            statement.executeUpdate();
            return session;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update study session " + session.getId(), exception);
        }
    }

    private void bindSession(PreparedStatement statement, StudySession session) throws SQLException {
        JdbcUtils.setNullableLong(statement, 1, session.getPlanItemId());
        statement.setLong(2, session.getTopicId());
        JdbcUtils.setLocalDate(statement, 3, session.getSessionDate());
        JdbcUtils.setLocalDateTime(statement, 4, session.getStartedAt());
        statement.setInt(5, session.getPlannedMinutes());
        statement.setInt(6, session.getActualMinutes());
        statement.setString(7, session.getStatus().name());
        statement.setInt(8, session.getFocusQuality());
        statement.setDouble(9, session.getConfidenceAfter());
        if (session.getQuizScore() == null) {
            statement.setObject(10, null);
        } else {
            statement.setDouble(10, session.getQuizScore());
        }
        statement.setInt(11, session.isReviewSession() ? 1 : 0);
        statement.setString(12, session.getNotes());
    }

    private StudySession mapSession(ResultSet resultSet) throws SQLException {
        StudySession session = new StudySession();
        session.setId(resultSet.getLong("id"));
        long planItemId = resultSet.getLong("plan_item_id");
        session.setPlanItemId(resultSet.wasNull() ? null : planItemId);
        session.setTopicId(resultSet.getLong("topic_id"));
        session.setSessionDate(JdbcUtils.getLocalDate(resultSet, "session_date"));
        session.setStartedAt(JdbcUtils.getLocalDateTime(resultSet, "started_at"));
        session.setPlannedMinutes(resultSet.getInt("planned_minutes"));
        session.setActualMinutes(resultSet.getInt("actual_minutes"));
        session.setStatus(SessionStatus.valueOf(resultSet.getString("status")));
        session.setFocusQuality(resultSet.getInt("focus_quality"));
        session.setConfidenceAfter(resultSet.getDouble("confidence_after"));
        double quizScore = resultSet.getDouble("quiz_score");
        session.setQuizScore(resultSet.wasNull() ? null : quizScore);
        session.setReviewSession(resultSet.getInt("review_session") == 1);
        session.setNotes(resultSet.getString("notes"));
        return session;
    }
}
