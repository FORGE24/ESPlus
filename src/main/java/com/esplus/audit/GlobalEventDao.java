package com.esplus.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import com.esplus.Config;
import com.esplus.security.db.SqliteDatabase;

public final class GlobalEventDao {
    private static final String GENESIS = "GENESIS";

    private final SqliteDatabase database;

    public GlobalEventDao(SqliteDatabase database) {
        this.database = database;
    }

    /** Config value or fallback when NeoForge config not loaded (unit tests). */
    static boolean configBool(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue v, boolean fallback) {
        try {
            return v.getAsBoolean();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }

    static int configInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue v, int fallback) {
        try {
            return v.getAsInt();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }

    static String configStr(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> v, String fallback) {
        try {
            return v.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }

    public void insert(GlobalEvent event) throws SQLException {
        synchronized (database.lock()) {
            String prevHash = GENESIS;
            String eventHash = null;
            boolean chain = configBool(Config.AUDIT_HASH_CHAIN, false);
            if (chain) {
                prevHash = readTipUnlocked().orElse(GENESIS);
                eventHash = sha256(canonical(event, prevHash));
            }
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    INSERT INTO global_events (
                        event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                        dimension, x, y, z, item_id, trace_id, detail, source, event_hash, prev_hash
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, event.eventId());
                statement.setLong(2, event.ts());
                statement.setString(3, event.category());
                statement.setString(4, event.action());
                statement.setString(5, event.actorUuid());
                statement.setString(6, event.actorName());
                statement.setString(7, event.targetUuid());
                statement.setString(8, event.targetName());
                statement.setString(9, event.dimension());
                setNullableDouble(statement, 10, event.x());
                setNullableDouble(statement, 11, event.y());
                setNullableDouble(statement, 12, event.z());
                statement.setString(13, event.itemId());
                statement.setString(14, event.traceId());
                statement.setString(15, event.detail());
                statement.setString(16, event.source());
                statement.setString(17, eventHash);
                statement.setString(18, chain ? prevHash : null);
                statement.executeUpdate();
            }
            if (chain && eventHash != null) {
                writeTipUnlocked(eventHash);
            }
        }
    }

    public IntegrityReport verifyChain(int maxRows) throws SQLException {
        synchronized (database.lock()) {
            int checked = 0;
            String expectedPrev = GENESIS;
            String firstBreak = null;
            String tip = null;
            try (PreparedStatement statement = database.connection().prepareStatement(
                    """
                    SELECT event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                           dimension, x, y, z, item_id, trace_id, detail, source, event_hash, prev_hash
                    FROM global_events
                    ORDER BY id ASC
                    LIMIT ?
                    """)) {
                statement.setInt(1, Math.max(1, Math.min(maxRows, 500_000)));
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        checked++;
                        String prev = rs.getString("prev_hash");
                        String hash = rs.getString("event_hash");
                        if (hash == null || hash.isBlank()) {
                            if (firstBreak == null) {
                                firstBreak = rs.getString("event_id") + " (missing hash)";
                            }
                            continue;
                        }
                        if (prev == null || !prev.equals(expectedPrev)) {
                            if (firstBreak == null) {
                                firstBreak = rs.getString("event_id") + " (prev mismatch)";
                            }
                        }
                        GlobalEvent event = map(rs);
                        String recomputed = sha256(canonical(event, prev == null ? GENESIS : prev));
                        if (!hash.equalsIgnoreCase(recomputed)) {
                            if (firstBreak == null) {
                                firstBreak = rs.getString("event_id") + " (hash mismatch)";
                            }
                        }
                        expectedPrev = hash;
                        tip = hash;
                    }
                }
            }
            String storedTip = readTipUnlocked().orElse(null);
            boolean tipOk = tip == null || tip.equals(storedTip) || checked == 0;
            boolean ok = firstBreak == null && tipOk;
            return new IntegrityReport(ok, checked, firstBreak, tip, storedTip, tipOk);
        }
    }

    public record IntegrityReport(
            boolean ok,
            int checked,
            String firstBreakEventId,
            String computedTip,
            String storedTip,
            boolean tipMatches
    ) {
    }

    private Optional<String> readTipUnlocked() throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT audit_chain_tip FROM meta WHERE id = 1")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String tip = rs.getString(1);
                return tip == null || tip.isBlank() ? Optional.empty() : Optional.of(tip);
            }
        }
    }

    private void writeTipUnlocked(String tip) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE meta SET audit_chain_tip = ? WHERE id = 1")) {
            statement.setString(1, tip);
            statement.executeUpdate();
        }
    }

    private static String canonical(GlobalEvent event, String prevHash) {
        return String.join("|",
                nullToEmpty(prevHash),
                nullToEmpty(event.eventId()),
                Long.toString(event.ts()),
                nullToEmpty(event.category()),
                nullToEmpty(event.action()),
                nullToEmpty(event.actorUuid()),
                nullToEmpty(event.actorName()),
                nullToEmpty(event.targetUuid()),
                nullToEmpty(event.targetName()),
                nullToEmpty(event.dimension()),
                String.valueOf(event.x()),
                String.valueOf(event.y()),
                String.valueOf(event.z()),
                nullToEmpty(event.itemId()),
                nullToEmpty(event.traceId()),
                nullToEmpty(event.detail()),
                nullToEmpty(event.source())
        );
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public List<GlobalEvent> search(String query, String category, String actorUuid, String traceId, long fromTs, long toTs, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                       dimension, x, y, z, item_id, trace_id, detail, source
                FROM global_events WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            sql.append(" AND (detail LIKE ? OR action LIKE ? OR actor_name LIKE ? OR item_id LIKE ? OR event_id LIKE ?)");
            String like = "%" + query + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (actorUuid != null && !actorUuid.isBlank()) {
            sql.append(" AND actor_uuid = ?");
            params.add(actorUuid);
        }
        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND trace_id = ?");
            params.add(traceId);
        }
        if (fromTs > 0L) {
            sql.append(" AND ts >= ?");
            params.add(fromTs);
        }
        if (toTs > 0L) {
            sql.append(" AND ts <= ?");
            params.add(toTs);
        }
        sql.append(" ORDER BY ts DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 1000)));

        try (PreparedStatement statement = database.connection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<GlobalEvent> events = new ArrayList<>();
                while (rs.next()) {
                    events.add(map(rs));
                }
                return events;
            }
        }
    }

    public Optional<GlobalEvent> findByEventId(String eventId) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                       dimension, x, y, z, item_id, trace_id, detail, source
                FROM global_events WHERE event_id = ?
                """)) {
            statement.setString(1, eventId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    public List<GlobalEvent> around(long centerTs, long windowMs, String actorUuid, int limit) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                """
                SELECT event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                       dimension, x, y, z, item_id, trace_id, detail, source
                FROM global_events
                WHERE ts BETWEEN ? AND ?
                  AND (? IS NULL OR actor_uuid = ?)
                ORDER BY ts ASC
                LIMIT ?
                """)) {
            statement.setLong(1, centerTs - windowMs);
            statement.setLong(2, centerTs + windowMs);
            statement.setString(3, actorUuid);
            statement.setString(4, actorUuid);
            statement.setInt(5, Math.max(1, Math.min(limit, 2000)));
            try (ResultSet rs = statement.executeQuery()) {
                List<GlobalEvent> events = new ArrayList<>();
                while (rs.next()) {
                    events.add(map(rs));
                }
                return events;
            }
        }
    }

    public long countSince(long sinceTs) throws SQLException {
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM global_events WHERE ts >= ?")) {
            statement.setLong(1, sinceTs);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private static GlobalEvent map(ResultSet rs) throws SQLException {
        return new GlobalEvent(
                rs.getString("event_id"),
                rs.getLong("ts"),
                rs.getString("category"),
                rs.getString("action"),
                rs.getString("actor_uuid"),
                rs.getString("actor_name"),
                rs.getString("target_uuid"),
                rs.getString("target_name"),
                rs.getString("dimension"),
                (Double) rs.getObject("x"),
                (Double) rs.getObject("y"),
                (Double) rs.getObject("z"),
                rs.getString("item_id"),
                rs.getString("trace_id"),
                rs.getString("detail"),
                rs.getString("source")
        );
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
        } else {
            statement.setDouble(index, value);
        }
    }
}
