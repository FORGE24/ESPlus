package com.esplus.security.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class MetaDao {
    private final SqliteDatabase database;

    public MetaDao(SqliteDatabase database) {
        this.database = database;
    }

    public Optional<byte[]> findWrappedAesKey() throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT wrapped_aes_key FROM meta WHERE id = 1")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getBytes(1));
            }
        }
    }

    public void insertBootstrap(byte[] wrappedAesKey, int schemaVersion, long createdAt) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "INSERT INTO meta (id, wrapped_aes_key, schema_version, created_at) VALUES (1, ?, ?, ?)")) {
            statement.setBytes(1, wrappedAesKey);
            statement.setInt(2, schemaVersion);
            statement.setLong(3, createdAt);
            statement.executeUpdate();
        }
    }
}
