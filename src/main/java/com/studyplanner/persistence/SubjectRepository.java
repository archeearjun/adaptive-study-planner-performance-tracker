package com.studyplanner.persistence;

import com.studyplanner.model.Subject;
import com.studyplanner.utils.DateUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubjectRepository {
    private final DatabaseManager databaseManager;

    public SubjectRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM subjects";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to count subjects", exception);
        }
    }

    public List<Subject> findAll() {
        String sql = "SELECT * FROM subjects ORDER BY name";
        List<Subject> subjects = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                subjects.add(mapSubject(resultSet));
            }
            return subjects;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load subjects", exception);
        }
    }

    public Optional<Subject> findById(long id) {
        String sql = "SELECT * FROM subjects WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSubject(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load subject " + id, exception);
        }
    }

    public Subject save(Subject subject) {
        if (subject.getId() == 0) {
            return insert(subject);
        }
        return update(subject);
    }

    public void delete(long id) {
        String sql = "DELETE FROM subjects WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to delete subject " + id, exception);
        }
    }

    private Subject insert(Subject subject) {
        String sql = "INSERT INTO subjects(name, description, accent_color, created_at) VALUES(?, ?, ?, ?)";
        if (subject.getCreatedAt() == null) {
            subject.setCreatedAt(LocalDateTime.now());
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, subject.getName());
            statement.setString(2, subject.getDescription());
            statement.setString(3, subject.getAccentColor());
            JdbcUtils.setLocalDateTime(statement, 4, subject.getCreatedAt());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    subject.setId(keys.getLong(1));
                }
            }
            return subject;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to insert subject", exception);
        }
    }

    private Subject update(Subject subject) {
        String sql = "UPDATE subjects SET name = ?, description = ?, accent_color = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, subject.getName());
            statement.setString(2, subject.getDescription());
            statement.setString(3, subject.getAccentColor());
            statement.setLong(4, subject.getId());
            statement.executeUpdate();
            return subject;
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update subject " + subject.getId(), exception);
        }
    }

    private Subject mapSubject(ResultSet resultSet) throws SQLException {
        return new Subject(
            resultSet.getLong("id"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            resultSet.getString("accent_color"),
            DateUtils.parseDateTime(resultSet.getString("created_at"))
        );
    }
}
