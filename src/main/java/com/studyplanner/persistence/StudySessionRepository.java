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
import java.util.Optional;

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

    public StudySession save(Connection connection, StudySession session) {
        try {
            if (session.getId() == 0) {
                return insert(connection, session);
            }
            return update(connection, session);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to save study session", exception);
        }
    }

    public List<StudySession> findAll() {
        String sql = "SELECT * FROM study_sessions ORDER BY session_date DESC, updated_at DESC, started_at DESC";
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

    public Optional<StudySession> findById(long id) {
        String sql = "SELECT * FROM study_sessions WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSession(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load study session " + id, exception);
        }
    }

    public List<StudySession> findByTopicId(long topicId) {
        String sql = "SELECT * FROM study_sessions WHERE topic_id = ? ORDER BY session_date DESC, updated_at DESC, started_at DESC";
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

    public Optional<StudySession> findLatestByPlanItemId(long planItemId) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE plan_item_id = ?
            ORDER BY updated_at DESC, started_at DESC
            LIMIT 1
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, planItemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSession(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load latest session for plan item " + planItemId, exception);
        }
    }

    public Optional<StudySession> findActiveByPlanItemId(long planItemId) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE plan_item_id = ? AND status IN ('STARTED', 'PAUSED')
            ORDER BY updated_at DESC, started_at DESC
            LIMIT 1
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, planItemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSession(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load active session for plan item " + planItemId, exception);
        }
    }

    public Optional<StudySession> findActiveByTopicAndDate(long topicId, LocalDate sessionDate) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE topic_id = ? AND session_date = ? AND status IN ('STARTED', 'PAUSED')
            ORDER BY updated_at DESC, started_at DESC
            LIMIT 1
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, topicId);
            JdbcUtils.setLocalDate(statement, 2, sessionDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSession(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load active session for topic " + topicId, exception);
        }
    }

    public List<StudySession> findOpenSessionsBefore(LocalDate cutoffDate) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE session_date < ? AND status IN ('STARTED', 'PAUSED')
            ORDER BY session_date ASC, updated_at ASC
            """;
        List<StudySession> sessions = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcUtils.setLocalDate(statement, 1, cutoffDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sessions.add(mapSession(resultSet));
                }
            }
            return sessions;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load open sessions before " + cutoffDate, exception);
        }
    }

    public List<StudySession> findByDateRange(LocalDate start, LocalDate end) {
        String sql = """
            SELECT * FROM study_sessions
            WHERE session_date BETWEEN ? AND ?
            ORDER BY session_date ASC, updated_at ASC, started_at ASC
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
                plan_item_id, topic_id, subject_id, session_date, started_at, updated_at, ended_at,
                planned_minutes, actual_minutes, status, focus_quality, confidence_after,
                quiz_score, review_session, notes
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private StudySession insert(Connection connection, StudySession session) throws SQLException {
        String sql = """
            INSERT INTO study_sessions(
                plan_item_id, topic_id, subject_id, session_date, started_at, updated_at, ended_at,
                planned_minutes, actual_minutes, status, focus_quality, confidence_after,
                quiz_score, review_session, notes
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindSession(statement, session);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setId(keys.getLong(1));
                }
            }
            return session;
        }
    }

    private StudySession update(StudySession session) {
        String sql = """
            UPDATE study_sessions
            SET plan_item_id = ?, topic_id = ?, subject_id = ?, session_date = ?, started_at = ?, updated_at = ?, ended_at = ?,
                planned_minutes = ?, actual_minutes = ?, status = ?, focus_quality = ?, confidence_after = ?,
                quiz_score = ?, review_session = ?, notes = ?
            WHERE id = ?
            """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSession(statement, session);
            statement.setLong(16, session.getId());
            statement.executeUpdate();
            return session;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update study session " + session.getId(), exception);
        }
    }

    private StudySession update(Connection connection, StudySession session) throws SQLException {
        String sql = """
            UPDATE study_sessions
            SET plan_item_id = ?, topic_id = ?, subject_id = ?, session_date = ?, started_at = ?, updated_at = ?, ended_at = ?,
                planned_minutes = ?, actual_minutes = ?, status = ?, focus_quality = ?, confidence_after = ?,
                quiz_score = ?, review_session = ?, notes = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSession(statement, session);
            statement.setLong(16, session.getId());
            statement.executeUpdate();
            return session;
        }
    }

    private void bindSession(PreparedStatement statement, StudySession session) throws SQLException {
        if (session.getUpdatedAt() == null) {
            session.setUpdatedAt(session.getEndedAt() != null ? session.getEndedAt() : session.getStartedAt());
        }
        JdbcUtils.setNullableLong(statement, 1, session.getPlanItemId());
        statement.setLong(2, session.getTopicId());
        JdbcUtils.setNullableLong(statement, 3, session.getSubjectId());
        JdbcUtils.setLocalDate(statement, 4, session.getSessionDate());
        JdbcUtils.setLocalDateTime(statement, 5, session.getStartedAt());
        JdbcUtils.setLocalDateTime(statement, 6, session.getUpdatedAt());
        JdbcUtils.setLocalDateTime(statement, 7, session.getEndedAt());
        statement.setInt(8, session.getPlannedMinutes());
        statement.setInt(9, session.getActualMinutes());
        statement.setString(10, session.getStatus().name());
        if (session.getFocusQuality() == null) {
            statement.setObject(11, null);
        } else {
            statement.setInt(11, session.getFocusQuality());
        }
        if (session.getConfidenceAfter() == null) {
            statement.setObject(12, null);
        } else {
            statement.setDouble(12, session.getConfidenceAfter());
        }
        if (session.getQuizScore() == null) {
            statement.setObject(13, null);
        } else {
            statement.setDouble(13, session.getQuizScore());
        }
        statement.setInt(14, session.isReviewSession() ? 1 : 0);
        statement.setString(15, session.getNotes());
    }

    private StudySession mapSession(ResultSet resultSet) throws SQLException {
        StudySession session = new StudySession();
        session.setId(resultSet.getLong("id"));
        long planItemId = resultSet.getLong("plan_item_id");
        session.setPlanItemId(resultSet.wasNull() ? null : planItemId);
        session.setTopicId(resultSet.getLong("topic_id"));
        long subjectId = resultSet.getLong("subject_id");
        session.setSubjectId(resultSet.wasNull() ? null : subjectId);
        session.setSessionDate(JdbcUtils.getLocalDate(resultSet, "session_date"));
        session.setStartedAt(JdbcUtils.getLocalDateTime(resultSet, "started_at"));
        session.setUpdatedAt(JdbcUtils.getLocalDateTime(resultSet, "updated_at"));
        session.setEndedAt(JdbcUtils.getLocalDateTime(resultSet, "ended_at"));
        session.setPlannedMinutes(resultSet.getInt("planned_minutes"));
        session.setActualMinutes(resultSet.getInt("actual_minutes"));
        session.setStatus(SessionStatus.valueOf(resultSet.getString("status")));
        int focusQuality = resultSet.getInt("focus_quality");
        session.setFocusQuality(resultSet.wasNull() ? null : focusQuality);
        double confidenceAfter = resultSet.getDouble("confidence_after");
        session.setConfidenceAfter(resultSet.wasNull() ? null : confidenceAfter);
        double quizScore = resultSet.getDouble("quiz_score");
        session.setQuizScore(resultSet.wasNull() ? null : quizScore);
        session.setReviewSession(resultSet.getInt("review_session") == 1);
        session.setNotes(resultSet.getString("notes"));
        return session;
    }
}
