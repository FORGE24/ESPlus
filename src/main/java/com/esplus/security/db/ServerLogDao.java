package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class ServerLogDao {
    private final SqliteDatabase database;

    public ServerLogDao(SqliteDatabase database) {
        this.database = database;
    }

    public void insertBatch(List<LogLine> lines) throws SQLException {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO server_logs (ts, level, logger, message) VALUES (?, ?, ?, ?)")) {
            for (LogLine line : lines) {
                statement.setLong(1, line.ts());
                statement.setString(2, line.level());
                statement.setString(3, truncate(line.logger(), 128));
                statement.setString(4, truncate(line.message(), 4000));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public void prune(int keep) throws SQLException {
        try (Statement statement = database.connection().createStatement()) {
            statement.executeUpdate(
                    "DELETE FROM server_logs WHERE id NOT IN (SELECT id FROM server_logs ORDER BY id DESC LIMIT "
                            + Math.max(100, keep) + ")");
        }
    }

    public record LogLine(long ts, String level, String logger, String message) {
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
