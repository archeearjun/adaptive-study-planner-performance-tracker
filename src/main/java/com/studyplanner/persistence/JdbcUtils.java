package com.studyplanner.persistence;

import com.studyplanner.utils.DateUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class JdbcUtils {
    private JdbcUtils() {
    }

    public static void setLocalDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        statement.setString(index, DateUtils.toIsoDate(value));
    }

    public static void setLocalDateTime(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        statement.setString(index, DateUtils.toIsoDateTime(value));
    }

    public static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setLong(index, value);
        }
    }

    public static LocalDate getLocalDate(ResultSet resultSet, String columnName) throws SQLException {
        return DateUtils.parseDate(resultSet.getString(columnName));
    }

    public static LocalDateTime getLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        return DateUtils.parseDateTime(resultSet.getString(columnName));
    }
}
