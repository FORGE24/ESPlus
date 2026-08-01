package com.esplus.panel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PanelQueryService {
    private final JdbcTemplate jdbc;
    private final PanelGovernanceService governance;

    public PanelQueryService(JdbcTemplate jdbc, PanelGovernanceService governance) {
        this.jdbc = jdbc;
        this.governance = governance;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> map = new HashMap<>();
        long dayAgo = System.currentTimeMillis() - 86_400_000L;
        map.put("events24h", queryLong("SELECT COUNT(*) FROM global_events WHERE ts >= ?", dayAgo));
        map.put("alertsOpen", queryLong("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0"));
        map.put("traces", queryLong("SELECT COUNT(*) FROM item_traces"));
        map.put("movements24h", queryLong("SELECT COUNT(*) FROM player_movements WHERE ts >= ?", dayAgo));
        map.put("audit24h", queryLong("SELECT COUNT(*) FROM audit_log WHERE ts >= ?", dayAgo));
        map.put("usersCount", queryLong("SELECT COUNT(*) FROM users"));
        map.put("recentEvents", jdbc.queryForList(
                "SELECT event_id, ts, category, action, actor_name, item_id, trace_id, detail FROM global_events ORDER BY ts DESC LIMIT 30"));
        map.put("recentAlerts", jdbc.queryForList(
                "SELECT alert_id, ts, severity, title, message, actor_name, acknowledged FROM alerts ORDER BY ts DESC LIMIT 20"));
        map.put("recentAudit", jdbc.queryForList(
                "SELECT id, uuid, action, detail, success, ts FROM audit_log ORDER BY ts DESC LIMIT 20"));
        try {
            ensureSnapshotTables();
            map.put("runtime", runtimeSnapshot());
            map.put("onlineCount", queryLong("SELECT COUNT(*) FROM online_players"));
            ensurePanelActionsTable();
            map.put("pendingActions", queryLong("SELECT COUNT(*) FROM panel_actions WHERE status = 'pending'"));
            map.put("failedActions", queryLong(
                    "SELECT COUNT(*) FROM panel_actions WHERE status = 'failed' AND created_at >= ?", dayAgo));
            map.put("schedules", listSchedules(10));
            map.put("tpsSamples", perfSamplesAsc(60));
        } catch (Exception ignored) {
            map.put("runtime", Map.of());
            map.put("onlineCount", 0L);
            map.put("pendingActions", 0L);
            map.put("failedActions", 0L);
            map.put("schedules", List.of());
            map.put("tpsSamples", List.of());
        }
        return map;
    }

    public List<Map<String, Object>> auditLogs(String action, String uuid, Boolean success, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, uuid, action, detail, success, ts FROM audit_log WHERE 1=1");
        var args = new java.util.ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            args.add(action);
        }
        if (uuid != null && !uuid.isBlank()) {
            sql.append(" AND uuid = ?");
            args.add(uuid);
        }
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success ? 1 : 0);
        }
        sql.append(" ORDER BY ts DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** Read-only user summary — never exposes password_cipher. */
    public List<Map<String, Object>> usersSummary() {
        return jdbc.queryForList(
                """
                SELECT uuid, name, role, op_bound, created_at, updated_at, failed_attempts, locked_until
                FROM users
                ORDER BY updated_at DESC
                LIMIT 200
                """);
    }

    public Map<String, Object> adminsPage() {
        ensurePanelActionsTable();
        Map<String, Object> map = new HashMap<>();
        map.put("users", usersSummary());
        map.put("lockedCount", queryLong(
                "SELECT COUNT(*) FROM users WHERE locked_until > ?", System.currentTimeMillis()));
        map.put("roles", List.of("owner", "admin", "moderator", "builder", "helper", "op", "viewer"));
        map.put("recentOpActions", jdbc.queryForList(
                """
                SELECT id, action, target_uuid, target_name, status, result, created_at, processed_at
                FROM panel_actions
                WHERE action IN ('grant_op', 'revoke_op')
                ORDER BY id DESC
                LIMIT 30
                """));
        return map;
    }

    public boolean enqueueGrantOp(String playerName, String uuid) {
        return enqueueOpAction("grant_op", playerName, uuid);
    }

    public boolean enqueueRevokeOp(String playerName, String uuid) {
        return enqueueOpAction("revoke_op", playerName, uuid);
    }

    public boolean enqueueKick(String playerName, String uuid, String reason) {
        return enqueueModerationAction("kick_player", playerName, uuid, reason);
    }

    public boolean enqueueBan(String playerName, String uuid, String reason) {
        return enqueueModerationAction("ban_player", playerName, uuid, reason);
    }

    public boolean enqueueUnban(String playerName, String uuid) {
        return enqueueModerationAction("unban_player", playerName, uuid, null);
    }

    private boolean enqueueModerationAction(String action, String playerName, String uuid, String reason) {
        String name = playerName == null ? null : playerName.trim();
        String id = uuid == null ? null : uuid.trim();
        if ((name == null || name.isBlank()) && (id == null || id.isBlank())) {
            return false;
        }
        if (id != null && !id.isBlank() && !validUuid(id)) {
            return false;
        }
        String payload = null;
        if (reason != null) {
            payload = reason.trim();
            if (payload.length() > 200) {
                payload = payload.substring(0, 200);
            }
            if (payload.isBlank()) {
                payload = null;
            }
        }
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES (?, ?, ?, ?, 'pending', ?)
                """,
                action,
                (id == null || id.isBlank()) ? null : id,
                (name == null || name.isBlank()) ? null : name,
                payload,
                System.currentTimeMillis());
        String detail = (name == null || name.isBlank() ? id : name)
                + (payload == null ? "" : (" | " + payload));
        writePanelAudit(id, "panel_" + action, detail, true);
        return true;
    }

    private boolean enqueueOpAction(String action, String playerName, String uuid) {
        String name = playerName == null ? null : playerName.trim();
        String id = uuid == null ? null : uuid.trim();
        if ((name == null || name.isBlank()) && (id == null || id.isBlank())) {
            return false;
        }
        if (id != null && !id.isBlank() && !validUuid(id)) {
            return false;
        }
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, status, created_at)
                VALUES (?, ?, ?, 'pending', ?)
                """,
                action,
                (id == null || id.isBlank()) ? null : id,
                (name == null || name.isBlank()) ? null : name,
                System.currentTimeMillis());
        writePanelAudit(id, "panel_" + action, name == null ? id : name, true);
        return true;
    }

    private void ensurePanelActionsTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS panel_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action TEXT NOT NULL,
                    target_uuid TEXT,
                    target_name TEXT,
                    payload TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    result TEXT,
                    created_at INTEGER NOT NULL,
                    processed_at INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    level TEXT NOT NULL,
                    logger TEXT,
                    message TEXT NOT NULL
                )
                """);
    }

    public boolean enqueueConsoleCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String cmd = command.trim();
        if (cmd.length() > 2000) {
            return false;
        }
        ensurePanelActionsTable();
        String result = governance.enqueueOrApprove("console_cmd", null, null, cmd, "panel", "控制台指令");
        if ("fail".equals(result)) {
            return false;
        }
        writePanelAudit(null, "panel_console_" + result, cmd, true);
        return true;
    }

    public List<Map<String, Object>> serverLogs(String level, String q, Long afterId, int limit) {
        ensurePanelActionsTable();
        StringBuilder sql = new StringBuilder(
                "SELECT id, ts, level, logger, message FROM server_logs WHERE 1=1");
        var args = new java.util.ArrayList<>();
        if (afterId != null && afterId > 0) {
            sql.append(" AND id > ?");
            args.add(afterId);
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND level = ?");
            args.add(level.trim().toUpperCase());
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (message LIKE ? OR logger LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 1000)));
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        java.util.Collections.reverse(rows);
        return rows;
    }

    public List<Map<String, Object>> recentConsoleActions(int limit) {
        ensurePanelActionsTable();
        return jdbc.queryForList(
                """
                SELECT id, action, payload, status, result, created_at, processed_at
                FROM panel_actions
                WHERE action = 'console_cmd'
                ORDER BY id DESC
                LIMIT ?
                """,
                Math.max(1, Math.min(limit, 100)));
    }

    public Map<String, Object> consolePage(String level, String q) {
        Map<String, Object> map = new HashMap<>();
        map.put("logs", serverLogs(level, q, null, 300));
        map.put("commands", recentConsoleActions(30));
        map.put("level", level);
        map.put("q", q);
        return map;
    }

    public boolean unlockUser(String uuid) {
        if (!validUuid(uuid)) {
            return false;
        }
        int n = jdbc.update(
                "UPDATE users SET failed_attempts = 0, locked_until = 0, updated_at = ? WHERE uuid = ?",
                System.currentTimeMillis(), uuid);
        if (n > 0) {
            writePanelAudit(uuid, "panel_unlock", "unlocked via admin panel", true);
        }
        return n > 0;
    }

    /** Clears sudo password record — player must /setoppw again in-game. */
    public boolean resetUserPassword(String uuid) {
        if (!validUuid(uuid)) {
            return false;
        }
        jdbc.update("DELETE FROM user_permissions WHERE uuid = ?", uuid);
        int n = jdbc.update("DELETE FROM users WHERE uuid = ?", uuid);
        if (n > 0) {
            writePanelAudit(uuid, "panel_password_reset", "password cleared via admin panel", true);
        }
        return n > 0;
    }

    public boolean updateUserRole(String uuid, String role) {
        if (!validUuid(uuid) || role == null || role.isBlank()) {
            return false;
        }
        String normalized = role.trim().toLowerCase();
        if (!List.of("owner", "admin", "moderator", "builder", "helper", "op", "viewer").contains(normalized)) {
            return false;
        }
        int n = jdbc.update(
                "UPDATE users SET role = ?, updated_at = ? WHERE uuid = ?",
                normalized, System.currentTimeMillis(), uuid);
        if (n > 0) {
            applyRolePermissionDefaults(uuid, normalized);
            writePanelAudit(uuid, "panel_role_update", "role=" + normalized, true);
        }
        return n > 0;
    }

    public boolean updateOpBound(String uuid, boolean opBound) {
        if (!validUuid(uuid)) {
            return false;
        }
        int n = jdbc.update(
                "UPDATE users SET op_bound = ?, updated_at = ? WHERE uuid = ?",
                opBound ? 1 : 0, System.currentTimeMillis(), uuid);
        if (n > 0) {
            writePanelAudit(uuid, "panel_op_bound", "op_bound=" + opBound, true);
        }
        return n > 0;
    }

    public Map<String, Object> userPermissionsPage(String uuid) {
        Map<String, Object> map = new HashMap<>();
        if (!validUuid(uuid)) {
            map.put("found", false);
            return map;
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT uuid, name, role FROM users WHERE uuid = ?", uuid);
        if (users.isEmpty()) {
            map.put("found", false);
            return map;
        }
        map.put("found", true);
        map.put("user", users.getFirst());
        List<String> catalog = permissionCatalog();
        java.util.Set<String> allowed = new java.util.HashSet<>(jdbc.query(
                "SELECT perm FROM user_permissions WHERE uuid = ? AND allowed = 1",
                (rs, i) -> rs.getString(1),
                uuid));
        if (allowed.isEmpty()) {
            allowed.addAll(defaultsForRole((String) users.getFirst().get("role")));
            replacePermissions(uuid, allowed);
        }
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String perm : catalog) {
            Map<String, Object> row = new HashMap<>();
            row.put("perm", perm);
            row.put("allowed", allowed.contains(perm));
            row.put("label", permissionLabel(perm));
            rows.add(row);
        }
        map.put("perms", rows);
        return map;
    }

    public boolean saveUserPermissions(String uuid, List<String> selected) {
        if (!validUuid(uuid)) {
            return false;
        }
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE uuid = ?", Long.class, uuid);
        if (exists == null || exists == 0) {
            return false;
        }
        java.util.Set<String> catalog = new java.util.HashSet<>(permissionCatalog());
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        if (selected != null) {
            for (String p : selected) {
                if (p != null && catalog.contains(p)) {
                    allowed.add(p);
                }
            }
        }
        replacePermissions(uuid, allowed);
        writePanelAudit(uuid, "panel_perm_update", "perms=" + allowed.size(), true);
        return true;
    }

    public boolean applyRolePermissionDefaults(String uuid, String role) {
        if (!validUuid(uuid)) {
            return false;
        }
        replacePermissions(uuid, defaultsForRole(role));
        writePanelAudit(uuid, "panel_perm_role_seed", "role=" + role, true);
        return true;
    }

    private void replacePermissions(String uuid, java.util.Set<String> allowed) {
        jdbc.update("DELETE FROM user_permissions WHERE uuid = ?", uuid);
        for (String perm : allowed) {
            jdbc.update("INSERT INTO user_permissions (uuid, perm, allowed) VALUES (?, ?, 1)", uuid, perm);
        }
    }

    private static List<String> permissionCatalog() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        set.add("sudo.session");
        set.add("sudo.give");
        for (String cmd : List.of(
                "give", "gamemode", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop",
                "whitelist", "difficulty", "tp", "teleport", "kill", "clear", "effect", "enchant", "xp",
                "experience", "setblock", "fill", "clone", "summon", "setworldspawn", "time", "weather", "gamerule"
        )) {
            set.add("cmd." + cmd);
        }
        return new java.util.ArrayList<>(set);
    }

    private static java.util.Set<String> defaultsForRole(String role) {
        String r = role == null ? "op" : role.toLowerCase();
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        List<String> all = permissionCatalog();
        return switch (r) {
            case "owner", "admin" -> {
                set.addAll(all);
                yield set;
            }
            case "moderator" -> {
                set.add("sudo.session");
                for (String p : List.of("cmd.kick", "cmd.ban", "cmd.ban-ip", "cmd.pardon", "cmd.pardon-ip",
                        "cmd.tp", "cmd.teleport", "cmd.gamemode", "cmd.clear", "cmd.effect")) {
                    set.add(p);
                }
                yield set;
            }
            case "builder" -> {
                set.add("sudo.session");
                for (String p : List.of("cmd.gamemode", "cmd.tp", "cmd.teleport", "cmd.give", "cmd.setblock",
                        "cmd.fill", "cmd.clone", "cmd.summon", "cmd.time", "cmd.weather", "cmd.gamerule")) {
                    set.add(p);
                }
                yield set;
            }
            case "helper" -> {
                set.add("sudo.session");
                for (String p : List.of("cmd.kick", "cmd.tp", "cmd.teleport", "cmd.gamemode")) {
                    set.add(p);
                }
                yield set;
            }
            case "viewer" -> set;
            default -> {
                set.add("sudo.session");
                yield set;
            }
        };
    }

    private static String permissionLabel(String perm) {
        return switch (perm) {
            case "sudo.session" -> "Open sudo session";
            case "sudo.give" -> "/sudo give (admin only by default)";
            default -> perm.startsWith("cmd.") ? "/" + perm.substring(4) : perm;
        };
    }

    private void writePanelAudit(String uuid, String action, String detail, boolean success) {
        jdbc.update(
                "INSERT INTO audit_log (uuid, action, detail, success, ts) VALUES (?, ?, ?, ?, ?)",
                uuid, action, detail, success ? 1 : 0, System.currentTimeMillis());
    }

    private static boolean validUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        try {
            java.util.UUID.fromString(uuid.trim());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public List<Map<String, Object>> search(String q, String category, String actor, String traceId, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT event_id, ts, category, action, actor_uuid, actor_name, item_id, trace_id, detail, source FROM global_events WHERE 1=1");
        var args = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (detail LIKE ? OR action LIKE ? OR actor_name LIKE ? OR item_id LIKE ?)");
            String like = "%" + q + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            args.add(category);
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND (actor_uuid = ? OR actor_name LIKE ?)");
            args.add(actor);
            args.add("%" + actor + "%");
        }
        if (traceId != null && !traceId.isBlank()) {
            sql.append(" AND trace_id = ?");
            args.add(traceId);
        }
        sql.append(" ORDER BY ts DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limit, 500)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> itemTrace(String traceId) {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> traces = jdbc.queryForList(
                "SELECT * FROM item_traces WHERE trace_id = ?", traceId);
        map.put("found", !traces.isEmpty());
        map.put("trace", traces.isEmpty() ? null : traces.getFirst());
        map.put("links", jdbc.queryForList(
                "SELECT * FROM item_trace_links WHERE trace_id = ? OR parent_trace_id = ? ORDER BY ts ASC",
                traceId, traceId));
        map.put("events", jdbc.queryForList(
                "SELECT * FROM global_events WHERE trace_id = ? ORDER BY ts ASC", traceId));
        return map;
    }

    public Map<String, Object> incident(String eventId, long windowMs) {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> seeds = jdbc.queryForList("SELECT * FROM global_events WHERE event_id = ?", eventId);
        map.put("found", !seeds.isEmpty());
        if (seeds.isEmpty()) {
            return map;
        }
        Map<String, Object> seed = seeds.getFirst();
        long ts = ((Number) seed.get("ts")).longValue();
        String actor = (String) seed.get("actor_uuid");
        map.put("seed", seed);
        map.put("events", jdbc.queryForList(
                "SELECT * FROM global_events WHERE ts BETWEEN ? AND ? AND (? IS NULL OR actor_uuid = ?) ORDER BY ts ASC LIMIT 1000",
                ts - windowMs, ts + windowMs, actor, actor));
        if (actor != null) {
            map.put("movements", jdbc.queryForList(
                    "SELECT * FROM player_movements WHERE player_uuid = ? AND ts BETWEEN ? AND ? ORDER BY ts ASC LIMIT 2000",
                    actor, ts - windowMs, ts + windowMs));
        } else {
            map.put("movements", List.of());
        }
        String traceId = (String) seed.get("trace_id");
        if (traceId != null && !traceId.isBlank()) {
            map.put("item", itemTrace(traceId));
        }
        return map;
    }

    public List<Map<String, Object>> alerts(boolean onlyOpen) {
        if (onlyOpen) {
            return jdbc.queryForList(
                    "SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY ts DESC LIMIT 200");
        }
        return jdbc.queryForList("SELECT * FROM alerts ORDER BY ts DESC LIMIT 200");
    }

    public boolean acknowledge(String alertId) {
        return jdbc.update("UPDATE alerts SET acknowledged = 1 WHERE alert_id = ?", alertId) > 0;
    }

    public List<Map<String, Object>> movements(String player, long from, long to) {
        return jdbc.queryForList(
                """
                SELECT * FROM player_movements
                WHERE (player_uuid = ? OR player_name = ?)
                  AND (? <= 0 OR ts >= ?)
                  AND (? <= 0 OR ts <= ?)
                ORDER BY ts ASC
                LIMIT 5000
                """,
                player, player, from, from, to, to);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    public void ensureSnapshotTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS online_players (
                    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, ping INTEGER NOT NULL DEFAULT 0,
                    dimension TEXT, x REAL, y REAL, z REAL, game_mode TEXT, updated_at INTEGER NOT NULL)
                """);
        ensureColumn("online_players", "health", "REAL");
        ensureColumn("online_players", "food", "INTEGER");
        ensureColumn("online_players", "xp_level", "INTEGER");
        ensureColumn("online_players", "is_op", "INTEGER NOT NULL DEFAULT 0");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_bans (
                    uuid TEXT PRIMARY KEY, name TEXT, reason TEXT, source TEXT,
                    created_at INTEGER, expires_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_whitelist (
                    uuid TEXT PRIMARY KEY, name TEXT NOT NULL, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_runtime (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    whitelist_enabled INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)
                """);
        for (String col : List.of(
                "player_count INTEGER NOT NULL DEFAULT 0",
                "max_players INTEGER NOT NULL DEFAULT 0",
                "mspt_ms REAL NOT NULL DEFAULT 0",
                "tps_approx REAL NOT NULL DEFAULT 20",
                "entity_count INTEGER NOT NULL DEFAULT 0",
                "chunk_count INTEGER NOT NULL DEFAULT 0",
                "world_count INTEGER NOT NULL DEFAULT 0",
                "memory_used_mb INTEGER NOT NULL DEFAULT 0",
                "memory_max_mb INTEGER NOT NULL DEFAULT 0",
                "game_port INTEGER NOT NULL DEFAULT 0",
                "online_mode INTEGER NOT NULL DEFAULT 1",
                "motd TEXT",
                "difficulty TEXT",
                "hardcore INTEGER NOT NULL DEFAULT 0",
                "default_gamemode TEXT",
                "day_time INTEGER NOT NULL DEFAULT 0",
                "raining INTEGER NOT NULL DEFAULT 0",
                "thundering INTEGER NOT NULL DEFAULT 0",
                "border_size REAL NOT NULL DEFAULT 0",
                "border_center_x REAL NOT NULL DEFAULT 0",
                "border_center_z REAL NOT NULL DEFAULT 0",
                "border_warning INTEGER NOT NULL DEFAULT 0",
                "border_damage REAL NOT NULL DEFAULT 0",
                "uptime_ticks INTEGER NOT NULL DEFAULT 0",
                "mc_version TEXT",
                "neoforge_version TEXT",
                "mod_version TEXT"
        )) {
            int sp = col.indexOf(' ');
            ensureColumn("server_runtime", col.substring(0, sp), col.substring(sp + 1));
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_gamerules (
                    rule_id TEXT PRIMARY KEY, category TEXT, value_type TEXT NOT NULL,
                    value TEXT NOT NULL, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_dimensions (
                    dimension TEXT PRIMARY KEY, player_count INTEGER NOT NULL DEFAULT 0,
                    entity_count INTEGER NOT NULL DEFAULT 0, chunk_count INTEGER NOT NULL DEFAULT 0,
                    day_time INTEGER NOT NULL DEFAULT 0, raining INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL)
                """);
        for (String col : List.of(
                "spawn_x REAL", "spawn_y REAL", "spawn_z REAL", "spawn_angle REAL",
                "sudo_session_minutes INTEGER", "max_failed_attempts INTEGER", "lock_minutes INTEGER",
                "protected_commands TEXT", "audit_retention_days INTEGER"
        )) {
            int sp = col.indexOf(' ');
            ensureColumn("server_runtime", col.substring(0, sp), col.substring(sp + 1));
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_bossbars (
                    id TEXT PRIMARY KEY, name TEXT, color TEXT, overlay TEXT,
                    value INTEGER NOT NULL DEFAULT 0, max_value INTEGER NOT NULL DEFAULT 100,
                    visible INTEGER NOT NULL DEFAULT 1, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_scoreboard (
                    name TEXT PRIMARY KEY, criteria TEXT, display_name TEXT,
                    display_slot TEXT, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_teams (
                    name TEXT PRIMARY KEY, display_name TEXT, color TEXT,
                    friendly_fire INTEGER NOT NULL DEFAULT 1,
                    see_friendly_invisibles INTEGER NOT NULL DEFAULT 1,
                    members TEXT, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_filter_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL UNIQUE,
                    enabled INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_ip_bans (
                    ip TEXT PRIMARY KEY, reason TEXT, source TEXT,
                    created_at INTEGER, expires_at INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS server_entity_types (
                    entity_type TEXT PRIMARY KEY, count INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS panel_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    interval_seconds INTEGER NOT NULL DEFAULT 0,
                    next_run_at INTEGER NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    last_run_at INTEGER,
                    created_at INTEGER NOT NULL,
                    note TEXT)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS chat_mutes (
                    key TEXT PRIMARY KEY, name TEXT, reason TEXT,
                    until_ts INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)
                """);
        ensureColumn("server_runtime", "idle_timeout", "INTEGER");
        ensureColumn("server_runtime", "maintenance", "INTEGER NOT NULL DEFAULT 0");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS perf_samples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    tps REAL NOT NULL,
                    mspt_ms REAL NOT NULL,
                    memory_used_mb INTEGER NOT NULL DEFAULT 0,
                    entity_count INTEGER NOT NULL DEFAULT 0,
                    player_count INTEGER NOT NULL DEFAULT 0)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS player_inventory (
                    uuid TEXT NOT NULL,
                    name TEXT,
                    section TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER NOT NULL DEFAULT 1,
                    display_name TEXT,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, section, slot))
                """);
    }

    private void ensureColumn(String table, String column, String definition) {
        try {
            Integer n = jdbc.query(
                    "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?",
                    rs -> rs.next() ? rs.getInt(1) : 0,
                    table, column);
            if (n == null || n == 0) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (Exception ignored) {
            try {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            } catch (Exception ignored2) {
                // already exists or unsupported
            }
        }
    }

    public List<Map<String, Object>> onlinePlayers() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                """
                SELECT uuid, name, ping, dimension, x, y, z, game_mode, updated_at,
                       health, food, xp_level, is_op
                FROM online_players ORDER BY name
                """);
    }

    public Map<String, Object> runtimeSnapshot() {
        ensureSnapshotTables();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM server_runtime WHERE id = 1");
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    public Map<String, Object> statusOverview(long alertsOpen) {
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> rt = runtimeSnapshot();
        map.put("runtime", rt);
        map.put("alertsOpen", alertsOpen);
        map.put("online", onlinePlayers());
        map.put("updatedAt", rt.getOrDefault("updated_at", 0));
        return map;
    }

    public List<Map<String, Object>> gamerules() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                "SELECT rule_id, category, value_type, value, updated_at FROM server_gamerules ORDER BY category, rule_id");
    }

    public List<Map<String, Object>> dimensions() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                """
                SELECT dimension, player_count, entity_count, chunk_count, day_time, raining, updated_at
                FROM server_dimensions ORDER BY dimension
                """);
    }

    public List<Map<String, Object>> recentPanelActions(int limit, String... actions) {
        ensurePanelActionsTable();
        if (actions == null || actions.length == 0) {
            return jdbc.queryForList(
                    """
                    SELECT id, action, target_uuid, target_name, payload, status, result, created_at, processed_at
                    FROM panel_actions ORDER BY id DESC LIMIT ?
                    """,
                    Math.max(1, Math.min(limit, 200)));
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(actions.length, "?"));
        var args = new java.util.ArrayList<Object>();
        args.addAll(List.of(actions));
        args.add(Math.max(1, Math.min(limit, 200)));
        return jdbc.queryForList(
                "SELECT id, action, target_uuid, target_name, payload, status, result, created_at, processed_at "
                        + "FROM panel_actions WHERE action IN (" + placeholders + ") ORDER BY id DESC LIMIT ?",
                args.toArray());
    }

    public boolean enqueuePayload(String action, String playerName, String uuid, String payload) {
        return enqueuePayload(action, playerName, uuid, payload, "panel", null);
    }

    /** @return true if queued for execution or approval */
    public boolean enqueuePayload(String action, String playerName, String uuid, String payload, String requester, String reason) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String name = playerName == null ? null : playerName.trim();
        String id = uuid == null ? null : uuid.trim();
        if (id != null && !id.isBlank() && !validUuid(id)) {
            return false;
        }
        String body = payload == null ? null : payload.trim();
        if (body != null && body.length() > 2000) {
            body = body.substring(0, 2000);
        }
        ensurePanelActionsTable();
        String result = governance.enqueueOrApprove(action.trim(), name, id, body, requester, reason);
        if ("fail".equals(result)) {
            return false;
        }
        writePanelAudit(id, "panel_" + action.trim() + "_" + result,
                (name == null || name.isBlank() ? id : name) + (body == null ? "" : (" | " + body)), true);
        return true;
    }

    public boolean enqueuePayloadDirect(String action, String playerName, String uuid, String payload) {
        if (action == null || action.isBlank()) {
            return false;
        }
        String name = playerName == null ? null : playerName.trim();
        String id = uuid == null ? null : uuid.trim();
        if (id != null && !id.isBlank() && !validUuid(id)) {
            return false;
        }
        String body = payload == null ? null : payload.trim();
        if (body != null && body.length() > 2000) {
            body = body.substring(0, 2000);
        }
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES (?, ?, ?, ?, 'pending', ?)
                """,
                action.trim(),
                (id == null || id.isBlank()) ? null : id,
                (name == null || name.isBlank()) ? null : name,
                body,
                System.currentTimeMillis());
        writePanelAudit(id, "panel_" + action.trim() + "_enqueue",
                (name == null || name.isBlank() ? id : name) + (body == null ? "" : (" | " + body)), true);
        return true;
    }

    public boolean enqueueSetGamerule(String ruleId, String value) {
        if (ruleId == null || ruleId.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        return enqueuePayload("set_gamerule", null, null, ruleId.trim() + "|" + value.trim());
    }

    public Map<String, Object> playerProfile(String q) {
        Map<String, Object> map = new HashMap<>();
        map.put("q", q);
        if (q == null || q.isBlank()) {
            map.put("onlineHit", null);
            map.put("userHit", null);
            map.put("muteHit", null);
            map.put("banHit", null);
            map.put("inventory", List.of());
            return map;
        }
        ensureSnapshotTables();
        String query = q.trim();
        List<Map<String, Object>> online = jdbc.queryForList(
                "SELECT * FROM online_players WHERE lower(name) = lower(?) OR uuid = ? LIMIT 1", query, query);
        map.put("onlineHit", online.isEmpty() ? null : online.getFirst());
        List<Map<String, Object>> users = jdbc.queryForList(
                """
                SELECT uuid, name, role, op_bound, created_at, updated_at, failed_attempts, locked_until
                FROM users WHERE lower(name) = lower(?) OR uuid = ? LIMIT 1
                """,
                query, query);
        map.put("userHit", users.isEmpty() ? null : users.getFirst());
        String uuidKey = online.isEmpty() ? query : String.valueOf(online.getFirst().get("uuid"));
        String nameKey = online.isEmpty() ? query : String.valueOf(online.getFirst().get("name"));
        List<Map<String, Object>> mutes = jdbc.queryForList(
                """
                SELECT key, name, reason, until_ts, created_at FROM chat_mutes
                WHERE key = ? OR key = ? OR lower(name) = lower(?)
                LIMIT 1
                """,
                uuidKey, "name:" + nameKey.toLowerCase(), nameKey);
        map.put("muteHit", mutes.isEmpty() ? null : mutes.getFirst());
        List<Map<String, Object>> bans = jdbc.queryForList(
                """
                SELECT uuid, name, reason, source, created_at, expires_at, updated_at FROM server_bans
                WHERE lower(name) = lower(?) OR uuid = ? LIMIT 1
                """,
                query, query);
        map.put("banHit", bans.isEmpty() ? null : bans.getFirst());
        map.put("inventory", playerInventory(query));
        return map;
    }

    public List<Map<String, Object>> perfSamples(int limit) {
        ensureSnapshotTables();
        int lim = Math.max(10, Math.min(limit, 1800));
        return jdbc.queryForList(
                """
                SELECT ts, tps, mspt_ms, memory_used_mb, entity_count, player_count
                FROM perf_samples ORDER BY ts DESC LIMIT ?
                """,
                lim);
    }

    public List<Map<String, Object>> perfSamplesAsc(int limit) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>(perfSamples(limit));
        java.util.Collections.reverse(rows);
        for (Map<String, Object> row : rows) {
            Object tpsObj = row.get("tps");
            double tps = tpsObj instanceof Number n ? n.doubleValue() : 0;
            int pct = (int) Math.round(Math.max(0, Math.min(20, tps)) / 20.0 * 100);
            row.put("tps_pct", pct);
            Object msptObj = row.get("mspt_ms");
            double mspt = msptObj instanceof Number n ? n.doubleValue() : 0;
            int msptPct = (int) Math.round(Math.max(0, Math.min(100, mspt / 50.0 * 100)));
            row.put("mspt_pct", msptPct);
        }
        return rows;
    }

    public List<Map<String, Object>> playerInventory(String playerOrUuid) {
        return playerInventory(playerOrUuid, null);
    }

    public List<Map<String, Object>> playerInventory(String playerOrUuid, String section) {
        ensureSnapshotTables();
        if (playerOrUuid == null || playerOrUuid.isBlank()) {
            return List.of();
        }
        String q = playerOrUuid.trim();
        if (section != null && !section.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT uuid, name, section, slot, item_id, count, display_name, updated_at
                    FROM player_inventory
                    WHERE (uuid = ? OR lower(name) = lower(?)) AND section = ?
                    ORDER BY slot
                    """,
                    q, q, section.trim().toLowerCase());
        }
        return jdbc.queryForList(
                """
                SELECT uuid, name, section, slot, item_id, count, display_name, updated_at
                FROM player_inventory
                WHERE uuid = ? OR lower(name) = lower(?)
                ORDER BY
                  CASE section
                    WHEN 'armor' THEN 0
                    WHEN 'offhand' THEN 1
                    WHEN 'main' THEN 2
                    WHEN 'ender' THEN 3
                    ELSE 4
                  END,
                  slot
                """,
                q, q);
    }

    public List<Map<String, Object>> bossbars() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                "SELECT id, name, color, overlay, value, max_value, visible, updated_at FROM server_bossbars ORDER BY id");
    }

    public List<Map<String, Object>> scoreboardObjectives() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                "SELECT name, criteria, display_name, display_slot, updated_at FROM server_scoreboard ORDER BY name");
    }

    public List<Map<String, Object>> teams() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                """
                SELECT name, display_name, color, friendly_fire, see_friendly_invisibles, members, updated_at
                FROM server_teams ORDER BY name
                """);
    }

    public List<Map<String, Object>> chatFilterWords() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                "SELECT id, word, enabled, created_at FROM chat_filter_words ORDER BY id DESC LIMIT 500");
    }

    public boolean addChatFilterWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }
        String w = word.trim();
        if (w.length() > 64) {
            w = w.substring(0, 64);
        }
        ensureSnapshotTables();
        try {
            jdbc.update(
                    "INSERT INTO chat_filter_words (word, enabled, created_at) VALUES (?, 1, ?)",
                    w, System.currentTimeMillis());
            writePanelAudit(null, "panel_chat_filter_add", w, true);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean deleteChatFilterWord(long id) {
        ensureSnapshotTables();
        int n = jdbc.update("DELETE FROM chat_filter_words WHERE id = ?", id);
        if (n > 0) {
            writePanelAudit(null, "panel_chat_filter_delete", String.valueOf(id), true);
        }
        return n > 0;
    }

    public boolean toggleChatFilterWord(long id, boolean enabled) {
        ensureSnapshotTables();
        int n = jdbc.update("UPDATE chat_filter_words SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
        return n > 0;
    }

    public Map<String, Object> playersPage() {
        ensurePanelActionsTable();
        ensureSnapshotTables();
        Map<String, Object> map = new HashMap<>();
        map.put("online", onlinePlayers());
        map.put("users", usersSummary());
        map.put("recentModeration", jdbc.queryForList(
                """
                SELECT id, action, target_uuid, target_name, payload, status, result, created_at, processed_at
                FROM panel_actions
                WHERE action IN ('kick_player', 'ban_player', 'temp_ban_player', 'unban_player')
                ORDER BY id DESC LIMIT 40
                """));
        return map;
    }

    public Map<String, Object> bansPage() {
        ensureSnapshotTables();
        Map<String, Object> map = new HashMap<>();
        long now = System.currentTimeMillis();
        map.put("bans", jdbc.queryForList(
                "SELECT uuid, name, reason, source, created_at, expires_at, updated_at FROM server_bans ORDER BY created_at DESC"));
        map.put("ipBans", jdbc.queryForList(
                "SELECT ip, reason, source, created_at, expires_at, updated_at FROM server_ip_bans ORDER BY created_at DESC"));
        map.put("now", now);
        return map;
    }

    public Map<String, Object> whitelistPage() {
        ensureSnapshotTables();
        Map<String, Object> map = new HashMap<>();
        map.put("entries", jdbc.queryForList("SELECT uuid, name, updated_at FROM server_whitelist ORDER BY name"));
        List<Map<String, Object>> runtime = jdbc.queryForList(
                "SELECT whitelist_enabled, updated_at FROM server_runtime WHERE id = 1");
        map.put("enabled", runtime.isEmpty() ? 0 : runtime.getFirst().get("whitelist_enabled"));
        return map;
    }

    public boolean enqueueTempBan(String playerName, String uuid, int minutes, String reason) {
        minutes = Math.max(1, Math.min(minutes, 60 * 24 * 365));
        String payload = minutes + "|" + (reason == null ? "" : reason.trim());
        return enqueueModerationAction("temp_ban_player", playerName, uuid, payload);
    }

    public boolean enqueueBroadcast(String message) {
        return enqueueBroadcast(message, "[公告]", 1);
    }

    public boolean enqueueBroadcast(String message, String prefix, int times) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String msg = message.trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        String pfx = prefix == null || prefix.isBlank() ? "[公告]" : prefix.trim();
        if (pfx.length() > 40) {
            pfx = pfx.substring(0, 40);
        }
        times = Math.max(1, Math.min(times, 10));
        String payload = "__v2|" + pfx + "|" + times + "|" + msg;
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES ('broadcast', NULL, NULL, ?, 'pending', ?)
                """,
                payload, System.currentTimeMillis());
        writePanelAudit(null, "panel_broadcast_enqueue", payload, true);
        return true;
    }

    public List<Map<String, Object>> entityTypes() {
        ensureSnapshotTables();
        return jdbc.queryForList(
                "SELECT entity_type, count, updated_at FROM server_entity_types ORDER BY count DESC LIMIT 80");
    }

    public void ensureScheduleTable() {
        ensureSnapshotTables();
    }

    public List<Map<String, Object>> listSchedules(int limit) {
        ensureScheduleTable();
        return jdbc.queryForList(
                """
                SELECT id, kind, payload, interval_seconds, next_run_at, enabled, last_run_at, created_at, note
                FROM panel_schedules ORDER BY enabled DESC, next_run_at ASC LIMIT ?
                """,
                Math.max(1, Math.min(limit, 200)));
    }

    public boolean createBroadcastSchedule(String message, String prefix, int times, int delaySeconds, int intervalSeconds, String note) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String msg = message.trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        String pfx = prefix == null || prefix.isBlank() ? "[公告]" : prefix.trim();
        times = Math.max(1, Math.min(times, 10));
        delaySeconds = Math.max(0, Math.min(delaySeconds, 86400 * 7));
        intervalSeconds = Math.max(0, Math.min(intervalSeconds, 86400 * 7));
        String payload = "__v2|" + pfx + "|" + times + "|" + msg;
        long now = System.currentTimeMillis();
        ensureScheduleTable();
        jdbc.update(
                """
                INSERT INTO panel_schedules (kind, payload, interval_seconds, next_run_at, enabled, created_at, note)
                VALUES ('broadcast', ?, ?, ?, 1, ?, ?)
                """,
                payload, intervalSeconds, now + delaySeconds * 1000L, now,
                note == null || note.isBlank() ? null : note.trim());
        writePanelAudit(null, "panel_schedule_create", payload, true);
        return true;
    }

    public boolean setScheduleEnabled(long id, boolean enabled) {
        ensureScheduleTable();
        int n = jdbc.update("UPDATE panel_schedules SET enabled = ? WHERE id = ?", enabled ? 1 : 0, id);
        return n > 0;
    }

    public boolean deleteSchedule(long id) {
        ensureScheduleTable();
        int n = jdbc.update("DELETE FROM panel_schedules WHERE id = ?", id);
        if (n > 0) {
            writePanelAudit(null, "panel_schedule_delete", String.valueOf(id), true);
        }
        return n > 0;
    }

    public List<Map<String, Object>> listMutes() {
        ensureSnapshotTables();
        long now = System.currentTimeMillis();
        jdbc.update("DELETE FROM chat_mutes WHERE until_ts > 0 AND until_ts < ?", now);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT key, name, reason, until_ts, created_at FROM chat_mutes ORDER BY created_at DESC LIMIT 200");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault());
        for (Map<String, Object> row : rows) {
            Object untilObj = row.get("until_ts");
            long until = untilObj instanceof Number n ? n.longValue() : 0L;
            row.put("until_label", until == 0 ? "永久" : fmt.format(java.time.Instant.ofEpochMilli(until)));
        }
        return rows;
    }

    public boolean mutePlayer(String player, String uuid, int minutes, String reason) {
        String name = player == null ? null : player.trim();
        String id = uuid == null ? null : uuid.trim();
        if ((name == null || name.isBlank()) && (id == null || id.isBlank())) {
            return false;
        }
        String key = (id != null && !id.isBlank()) ? id : ("name:" + name.toLowerCase());
        minutes = Math.max(0, Math.min(minutes, 60 * 24 * 30));
        long until = minutes == 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L;
        String why = reason == null || reason.isBlank() ? "muted by panel" : reason.trim();
        if (why.length() > 200) {
            why = why.substring(0, 200);
        }
        ensureSnapshotTables();
        jdbc.update(
                """
                INSERT INTO chat_mutes (key, name, reason, until_ts, created_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET name = excluded.name, reason = excluded.reason,
                    until_ts = excluded.until_ts, created_at = excluded.created_at
                """,
                key, name, why, until, System.currentTimeMillis());
        writePanelAudit(id, "panel_mute", name + "|" + minutes + "|" + why, true);
        return true;
    }

    public boolean unmutePlayer(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        ensureSnapshotTables();
        int n = jdbc.update("DELETE FROM chat_mutes WHERE key = ?", key.trim());
        if (n > 0) {
            writePanelAudit(null, "panel_unmute", key, true);
        }
        return n > 0;
    }

    public boolean enqueueTell(String playerName, String uuid, String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String msg = message.trim();
        if (msg.length() > 500) {
            msg = msg.substring(0, 500);
        }
        return enqueueModerationAction("tell_player", playerName, uuid, msg);
    }

    public boolean enqueueWhitelistAdd(String playerName, String uuid) {
        return enqueueModerationAction("whitelist_add", playerName, uuid, null);
    }

    public boolean enqueueWhitelistRemove(String playerName, String uuid) {
        return enqueueModerationAction("whitelist_remove", playerName, uuid, null);
    }

    public boolean enqueueWhitelistSet(boolean enabled) {
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES ('whitelist_set', NULL, NULL, ?, 'pending', ?)
                """,
                enabled ? "on" : "off", System.currentTimeMillis());
        writePanelAudit(null, "panel_whitelist_set_enqueue", String.valueOf(enabled), true);
        return true;
    }

    public boolean enqueueRetentionCleanup(Integer days) {
        ensurePanelActionsTable();
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES ('retention_cleanup', NULL, NULL, ?, 'pending', ?)
                """,
                days == null ? null : String.valueOf(days), System.currentTimeMillis());
        writePanelAudit(null, "panel_retention_enqueue", String.valueOf(days), true);
        return true;
    }

    public String exportAuditCsv(String action, String uuid, Boolean success, int limit) {
        List<Map<String, Object>> rows = auditLogs(action, uuid, success, limit);
        StringBuilder sb = new StringBuilder("id,uuid,action,detail,success,ts\n");
        for (Map<String, Object> row : rows) {
            sb.append(csv(row.get("id"))).append(',')
                    .append(csv(row.get("uuid"))).append(',')
                    .append(csv(row.get("action"))).append(',')
                    .append(csv(row.get("detail"))).append(',')
                    .append(csv(row.get("success"))).append(',')
                    .append(csv(row.get("ts"))).append('\n');
        }
        return sb.toString();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value).replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    public void completeSetup(String policy) {
        ensureSnapshotTables();
        try {
            jdbc.update("UPDATE server_runtime SET setup_complete = 1 WHERE id = 1");
        } catch (Exception ignored) {
            // ignore
        }
        String p = policy == null ? "推荐" : policy.trim();
        // Seed a note schedule for strict policy reminder
        if ("严格".equals(p)) {
            createOpsSchedule("restart_hint", "严格策略：请启用 TOTP 并检查审批开关", 60, 0, "setup");
        }
        writePanelAudit(null, "panel_setup_complete", "policy=" + p, true);
    }

    public boolean createOpsSchedule(String kind, String payload, int delaySeconds, int intervalSeconds, String note) {
        if (kind == null || kind.isBlank()) {
            return false;
        }
        String k = kind.trim().toLowerCase();
        if (!List.of("broadcast", "save_all", "restart_hint", "kill_entities", "create_security_snapshot",
                "maintenance_kick", "lockdown_on").contains(k)) {
            return false;
        }
        delaySeconds = Math.max(0, Math.min(delaySeconds, 86400 * 30));
        intervalSeconds = Math.max(0, Math.min(intervalSeconds, 86400 * 30));
        long now = System.currentTimeMillis();
        ensureScheduleTable();
        String body = payload == null ? "" : payload.trim();
        if ("broadcast".equals(k) && !body.startsWith("__v2|")) {
            body = "__v2|[调度]|1|" + body;
        }
        jdbc.update(
                """
                INSERT INTO panel_schedules (kind, payload, interval_seconds, next_run_at, enabled, created_at, note)
                VALUES (?, ?, ?, ?, 1, ?, ?)
                """,
                k, body, intervalSeconds, now + delaySeconds * 1000L, now,
                note == null || note.isBlank() ? null : note.trim());
        writePanelAudit(null, "panel_schedule_create", k + "|" + body, true);
        return true;
    }

    public List<Map<String, Object>> perfSamplesSince(int hours) {
        ensureSnapshotTables();
        long since = System.currentTimeMillis() - Math.max(1, hours) * 3600_000L;
        return jdbc.queryForList(
                """
                SELECT ts, tps, mspt_ms, memory_used_mb, entity_count, player_count, chunk_count, entity_share_hint
                FROM perf_samples WHERE ts >= ? ORDER BY ts ASC LIMIT 5000
                """,
                since);
    }

    public Map<String, Object> simpleOnlineForecast() {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> week = perfSamplesSince(168);
        if (week.size() < 10) {
            map.put("ready", false);
            map.put("message", "样本不足，至少运行数小时后再看预测");
            return map;
        }
        // Peak player_count by hour-of-day average
        int[] sums = new int[24];
        int[] counts = new int[24];
        for (Map<String, Object> row : week) {
            long ts = ((Number) row.get("ts")).longValue();
            int hour = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).getHour();
            int players = row.get("player_count") instanceof Number n ? n.intValue() : 0;
            sums[hour] += players;
            counts[hour]++;
        }
        int bestHour = 0;
        double bestAvg = -1;
        for (int h = 0; h < 24; h++) {
            if (counts[h] == 0) {
                continue;
            }
            double avg = (double) sums[h] / counts[h];
            if (avg > bestAvg) {
                bestAvg = avg;
                bestHour = h;
            }
        }
        map.put("ready", true);
        map.put("peakHour", bestHour);
        map.put("peakAvg", Math.round(bestAvg * 10) / 10.0);
        map.put("message", "根据近 7 天数据，预计每天约 " + bestHour + ":00 在线接近峰值（均 " + map.get("peakAvg") + " 人），建议提前优化实体/区块。");
        return map;
    }
}
