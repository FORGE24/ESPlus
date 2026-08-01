package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ProtectedCommandDao {
    private final SqliteDatabase database;

    public ProtectedCommandDao(SqliteDatabase database) {
        this.database = database;
    }

    public void replaceAll(Map<String, String> commandToRisk) throws SQLException {
        try (Statement clear = database.connection().createStatement()) {
            clear.executeUpdate("DELETE FROM protected_commands");
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO protected_commands (command, risk) VALUES (?, ?)")) {
            for (Map.Entry<String, String> entry : commandToRisk.entrySet()) {
                statement.setString(1, entry.getKey().toLowerCase(Locale.ROOT));
                statement.setString(2, entry.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    public Map<String, String> findAll() throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT command, risk FROM protected_commands");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("command").toLowerCase(Locale.ROOT), rs.getString("risk"));
            }
        }
        return map;
    }
}
