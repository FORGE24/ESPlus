package com.esplus.panel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.esplus.panel.security.TotpService;

@Service
public class PanelGovernanceService {
    private static final String GENESIS = "GENESIS";

    private final JdbcTemplate jdbc;
    private final boolean approvalEnabled;
    private final int approvalGiveThreshold;
    private final Set<String> approvalActions;
    private final String serverId;
    private final String serverName;
    private final String webhookUrl;
    private final boolean auditHashChain;

    public PanelGovernanceService(
            JdbcTemplate jdbc,
            @Value("${esplus.approvalEnabled:true}") boolean approvalEnabled,
            @Value("${esplus.approvalGiveThreshold:64}") int approvalGiveThreshold,
            @Value("${esplus.approvalRequiredActions:give_item,set_gamerule,console_cmd,kill_entities,stop_server,restore_snapshot}") String approvalActionsCsv,
            @Value("${esplus.serverId:local}") String serverId,
            @Value("${esplus.serverName:ES+}") String serverName,
            @Value("${esplus.alertWebhookUrl:}") String webhookUrl,
            @Value("${esplus.auditHashChain:true}") boolean auditHashChain
    ) {
        this.jdbc = jdbc;
        this.approvalEnabled = approvalEnabled;
        this.approvalGiveThreshold = approvalGiveThreshold;
        this.approvalActions = parseCsv(approvalActionsCsv);
        this.serverId = serverId;
        this.serverName = serverName;
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
        this.auditHashChain = auditHashChain;
    }

    public boolean approvalEnabled() {
        return approvalEnabled;
    }

    public String serverId() {
        return serverId;
    }

    public String serverName() {
        return serverName;
    }

    public Map<String, Object> enrollPlayerTotp(String uuid) {
        Map<String, Object> map = new HashMap<>();
        if (uuid == null || uuid.isBlank()) {
            map.put("ok", false);
            return map;
        }
        String secret = TotpService.generateSecret();
        jdbc.update(
                """
                INSERT INTO user_mfa (uuid, secret, enabled, updated_at) VALUES (?, ?, 0, ?)
                ON CONFLICT(uuid) DO UPDATE SET secret=excluded.secret, enabled=0, updated_at=excluded.updated_at
                """,
                uuid.trim(), secret, System.currentTimeMillis());
        map.put("ok", true);
        map.put("secret", secret);
        map.put("uri", TotpService.otpAuthUri("ESPlus-Sudo", uuid.trim(), secret));
        return map;
    }

    public boolean enablePlayerTotp(String uuid, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT secret FROM user_mfa WHERE uuid = ?", uuid.trim());
        if (rows.isEmpty()) {
            return false;
        }
        if (!TotpService.verify(String.valueOf(rows.getFirst().get("secret")), code)) {
            return false;
        }
        jdbc.update("UPDATE user_mfa SET enabled = 1, updated_at = ? WHERE uuid = ?", System.currentTimeMillis(), uuid.trim());
        return true;
    }

    /** @return null if enqueued directly; "approval" if queued for approval; "fail" on error */
    public String enqueueOrApprove(
            String action,
            String playerName,
            String uuid,
            String payload,
            String requester,
            String reason
    ) {
        if (action == null || action.isBlank()) {
            return "fail";
        }
        if (needsApproval(action, payload)) {
            return createApproval(requester, action, uuid, playerName, payload, reason) ? "approval" : "fail";
        }
        return enqueueDirect(action, playerName, uuid, payload) ? "ok" : "fail";
    }

    public boolean needsApproval(String action, String payload) {
        if (!approvalEnabled) {
            return false;
        }
        String a = action.trim().toLowerCase(Locale.ROOT);
        if (!approvalActions.contains(a)) {
            return false;
        }
        if ("give_item".equals(a)) {
            int count = parseGiveCount(payload);
            return count >= approvalGiveThreshold;
        }
        return true;
    }

    public boolean createApproval(
            String requester,
            String actionType,
            String targetUuid,
            String targetName,
            String payload,
            String reason
    ) {
        ensureApprovalTable();
        jdbc.update(
                """
                INSERT INTO approval_requests
                (requester, action_type, target_uuid, target_name, payload, reason, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'pending', ?)
                """,
                requester == null || requester.isBlank() ? "panel" : requester,
                actionType,
                blankToNull(targetUuid),
                blankToNull(targetName),
                payload,
                reason == null || reason.isBlank() ? "面板高危操作" : reason,
                System.currentTimeMillis());
        return true;
    }

    public List<Map<String, Object>> listApprovals(String status, int limit) {
        ensureApprovalTable();
        if (status != null && !status.isBlank()) {
            return jdbc.queryForList(
                    """
                    SELECT id, requester, action_type, target_uuid, target_name, payload, reason, status,
                           reviewer, created_at, decided_at, panel_action_id, result
                    FROM approval_requests WHERE status = ? ORDER BY id DESC LIMIT ?
                    """,
                    status, clamp(limit));
        }
        return jdbc.queryForList(
                """
                SELECT id, requester, action_type, target_uuid, target_name, payload, reason, status,
                       reviewer, created_at, decided_at, panel_action_id, result
                FROM approval_requests ORDER BY id DESC LIMIT ?
                """,
                clamp(limit));
    }

    public boolean decideApproval(long id, boolean approve, String reviewer) {
        ensureApprovalTable();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM approval_requests WHERE id = ? AND status = 'pending'", id);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.getFirst();
        long now = System.currentTimeMillis();
        if (!approve) {
            jdbc.update(
                    "UPDATE approval_requests SET status='rejected', reviewer=?, decided_at=?, result=? WHERE id=?",
                    reviewer, now, "rejected", id);
            return true;
        }
        boolean queued = enqueueDirect(
                String.valueOf(row.get("action_type")),
                row.get("target_name") == null ? null : String.valueOf(row.get("target_name")),
                row.get("target_uuid") == null ? null : String.valueOf(row.get("target_uuid")),
                row.get("payload") == null ? null : String.valueOf(row.get("payload")));
        jdbc.update(
                "UPDATE approval_requests SET status=?, reviewer=?, decided_at=?, result=? WHERE id=?",
                queued ? "approved" : "failed",
                reviewer,
                now,
                queued ? "enqueued" : "enqueue_failed",
                id);
        return queued;
    }

    public Map<String, Object> riskPage() {
        Map<String, Object> map = new HashMap<>();
        ensureRiskTable();
        recomputeRisk(7);
        map.put("scores", jdbc.queryForList(
                "SELECT actor_uuid, actor_name, window_days, score, factors_json, suggestion, updated_at FROM admin_risk_cache ORDER BY score DESC LIMIT 100"));
        map.put("pendingApprovals", queryLong("SELECT COUNT(*) FROM approval_requests WHERE status='pending'"));
        return map;
    }

    public void recomputeRisk(int windowDays) {
        ensureRiskTable();
        long since = System.currentTimeMillis() - windowDays * 86_400_000L;
        List<Map<String, Object>> events = jdbc.queryForList(
                """
                SELECT actor_uuid, actor_name, category, action, ts
                FROM global_events
                WHERE ts >= ? AND actor_uuid IS NOT NULL AND actor_uuid <> ''
                """,
                since);
        Map<String, int[]> agg = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Map<String, Object> e : events) {
            String uuid = String.valueOf(e.get("actor_uuid"));
            names.put(uuid, e.get("actor_name") == null ? uuid : String.valueOf(e.get("actor_name")));
            int[] a = agg.computeIfAbsent(uuid, k -> new int[5]); // sudo,give,gamerule,night,total
            String action = String.valueOf(e.get("action")).toLowerCase(Locale.ROOT);
            String cat = String.valueOf(e.get("category"));
            a[4]++;
            if (action.contains("sudo") || "security".equals(cat)) {
                a[0]++;
            }
            if (action.contains("give")) {
                a[1]++;
            }
            if (action.contains("gamerule")) {
                a[2]++;
            }
            long ts = ((Number) e.get("ts")).longValue();
            int hour = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).getHour();
            if (hour >= 0 && hour < 5) {
                a[3]++;
            }
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, int[]> entry : agg.entrySet()) {
            int[] a = entry.getValue();
            int score = Math.min(100, Math.min(25, a[0] / 2) + Math.min(35, a[1] / 4)
                    + Math.min(20, a[2]) + Math.min(25, a[3] * 2) + (a[3] >= 10 && a[1] >= 50 ? 15 : 0));
            String factors = "{\"sudo\":" + a[0] + ",\"give\":" + a[1] + ",\"gamerule\":" + a[2]
                    + ",\"night\":" + a[3] + ",\"total\":" + a[4] + "}";
            String suggestion;
            if (a[3] >= 8 && a[1] >= 30) {
                suggestion = "异常行为：凌晨大量给予物品，建议检查";
            } else if (a[1] >= 100) {
                suggestion = "给予次数偏高，建议核对活动/审批记录";
            } else if (score >= 70) {
                suggestion = "综合风险偏高，建议 Owner 复核近期操作";
            } else if (score >= 40) {
                suggestion = "中等风险，保持抽查即可";
            } else {
                suggestion = "风险较低";
            }
            jdbc.update(
                    """
                    INSERT INTO admin_risk_cache (actor_uuid, actor_name, window_days, score, factors_json, suggestion, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(actor_uuid) DO UPDATE SET
                      actor_name=excluded.actor_name, window_days=excluded.window_days, score=excluded.score,
                      factors_json=excluded.factors_json, suggestion=excluded.suggestion, updated_at=excluded.updated_at
                    """,
                    entry.getKey(), names.get(entry.getKey()), windowDays, score, factors, suggestion, now);
        }
    }

    public Map<String, Object> integrityPage() {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", auditHashChain);
        Map<String, Object> report = verifyHashChain(50_000);
        map.putAll(report);
        return map;
    }

    public Map<String, Object> verifyHashChain(int maxRows) {
        Map<String, Object> out = new HashMap<>();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                    """
                    SELECT event_id, ts, category, action, actor_uuid, actor_name, target_uuid, target_name,
                           dimension, x, y, z, item_id, trace_id, detail, source, event_hash, prev_hash
                    FROM global_events ORDER BY id ASC LIMIT ?
                    """,
                    Math.max(1, Math.min(maxRows, 200_000)));
        } catch (Exception ex) {
            out.put("ok", false);
            out.put("checked", 0);
            out.put("firstBreak", "query_failed:" + ex.getMessage());
            return out;
        }
        String expectedPrev = GENESIS;
        String firstBreak = null;
        String tip = null;
        int checked = 0;
        for (Map<String, Object> row : rows) {
            checked++;
            String prev = row.get("prev_hash") == null ? null : String.valueOf(row.get("prev_hash"));
            String hash = row.get("event_hash") == null ? null : String.valueOf(row.get("event_hash"));
            if (hash == null || hash.isBlank()) {
                if (firstBreak == null) {
                    firstBreak = row.get("event_id") + " (missing hash)";
                }
                continue;
            }
            if (prev == null || !prev.equals(expectedPrev)) {
                if (firstBreak == null) {
                    firstBreak = row.get("event_id") + " (prev mismatch)";
                }
            }
            String recomputed = sha256(canonical(row, prev == null ? GENESIS : prev));
            if (!hash.equalsIgnoreCase(recomputed) && firstBreak == null) {
                firstBreak = row.get("event_id") + " (hash mismatch)";
            }
            expectedPrev = hash;
            tip = hash;
        }
        String storedTip = null;
        try {
            List<Map<String, Object>> tipRows = jdbc.queryForList("SELECT audit_chain_tip FROM meta WHERE id = 1");
            if (!tipRows.isEmpty() && tipRows.getFirst().get("audit_chain_tip") != null) {
                storedTip = String.valueOf(tipRows.getFirst().get("audit_chain_tip"));
            }
        } catch (Exception ignored) {
            // older schema
        }
        boolean tipOk = tip == null || tip.equals(storedTip) || checked == 0;
        out.put("ok", firstBreak == null && tipOk);
        out.put("checked", checked);
        out.put("firstBreak", firstBreak);
        out.put("computedTip", tip);
        out.put("storedTip", storedTip);
        out.put("tipMatches", tipOk);
        try {
            out.put("signatures", jdbc.queryForList(
                    "SELECT id, ts, tip_hash, event_count, detail FROM audit_block_signatures ORDER BY id DESC LIMIT 20"));
        } catch (Exception ex) {
            out.put("signatures", List.of());
        }
        return out;
    }

    public Map<String, Object> centerPage() {
        Map<String, Object> map = new HashMap<>();
        map.put("serverId", serverId);
        map.put("serverName", serverName);
        try {
            map.put("runtime", jdbc.queryForMap("SELECT * FROM server_runtime WHERE id = 1"));
        } catch (Exception ex) {
            map.put("runtime", Map.of());
        }
        map.put("alertsOpen", queryLong("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0"));
        map.put("pendingApprovals", queryLong("SELECT COUNT(*) FROM approval_requests WHERE status='pending'"));
        try {
            map.put("topRisk", jdbc.queryForList(
                    "SELECT actor_name, score, suggestion FROM admin_risk_cache ORDER BY score DESC LIMIT 5"));
        } catch (Exception ex) {
            map.put("topRisk", List.of());
        }
        map.put("federationNote", "当前为单机 Center 视图。多服联邦需共享只读库/API，后续版本提供。");
        return map;
    }

    public List<Map<String, Object>> configHistory(int limit) {
        ensureConfigTable();
        return jdbc.queryForList(
                """
                SELECT id, ts, actor, scope, key AS config_key, old_value, new_value, snapshot_ref, detail
                FROM config_revisions ORDER BY id DESC LIMIT ?
                """,
                clamp(limit));
    }

    public boolean rollbackGamerule(long revisionId) {
        ensureConfigTable();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT scope, key AS config_key, old_value FROM config_revisions WHERE id = ?", revisionId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.getFirst();
        if (!"gamerule".equals(String.valueOf(row.get("scope")))) {
            return false;
        }
        String key = String.valueOf(row.get("config_key"));
        String old = row.get("old_value") == null ? "" : String.valueOf(row.get("old_value"));
        return enqueueDirect("set_gamerule", null, null, key + "|" + old);
    }

    public List<Map<String, Object>> snapshots(int limit) {
        ensureSnapshotTable();
        return jdbc.queryForList(
                "SELECT id, ts, actor, label, length(payload_json) AS payload_bytes FROM security_snapshots ORDER BY id DESC LIMIT ?",
                clamp(limit));
    }

    public boolean createSnapshot(String label, String actor) {
        return enqueueDirect("create_security_snapshot", null, null, label == null ? "manual" : label);
    }

    public boolean restoreSnapshot(long id) {
        String result = enqueueOrApprove("restore_snapshot", null, null, String.valueOf(id), "panel", "恢复安全快照 #" + id);
        return !"fail".equals(result);
    }

    public Map<String, Object> itemGraph(String traceId) {
        Map<String, Object> map = new HashMap<>();
        map.put("traceId", traceId);
        if (traceId == null || traceId.isBlank()) {
            map.put("found", false);
            return map;
        }
        List<Map<String, Object>> traces = jdbc.queryForList("SELECT * FROM item_traces WHERE trace_id = ?", traceId);
        if (traces.isEmpty()) {
            map.put("found", false);
            return map;
        }
        map.put("found", true);
        map.put("trace", traces.getFirst());
        List<Map<String, Object>> links = jdbc.queryForList(
                "SELECT * FROM item_trace_links WHERE trace_id = ? OR parent_trace_id = ? ORDER BY ts ASC LIMIT 200",
                traceId, traceId);
        map.put("links", links);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Map<String, Object> origin = traces.getFirst();
        nodes.add(Map.of(
                "id", "origin",
                "label", String.valueOf(origin.get("origin_actor_name")) + " / " + origin.get("origin_type"),
                "kind", "origin"));
        nodes.add(Map.of("id", traceId, "label", String.valueOf(origin.get("item_id")), "kind", "item"));
        edges.add(Map.of("from", "origin", "to", traceId, "label", "create"));
        int i = 0;
        for (Map<String, Object> link : links) {
            String nodeId = "n" + (i++);
            String actor = link.get("actor_name") == null ? "?" : String.valueOf(link.get("actor_name"));
            nodes.add(Map.of("id", nodeId, "label", actor + ":" + link.get("action"), "kind", "event"));
            String parent = link.get("parent_trace_id") == null ? traceId : String.valueOf(link.get("parent_trace_id"));
            edges.add(Map.of("from", parent, "to", nodeId, "label", String.valueOf(link.get("action"))));
        }
        map.put("nodes", nodes);
        map.put("edges", edges);
        List<Map<String, Object>> events = jdbc.queryForList(
                """
                SELECT ts, action, actor_name, x, y, z, dimension, detail
                FROM global_events WHERE trace_id = ? ORDER BY ts ASC LIMIT 100
                """,
                traceId);
        map.put("geoEvents", events);
        return map;
    }

    public List<Map<String, Object>> economyEvents(int limit) {
        return jdbc.queryForList(
                """
                SELECT event_id, ts, action, actor_name, detail, source
                FROM global_events WHERE category = 'economy' ORDER BY ts DESC LIMIT ?
                """,
                clamp(limit));
    }

    public List<Map<String, Object>> modSources(int limit) {
        return jdbc.queryForList(
                """
                SELECT COALESCE(source, 'unknown') AS source, COUNT(*) AS cnt, MAX(ts) AS last_ts
                FROM global_events GROUP BY COALESCE(source, 'unknown')
                ORDER BY cnt DESC LIMIT ?
                """,
                clamp(limit));
    }

    public List<Map<String, Object>> webhookLog(int limit) {
        try {
            return jdbc.queryForList(
                    "SELECT id, ts, severity, rule_code, http_status, ok, detail FROM webhook_delivery_log ORDER BY id DESC LIMIT ?",
                    clamp(limit));
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> webhookPage() {
        Map<String, Object> map = new HashMap<>();
        map.put("configured", webhookConfigured());
        map.put("urlHint", webhookConfigured() ? maskUrl(webhookUrl) : "(未配置)");
        map.put("deliveries", webhookLog(30));
        return map;
    }

    private boolean webhookConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }

    private boolean enqueueDirect(String action, String playerName, String uuid, String payload) {
        String body = payload == null ? null : payload.trim();
        if (body != null && body.length() > 4000) {
            body = body.substring(0, 4000);
        }
        jdbc.update(
                """
                INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                VALUES (?, ?, ?, ?, 'pending', ?)
                """,
                action.trim(),
                blankToNull(uuid),
                blankToNull(playerName),
                body,
                System.currentTimeMillis());
        return true;
    }

    private void ensureApprovalTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS approval_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    requester TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    target_uuid TEXT,
                    target_name TEXT,
                    payload TEXT,
                    reason TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    reviewer TEXT,
                    created_at INTEGER NOT NULL,
                    decided_at INTEGER,
                    panel_action_id INTEGER,
                    result TEXT
                )
                """);
    }

    private void ensureRiskTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_risk_cache (
                    actor_uuid TEXT PRIMARY KEY,
                    actor_name TEXT,
                    window_days INTEGER NOT NULL DEFAULT 7,
                    score INTEGER NOT NULL,
                    factors_json TEXT,
                    suggestion TEXT,
                    updated_at INTEGER NOT NULL
                )
                """);
    }

    private void ensureConfigTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS config_revisions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    actor TEXT,
                    scope TEXT NOT NULL,
                    key TEXT NOT NULL,
                    old_value TEXT,
                    new_value TEXT,
                    snapshot_ref TEXT,
                    detail TEXT
                )
                """);
    }

    private void ensureSnapshotTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS security_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    actor TEXT,
                    label TEXT NOT NULL,
                    payload_json TEXT NOT NULL
                )
                """);
    }

    private long queryLong(String sql, Object... args) {
        try {
            Long v = jdbc.queryForObject(sql, Long.class, args);
            return v == null ? 0L : v;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static int parseGiveCount(String payload) {
        if (payload == null || payload.isBlank()) {
            return 0;
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 2) {
            return 1;
        }
        try {
            return Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static Set<String> parseCsv(String csv) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (csv == null) {
            return set;
        }
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                set.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private static String maskUrl(String url) {
        if (url.length() < 16) {
            return "***";
        }
        return url.substring(0, 12) + "…" + url.substring(url.length() - 4);
    }

    private static String canonical(Map<String, Object> row, String prevHash) {
        return String.join("|",
                nullToEmpty(prevHash),
                nullToEmpty(row.get("event_id")),
                String.valueOf(row.get("ts")),
                nullToEmpty(row.get("category")),
                nullToEmpty(row.get("action")),
                nullToEmpty(row.get("actor_uuid")),
                nullToEmpty(row.get("actor_name")),
                nullToEmpty(row.get("target_uuid")),
                nullToEmpty(row.get("target_name")),
                nullToEmpty(row.get("dimension")),
                String.valueOf(row.get("x")),
                String.valueOf(row.get("y")),
                String.valueOf(row.get("z")),
                nullToEmpty(row.get("item_id")),
                nullToEmpty(row.get("trace_id")),
                nullToEmpty(row.get("detail")),
                nullToEmpty(row.get("source"))
        );
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
