package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PermissionDao {
    private final SqliteDatabase database;

    public PermissionDao(SqliteDatabase database) {
        this.database = database;
    }

    public Set<String> findAllowed(UUID uuid) throws SQLException {
        Set<String> set = new HashSet<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT perm FROM user_permissions WHERE uuid = ? AND allowed = 1")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("perm"));
                }
            }
        }
        return set;
    }

    public boolean hasAny(UUID uuid) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT 1 FROM user_permissions WHERE uuid = ? LIMIT 1")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void replaceAll(UUID uuid, Set<String> allowedPerms) throws SQLException {
        try (PreparedStatement delete = database.connection().prepareStatement(
                "DELETE FROM user_permissions WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
        if (allowedPerms == null || allowedPerms.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = database.connection().prepareStatement(
                "INSERT INTO user_permissions (uuid, perm, allowed) VALUES (?, ?, 1)")) {
            for (String perm : allowedPerms) {
                insert.setString(1, uuid.toString());
                insert.setString(2, perm);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public void deleteAll(UUID uuid) throws SQLException {
        try (PreparedStatement delete = database.connection().prepareStatement(
                "DELETE FROM user_permissions WHERE uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        }
    }

    public boolean isAllowed(UUID uuid, String perm) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT allowed FROM user_permissions WHERE uuid = ? AND perm = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, perm);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return rs.getInt("allowed") == 1;
            }
        }
    }
}
