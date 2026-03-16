package com.studyplanner.persistence;

import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.PlanItemType;
import com.studyplanner.model.PomodoroBlock;
import com.studyplanner.model.PomodoroStatus;
import com.studyplanner.model.SessionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DailyPlanRepository {
    private final DatabaseManager databaseManager;

    public DailyPlanRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Optional<DailyPlan> findByDate(LocalDate planDate) {
        String planSql = "SELECT * FROM daily_plans WHERE plan_date = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(planSql)) {
            JdbcUtils.setLocalDate(statement, 1, planDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                DailyPlan dailyPlan = new DailyPlan();
                dailyPlan.setId(resultSet.getLong("id"));
                dailyPlan.setPlanDate(JdbcUtils.getLocalDate(resultSet, "plan_date"));
                dailyPlan.setAvailableMinutes(resultSet.getInt("available_minutes"));
                dailyPlan.setTotalPlannedMinutes(resultSet.getInt("total_planned_minutes"));
                dailyPlan.setGeneratedAt(JdbcUtils.getLocalDateTime(resultSet, "generated_at"));
                dailyPlan.setSummary(resultSet.getString("summary"));
                dailyPlan.setItems(loadItems(connection, dailyPlan.getId()));
                return Optional.of(dailyPlan);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to load daily plan for " + planDate, exception);
        }
    }

    public DailyPlan saveOrReplace(DailyPlan plan) {
        Connection connection = null;
        try {
            connection = databaseManager.getConnection();
            connection.setAutoCommit(false);
            long existingId = findExistingPlanId(connection, plan.getPlanDate());
            if (existingId != 0) {
                deletePlan(connection, existingId);
            }
            insertPlan(connection, plan);
            connection.commit();
            return plan;
        } catch (SQLException exception) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw new PersistenceException("Failed to save daily plan", exception);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void updatePlanItemProgress(long planItemId, SessionStatus status, int completedMinutes, Integer qualityRating) {
        String sql = "UPDATE plan_items SET status = ?, completed_minutes = ?, quality_rating = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, completedMinutes);
            if (qualityRating == null) {
                statement.setObject(3, null);
            } else {
                statement.setInt(3, qualityRating);
            }
            statement.setLong(4, planItemId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Failed to update plan item progress", exception);
        }
    }

    private long findExistingPlanId(Connection connection, LocalDate planDate) throws SQLException {
        String sql = "SELECT id FROM daily_plans WHERE plan_date = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcUtils.setLocalDate(statement, 1, planDate);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        }
    }

    private void deletePlan(Connection connection, long planId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM daily_plans WHERE id = ?")) {
            statement.setLong(1, planId);
            statement.executeUpdate();
        }
    }

    private void insertPlan(Connection connection, DailyPlan plan) throws SQLException {
        String sql = """
            INSERT INTO daily_plans(plan_date, available_minutes, total_planned_minutes, generated_at, summary)
            VALUES(?, ?, ?, ?, ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            JdbcUtils.setLocalDate(statement, 1, plan.getPlanDate());
            statement.setInt(2, plan.getAvailableMinutes());
            statement.setInt(3, plan.getTotalPlannedMinutes());
            JdbcUtils.setLocalDateTime(statement, 4, plan.getGeneratedAt());
            statement.setString(5, plan.getSummary());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    plan.setId(keys.getLong(1));
                }
            }
        }

        String itemSql = """
            INSERT INTO plan_items(
                plan_id, topic_id, subject_id, topic_name, subject_name, item_type, recommended_order,
                planned_minutes, score, reason, pomodoro_count, recall_probability, status, completed_minutes, quality_rating
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String blockSql = """
            INSERT INTO pomodoro_blocks(plan_item_id, block_index, focus_minutes, break_minutes, status, quality_rating)
            VALUES(?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement itemStatement = connection.prepareStatement(itemSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement blockStatement = connection.prepareStatement(blockSql, Statement.RETURN_GENERATED_KEYS)) {
            for (PlanItem item : plan.getItems()) {
                itemStatement.setLong(1, plan.getId());
                itemStatement.setLong(2, item.getTopicId());
                itemStatement.setLong(3, item.getSubjectId());
                itemStatement.setString(4, item.getTopicName());
                itemStatement.setString(5, item.getSubjectName());
                itemStatement.setString(6, item.getItemType().name());
                itemStatement.setInt(7, item.getRecommendedOrder());
                itemStatement.setInt(8, item.getPlannedMinutes());
                itemStatement.setDouble(9, item.getScore());
                itemStatement.setString(10, item.getReason());
                itemStatement.setInt(11, item.getPomodoroCount());
                itemStatement.setDouble(12, item.getRecallProbability());
                itemStatement.setString(13, item.getStatus().name());
                itemStatement.setInt(14, item.getCompletedMinutes());
                if (item.getQualityRating() == null) {
                    itemStatement.setObject(15, null);
                } else {
                    itemStatement.setInt(15, item.getQualityRating());
                }
                itemStatement.executeUpdate();
                try (ResultSet keys = itemStatement.getGeneratedKeys()) {
                    if (keys.next()) {
                        item.setId(keys.getLong(1));
                    }
                }

                for (PomodoroBlock block : item.getPomodoroBlocks()) {
                    blockStatement.setLong(1, item.getId());
                    blockStatement.setInt(2, block.getBlockIndex());
                    blockStatement.setInt(3, block.getFocusMinutes());
                    blockStatement.setInt(4, block.getBreakMinutes());
                    blockStatement.setString(5, block.getStatus().name());
                    if (block.getQualityRating() == null) {
                        blockStatement.setObject(6, null);
                    } else {
                        blockStatement.setInt(6, block.getQualityRating());
                    }
                    blockStatement.executeUpdate();
                    try (ResultSet blockKeys = blockStatement.getGeneratedKeys()) {
                        if (blockKeys.next()) {
                            block.setId(blockKeys.getLong(1));
                        }
                    }
                    block.setPlanItemId(item.getId());
                }
            }
        }
    }

    private List<PlanItem> loadItems(Connection connection, long planId) throws SQLException {
        String itemSql = "SELECT * FROM plan_items WHERE plan_id = ? ORDER BY recommended_order";
        String blockSql = "SELECT * FROM pomodoro_blocks WHERE plan_item_id IN (SELECT id FROM plan_items WHERE plan_id = ?) ORDER BY block_index";
        Map<Long, PlanItem> itemsById = new LinkedHashMap<>();

        try (PreparedStatement itemStatement = connection.prepareStatement(itemSql)) {
            itemStatement.setLong(1, planId);
            try (ResultSet resultSet = itemStatement.executeQuery()) {
                while (resultSet.next()) {
                    PlanItem item = new PlanItem();
                    item.setId(resultSet.getLong("id"));
                    item.setPlanId(resultSet.getLong("plan_id"));
                    item.setTopicId(resultSet.getLong("topic_id"));
                    item.setSubjectId(resultSet.getLong("subject_id"));
                    item.setTopicName(resultSet.getString("topic_name"));
                    item.setSubjectName(resultSet.getString("subject_name"));
                    item.setItemType(PlanItemType.valueOf(resultSet.getString("item_type")));
                    item.setRecommendedOrder(resultSet.getInt("recommended_order"));
                    item.setPlannedMinutes(resultSet.getInt("planned_minutes"));
                    item.setScore(resultSet.getDouble("score"));
                    item.setReason(resultSet.getString("reason"));
                    item.setPomodoroCount(resultSet.getInt("pomodoro_count"));
                    item.setRecallProbability(resultSet.getDouble("recall_probability"));
                    item.setStatus(SessionStatus.valueOf(resultSet.getString("status")));
                    item.setCompletedMinutes(resultSet.getInt("completed_minutes"));
                    int qualityRating = resultSet.getInt("quality_rating");
                    item.setQualityRating(resultSet.wasNull() ? null : qualityRating);
                    itemsById.put(item.getId(), item);
                }
            }
        }

        try (PreparedStatement blockStatement = connection.prepareStatement(blockSql)) {
            blockStatement.setLong(1, planId);
            try (ResultSet resultSet = blockStatement.executeQuery()) {
                while (resultSet.next()) {
                    PomodoroBlock block = new PomodoroBlock();
                    block.setId(resultSet.getLong("id"));
                    block.setPlanItemId(resultSet.getLong("plan_item_id"));
                    block.setBlockIndex(resultSet.getInt("block_index"));
                    block.setFocusMinutes(resultSet.getInt("focus_minutes"));
                    block.setBreakMinutes(resultSet.getInt("break_minutes"));
                    block.setStatus(PomodoroStatus.valueOf(resultSet.getString("status")));
                    int qualityRating = resultSet.getInt("quality_rating");
                    block.setQualityRating(resultSet.wasNull() ? null : qualityRating);
                    PlanItem item = itemsById.get(block.getPlanItemId());
                    if (item != null) {
                        item.getPomodoroBlocks().add(block);
                    }
                }
            }
        }
        return new ArrayList<>(itemsById.values());
    }
}
