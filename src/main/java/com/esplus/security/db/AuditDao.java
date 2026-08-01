package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class AuditDao {
    private final SqliteDatabase database;

    public AuditDao(SqliteDatabase database) {
        this.database = database;
    }

    public void log(UUID uuid, String action, String detail, boolean success) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO audit_log (uuid, action, detail, success, ts) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid == null ? null : uuid.toString());
            statement.setString(2, action);
            statement.setString(3, detail);
            statement.setInt(4, success ? 1 : 0);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }
}
