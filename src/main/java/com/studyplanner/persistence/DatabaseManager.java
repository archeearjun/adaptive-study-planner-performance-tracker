package com.studyplanner.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class DatabaseManager {
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final Path databasePath;
    private final String jdbcUrl;

    public DatabaseManager() {
        this(resolveDefaultDatabasePath());
    }

    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath;
        this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
    }

    public void initialize() {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (IOException exception) {
            throw new PersistenceException("Failed to create data directory", exception);
        }

        List<String> statements = List.of(
            "PRAGMA foreign_keys = ON;",
            """
            CREATE TABLE IF NOT EXISTS subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                description TEXT,
                accent_color TEXT NOT NULL,
                created_at TEXT NOT NULL
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS topics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                notes TEXT,
                priority INTEGER NOT NULL,
                difficulty INTEGER NOT NULL,
                estimated_study_minutes INTEGER NOT NULL,
                target_exam_date TEXT,
                confidence_level REAL NOT NULL DEFAULT 0,
                last_studied_date TEXT,
                easiness_factor REAL NOT NULL DEFAULT 2.5,
                repetition_count INTEGER NOT NULL DEFAULT 0,
                interval_days INTEGER NOT NULL DEFAULT 0,
                next_review_date TEXT,
                archived INTEGER NOT NULL DEFAULT 0,
                UNIQUE(subject_id, name),
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS daily_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_date TEXT NOT NULL UNIQUE,
                available_minutes INTEGER NOT NULL,
                focus_minutes INTEGER NOT NULL DEFAULT 25,
                short_break_minutes INTEGER NOT NULL DEFAULT 5,
                total_planned_minutes INTEGER NOT NULL,
                generated_at TEXT NOT NULL,
                stale INTEGER NOT NULL DEFAULT 0,
                summary TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS plan_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id INTEGER NOT NULL,
                topic_id INTEGER NOT NULL,
                subject_id INTEGER NOT NULL,
                topic_name TEXT NOT NULL,
                subject_name TEXT NOT NULL,
                item_type TEXT NOT NULL,
                recommended_order INTEGER NOT NULL,
                planned_minutes INTEGER NOT NULL,
                score REAL NOT NULL,
                reason TEXT NOT NULL,
                pomodoro_count INTEGER NOT NULL,
                recall_probability REAL NOT NULL,
                status TEXT NOT NULL,
                completed_minutes INTEGER NOT NULL DEFAULT 0,
                quality_rating INTEGER,
                FOREIGN KEY(plan_id) REFERENCES daily_plans(id) ON DELETE CASCADE,
                FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS pomodoro_blocks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_item_id INTEGER NOT NULL,
                block_index INTEGER NOT NULL,
                focus_minutes INTEGER NOT NULL,
                break_minutes INTEGER NOT NULL,
                status TEXT NOT NULL,
                quality_rating INTEGER,
                FOREIGN KEY(plan_item_id) REFERENCES plan_items(id) ON DELETE CASCADE
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS study_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_item_id INTEGER,
                topic_id INTEGER NOT NULL,
                subject_id INTEGER,
                session_date TEXT NOT NULL,
                started_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                ended_at TEXT,
                planned_minutes INTEGER NOT NULL,
                actual_minutes INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                focus_quality INTEGER,
                confidence_after REAL,
                quiz_score REAL,
                review_session INTEGER NOT NULL,
                notes TEXT,
                FOREIGN KEY(plan_item_id) REFERENCES plan_items(id) ON DELETE SET NULL,
                FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE,
                FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE SET NULL
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS review_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                topic_id INTEGER NOT NULL,
                review_date TEXT NOT NULL,
                quality INTEGER NOT NULL,
                easiness_factor_before REAL NOT NULL,
                easiness_factor_after REAL NOT NULL,
                interval_before INTEGER NOT NULL,
                interval_after INTEGER NOT NULL,
                next_review_date TEXT NOT NULL,
                FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS retention_training_data (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                topic_id INTEGER NOT NULL,
                source_session_id INTEGER,
                captured_on TEXT NOT NULL,
                days_since_last_revision REAL NOT NULL,
                difficulty REAL NOT NULL,
                previous_review_quality REAL NOT NULL,
                confidence_score REAL NOT NULL,
                completion_consistency REAL NOT NULL,
                repetitions REAL NOT NULL,
                average_session_quality REAL NOT NULL,
                label INTEGER NOT NULL,
                FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE,
                FOREIGN KEY(source_session_id) REFERENCES study_sessions(id) ON DELETE CASCADE
            );
            """,
            "CREATE INDEX IF NOT EXISTS idx_topics_next_review ON topics(next_review_date);",
            "CREATE INDEX IF NOT EXISTS idx_topics_exam_date ON topics(target_exam_date);",
            "CREATE INDEX IF NOT EXISTS idx_sessions_topic_date ON study_sessions(topic_id, session_date);",
            "CREATE INDEX IF NOT EXISTS idx_reviews_topic_date ON review_records(topic_id, review_date);",
            "CREATE INDEX IF NOT EXISTS idx_training_topic_date ON retention_training_data(topic_id, captured_on);"
        );

        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            migrateStudySessionsTableIfNeeded(connection);
            ensureDailyPlanColumns(connection);
            ensureStudySessionSubjectSnapshot(connection);
            ensureRetentionTrainingColumns(connection);
            ensureSessionIndexes(connection);
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to initialize database schema", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
        }
        return connection;
    }

    public <T> T executeInTransaction(TransactionCallback<T> callback) {
        try (Connection connection = getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = callback.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw new PersistenceException("Failed to execute database transaction", exception);
            } catch (RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to execute database transaction", exception);
        }
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public String getDatabaseLocationLabel() {
        String override = System.getProperty("studyplanner.db.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("STUDYPLANNER_DB_PATH");
        }

        if (override != null && !override.isBlank()) {
            return "Custom path (" + databasePath.getFileName() + ")";
        }

        return "%USERPROFILE%/.adaptive-study-planner/" + databasePath.getFileName();
    }

    private static Path resolveDefaultDatabasePath() {
        String override = System.getProperty("studyplanner.db.path");
        if (override == null || override.isBlank()) {
            override = System.getenv("STUDYPLANNER_DB_PATH");
        }
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }

        return Paths.get(System.getProperty("user.home"), ".adaptive-study-planner", "studyplanner.db");
    }

    private void migrateStudySessionsTableIfNeeded(Connection connection) throws SQLException {
        if (!needsStudySessionMigration(connection)) {
            return;
        }

        boolean hasUpdatedAt = hasStudySessionColumn(connection, "updated_at");
        boolean hasEndedAt = hasStudySessionColumn(connection, "ended_at");

        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF;");
            statement.execute("""
                CREATE TABLE study_sessions_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    plan_item_id INTEGER,
                    topic_id INTEGER NOT NULL,
                    subject_id INTEGER,
                    session_date TEXT NOT NULL,
                    started_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    ended_at TEXT,
                    planned_minutes INTEGER NOT NULL,
                    actual_minutes INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL,
                    focus_quality INTEGER,
                    confidence_after REAL,
                    quiz_score REAL,
                    review_session INTEGER NOT NULL,
                    notes TEXT,
                    FOREIGN KEY(plan_item_id) REFERENCES plan_items(id) ON DELETE SET NULL,
                    FOREIGN KEY(topic_id) REFERENCES topics(id) ON DELETE CASCADE,
                    FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE SET NULL
                )
                """);
            String updatedAtExpression = hasUpdatedAt ? "updated_at" : "started_at";
            String endedAtExpression = hasEndedAt ? "ended_at" : "NULL";
            statement.execute("""
                INSERT INTO study_sessions_new(
                    id, plan_item_id, topic_id, subject_id, session_date, started_at, updated_at, ended_at,
                    planned_minutes, actual_minutes, status, focus_quality, confidence_after,
                    quiz_score, review_session, notes
                )
                SELECT
                    id,
                    plan_item_id,
                    topic_id,
                    (SELECT subject_id FROM topics WHERE topics.id = study_sessions.topic_id),
                    session_date,
                    started_at,
                    %s,
                    %s,
                    planned_minutes,
                    actual_minutes,
                    status,
                    focus_quality,
                    confidence_after,
                    quiz_score,
                    review_session,
                    notes
                FROM study_sessions
                """.formatted(updatedAtExpression, endedAtExpression));
            statement.execute("DROP TABLE study_sessions");
            statement.execute("ALTER TABLE study_sessions_new RENAME TO study_sessions");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
            }
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private void ensureDailyPlanColumns(Connection connection) throws SQLException {
        if (!hasColumn(connection, "daily_plans", "focus_minutes")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE daily_plans ADD COLUMN focus_minutes INTEGER NOT NULL DEFAULT 25");
            }
        }
        if (!hasColumn(connection, "daily_plans", "short_break_minutes")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE daily_plans ADD COLUMN short_break_minutes INTEGER NOT NULL DEFAULT 5");
            }
        }
        if (!hasColumn(connection, "daily_plans", "stale")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE daily_plans ADD COLUMN stale INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    private void ensureStudySessionSubjectSnapshot(Connection connection) throws SQLException {
        if (!hasColumn(connection, "study_sessions", "subject_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE study_sessions ADD COLUMN subject_id INTEGER");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                UPDATE study_sessions
                SET subject_id = (
                    SELECT subject_id FROM topics WHERE topics.id = study_sessions.topic_id
                )
                WHERE subject_id IS NULL
                """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sessions_subject_date ON study_sessions(subject_id, session_date)");
        }
    }

    private void ensureRetentionTrainingColumns(Connection connection) throws SQLException {
        if (!hasColumn(connection, "retention_training_data", "source_session_id")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE retention_training_data ADD COLUMN source_session_id INTEGER");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_training_source_session ON retention_training_data(source_session_id)");
        }
    }

    private boolean needsStudySessionMigration(Connection connection) throws SQLException {
        boolean hasUpdatedAt = false;
        boolean hasEndedAt = false;
        boolean focusQualityNullable = false;
        boolean confidenceNullable = false;

        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA table_info(study_sessions)")) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("name");
                boolean notNull = resultSet.getInt("notnull") == 1;
                if ("updated_at".equals(columnName)) {
                    hasUpdatedAt = true;
                }
                if ("ended_at".equals(columnName)) {
                    hasEndedAt = true;
                }
                if ("focus_quality".equals(columnName)) {
                    focusQualityNullable = !notNull;
                }
                if ("confidence_after".equals(columnName)) {
                    confidenceNullable = !notNull;
                }
            }
        }

        return !hasUpdatedAt || !hasEndedAt || !focusQualityNullable || !confidenceNullable;
    }

    private boolean hasStudySessionColumn(Connection connection, String columnName) throws SQLException {
        return hasColumn(connection, "study_sessions", columnName);
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void ensureSessionIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_active_session_by_plan_item
                ON study_sessions(plan_item_id)
                WHERE plan_item_id IS NOT NULL AND status IN ('STARTED', 'PAUSED')
                """);
            statement.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_active_session_by_topic_date
                ON study_sessions(topic_id, session_date)
                WHERE plan_item_id IS NULL AND status IN ('STARTED', 'PAUSED')
                """);
        }
    }
}
