package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class UserDao {
    private final SqliteDatabase database;

    public UserDao(SqliteDatabase database) {
        this.database = database;
    }

    public Optional<UserRecord> findByUuid(UUID uuid) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT uuid, name, password_cipher, op_bound, role, created_at, updated_at, failed_attempts, locked_until FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public void insert(UserRecord user) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO users (uuid, name, password_cipher, op_bound, role, created_at, updated_at, failed_attempts, locked_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bind(statement, user);
            statement.executeUpdate();
        }
    }

    public void updatePassword(UUID uuid, String name, String passwordCipher, long updatedAt) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE users SET name = ?, password_cipher = ?, updated_at = ?, failed_attempts = 0, locked_until = 0 WHERE uuid = ?")) {
            statement.setString(1, name);
            statement.setString(2, passwordCipher);
            statement.setLong(3, updatedAt);
            statement.setString(4, uuid.toString());
            statement.executeUpdate();
        }
    }

    public void updateLockState(UUID uuid, int failedAttempts, long lockedUntil) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE uuid = ?")) {
            statement.setInt(1, failedAttempts);
            statement.setLong(2, lockedUntil);
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        }
    }

    public boolean delete(UUID uuid) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "DELETE FROM users WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            return statement.executeUpdate() > 0;
        }
    }

    public java.util.List<UUID> findUuidsByRole(String role) throws SQLException {
        java.util.ArrayList<UUID> out = new java.util.ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT uuid FROM users WHERE lower(role) = lower(?)")) {
            statement.setString(1, role);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(UUID.fromString(rs.getString(1)));
                }
            }
        }
        return out;
    }

    private static void bind(PreparedStatement statement, UserRecord user) throws SQLException {
        statement.setString(1, user.uuid().toString());
        statement.setString(2, user.name());
        statement.setString(3, user.passwordCipher());
        statement.setInt(4, user.opBound() ? 1 : 0);
        statement.setString(5, user.role());
        statement.setLong(6, user.createdAt());
        statement.setLong(7, user.updatedAt());
        statement.setInt(8, user.failedAttempts());
        statement.setLong(9, user.lockedUntil());
    }

    private static UserRecord map(ResultSet rs) throws SQLException {
        return new UserRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("password_cipher"),
                rs.getInt("op_bound") != 0,
                rs.getString("role"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getInt("failed_attempts"),
                rs.getLong("locked_until")
        );
    }
}
