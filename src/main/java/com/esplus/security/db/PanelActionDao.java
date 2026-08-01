package com.esplus.security.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PanelActionDao {
    private final SqliteDatabase database;

    public PanelActionDao(SqliteDatabase database) {
        this.database = database;
    }

    public record PanelAction(
            long id,
            String action,
            String targetUuid,
            String targetName,
            String payload,
            long createdAt
    ) {
    }

    /**
     * Atomically claim pending actions under the DB write lock + a single transaction.
     */
    public List<PanelAction> claimPending(int limit) throws SQLException {
        synchronized (database.lock()) {
            Connection conn = database.connection();
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                List<Long> ids = new ArrayList<>();
                try (PreparedStatement statement = conn.prepareStatement(
                        """
                        SELECT id FROM panel_actions
                        WHERE status = 'pending'
                        ORDER BY id ASC
                        LIMIT ?
                        """)) {
                    statement.setInt(1, Math.max(1, limit));
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            ids.add(rs.getLong(1));
                        }
                    }
                }
                if (ids.isEmpty()) {
                    conn.commit();
                    return List.of();
                }
                try (PreparedStatement statement = conn.prepareStatement(
                        "UPDATE panel_actions SET status = 'processing' WHERE id = ? AND status = 'pending'")) {
                    for (Long id : ids) {
                        statement.setLong(1, id);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                List<PanelAction> list = new ArrayList<>();
                try (PreparedStatement statement = conn.prepareStatement(
                        """
                        SELECT id, action, target_uuid, target_name, payload, created_at, status
                        FROM panel_actions WHERE id = ?
                        """)) {
                    for (Long id : ids) {
                        statement.setLong(1, id);
                        try (ResultSet rs = statement.executeQuery()) {
                            if (rs.next() && "processing".equals(rs.getString("status"))) {
                                list.add(new PanelAction(
                                        rs.getLong("id"),
                                        rs.getString("action"),
                                        rs.getString("target_uuid"),
                                        rs.getString("target_name"),
                                        rs.getString("payload"),
                                        rs.getLong("created_at")
                                ));
                            }
                        }
                    }
                }
                conn.commit();
                return list;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(previous);
            }
        }
    }

    public void markDone(long id, boolean ok, String result) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    "UPDATE panel_actions SET status = ?, result = ?, processed_at = ? WHERE id = ?")) {
                statement.setString(1, ok ? "done" : "failed");
                statement.setString(2, truncate(result, 2000));
                statement.setLong(3, System.currentTimeMillis());
                statement.setLong(4, id);
                statement.executeUpdate();
            }
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
