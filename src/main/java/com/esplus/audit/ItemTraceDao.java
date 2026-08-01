package com.esplus.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.esplus.security.db.SqliteDatabase;

public final class ItemTraceDao {
    private final SqliteDatabase database;

    public ItemTraceDao(SqliteDatabase database) {
        this.database = database;
    }

    public void insertTrace(ItemTrace trace) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT OR IGNORE INTO item_traces (
                    trace_id, item_id, created_at, origin_type, origin_actor_uuid, origin_actor_name, origin_detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, trace.traceId());
            statement.setString(2, trace.itemId());
            statement.setLong(3, trace.createdAt());
            statement.setString(4, trace.originType());
            statement.setString(5, trace.originActorUuid());
            statement.setString(6, trace.originActorName());
            statement.setString(7, trace.originDetail());
            statement.executeUpdate();
        }
    }

    public void insertLink(ItemTraceLink link) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                INSERT INTO item_trace_links (
                    trace_id, parent_trace_id, event_id, ts, action, actor_uuid, actor_name, detail
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, link.traceId());
            statement.setString(2, link.parentTraceId());
            statement.setString(3, link.eventId());
            statement.setLong(4, link.ts());
            statement.setString(5, link.action());
            statement.setString(6, link.actorUuid());
            statement.setString(7, link.actorName());
            statement.setString(8, link.detail());
            statement.executeUpdate();
        }
    }

    public Optional<ItemTrace> findTrace(String traceId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT trace_id, item_id, created_at, origin_type, origin_actor_uuid, origin_actor_name, origin_detail FROM item_traces WHERE trace_id = ?")) {
            statement.setString(1, traceId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ItemTrace(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7)
                ));
            }
        }
    }

    public List<ItemTraceLink> linksForTrace(String traceId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT id, trace_id, parent_trace_id, event_id, ts, action, actor_uuid, actor_name, detail
                FROM item_trace_links WHERE trace_id = ? OR parent_trace_id = ?
                ORDER BY ts ASC
                """)) {
            statement.setString(1, traceId);
            statement.setString(2, traceId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ItemTraceLink> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(new ItemTraceLink(
                            rs.getLong(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getLong(5),
                            rs.getString(6),
                            rs.getString(7),
                            rs.getString(8),
                            rs.getString(9)
                    ));
                }
                return links;
            }
        }
    }
}
