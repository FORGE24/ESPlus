package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public final class MfaDao {
    private final SqliteDatabase database;

    public MfaDao(SqliteDatabase database) {
        this.database = database;
    }

    public Optional<MfaRecord> findUser(UUID uuid) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT uuid, secret, enabled, updated_at FROM user_mfa WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new MfaRecord(
                        rs.getString(1), rs.getString(2), rs.getInt(3) != 0, rs.getLong(4)));
            }
        }
    }

    public void upsertUser(UUID uuid, String secret, boolean enabled) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT INTO user_mfa (uuid, secret, enabled, updated_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET secret=excluded.secret, enabled=excluded.enabled, updated_at=excluded.updated_at
                    """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, secret);
                statement.setInt(3, enabled ? 1 : 0);
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
        }
    }

    public void setUserEnabled(UUID uuid, boolean enabled) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "UPDATE user_mfa SET enabled = ?, updated_at = ? WHERE uuid = ?")) {
                statement.setInt(1, enabled ? 1 : 0);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, uuid.toString());
                statement.executeUpdate();
            }
        }
    }

    public record MfaRecord(String uuid, String secret, boolean enabled, long updatedAt) {
    }
}
