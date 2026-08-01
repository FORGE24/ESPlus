package com.esplus.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.esplus.security.db.SqliteDatabase;

public final class MovementDao {
    private final SqliteDatabase database;

    public MovementDao(SqliteDatabase database) {
        this.database = database;
    }

    public void insert(MovementSample sample) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO player_movements (
                    ts, player_uuid, player_name, dimension, x, y, z, yaw, pitch, on_ground, sprinting, flying
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, sample.ts());
            statement.setString(2, sample.playerUuid());
            statement.setString(3, sample.playerName());
            statement.setString(4, sample.dimension());
            statement.setDouble(5, sample.x());
            statement.setDouble(6, sample.y());
            statement.setDouble(7, sample.z());
            statement.setFloat(8, sample.yaw());
            statement.setFloat(9, sample.pitch());
            statement.setInt(10, sample.onGround() ? 1 : 0);
            statement.setInt(11, sample.sprinting() ? 1 : 0);
            statement.setInt(12, sample.flying() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public List<MovementSample> forPlayer(String playerUuid, long fromTs, long toTs, int limit) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT ts, player_uuid, player_name, dimension, x, y, z, yaw, pitch, on_ground, sprinting, flying
                FROM player_movements
                WHERE player_uuid = ?
                  AND (? <= 0 OR ts >= ?)
                  AND (? <= 0 OR ts <= ?)
                ORDER BY ts ASC
                LIMIT ?
                """)) {
            statement.setString(1, playerUuid);
            statement.setLong(2, fromTs);
            statement.setLong(3, fromTs);
            statement.setLong(4, toTs);
            statement.setLong(5, toTs);
            statement.setInt(6, Math.max(1, Math.min(limit, 5000)));
            try (ResultSet rs = statement.executeQuery()) {
                List<MovementSample> samples = new ArrayList<>();
                while (rs.next()) {
                    samples.add(new MovementSample(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getDouble(5),
                            rs.getDouble(6),
                            rs.getDouble(7),
                            rs.getFloat(8),
                            rs.getFloat(9),
                            rs.getInt(10) != 0,
                            rs.getInt(11) != 0,
                            rs.getInt(12) != 0
                    ));
                }
                return samples;
            }
        }
    }
}
