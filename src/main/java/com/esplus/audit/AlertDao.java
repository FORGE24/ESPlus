package com.esplus.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.esplus.security.db.SqliteDatabase;

public final class AlertDao {
    private final SqliteDatabase database;

    public AlertDao(SqliteDatabase database) {
        this.database = database;
    }

    public void insert(AlertRecord alert) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT OR IGNORE INTO alerts (
                        alert_id, ts, severity, rule_code, title, message, actor_uuid, actor_name,
                        related_event_id, related_trace_id, acknowledged
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, alert.alertId());
                statement.setLong(2, alert.ts());
                statement.setString(3, alert.severity());
                statement.setString(4, alert.ruleCode());
                statement.setString(5, alert.title());
                statement.setString(6, alert.message());
                statement.setString(7, alert.actorUuid());
                statement.setString(8, alert.actorName());
                statement.setString(9, alert.relatedEventId());
                statement.setString(10, alert.relatedTraceId());
                statement.setInt(11, alert.acknowledged() ? 1 : 0);
                statement.executeUpdate();
            }
        }
    }

    public List<AlertRecord> list(boolean onlyUnacked, int limit) throws SQLException {
        String sql = onlyUnacked
                ? "SELECT alert_id, ts, severity, rule_code, title, message, actor_uuid, actor_name, related_event_id, related_trace_id, acknowledged FROM alerts WHERE acknowledged = 0 ORDER BY ts DESC LIMIT ?"
                : "SELECT alert_id, ts, severity, rule_code, title, message, actor_uuid, actor_name, related_event_id, related_trace_id, acknowledged FROM alerts ORDER BY ts DESC LIMIT ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = statement.executeQuery()) {
                List<AlertRecord> alerts = new ArrayList<>();
                while (rs.next()) {
                    alerts.add(map(rs));
                }
                return alerts;
            }
        }
    }

    public boolean acknowledge(String alertId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE alerts SET acknowledged = 1 WHERE alert_id = ?")) {
            statement.setString(1, alertId);
            return statement.executeUpdate() > 0;
        }
    }

    public long countUnacked() throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static AlertRecord map(ResultSet rs) throws SQLException {
        return new AlertRecord(
                rs.getString(1),
                rs.getLong(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getString(9),
                rs.getString(10),
                rs.getInt(11) != 0
        );
    }
}
