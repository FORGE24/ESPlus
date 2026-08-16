package com.esplus.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.security.db.SqliteDatabase;

/**
 * UEBA-lite: aggregate admin/sudo related events into a 0-100 risk score.
 */
public final class AdminRiskScorer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRiskScorer.class);

    private final SqliteDatabase database;
    private final AlertDao alertDao;
    private final AlertWebhookDispatcher webhook;

    public AdminRiskScorer(SqliteDatabase database, AlertDao alertDao, AlertWebhookDispatcher webhook) {
        this.database = database;
        this.alertDao = alertDao;
        this.webhook = webhook;
    }

    public List<Map<String, Object>> recompute(int windowDays) {
        long since = System.currentTimeMillis() - windowDays * 86_400_000L;
        List<Map<String, Object>> rows = new ArrayList<>();
        List<AlertPending> pendingAlerts = new ArrayList<>();
        try {
            synchronized (database.lock()) {
                Map<String, Agg> aggs = new HashMap<>();
                try (PreparedStatement statement = database.connection().prepareStatement(
                        """
                        SELECT actor_uuid, actor_name, category, action, ts, detail
                        FROM global_events
                        WHERE ts >= ? AND actor_uuid IS NOT NULL AND actor_uuid <> ''
                        """)) {
                    statement.setLong(1, since);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            String uuid = rs.getString("actor_uuid");
                            Agg agg = aggs.computeIfAbsent(uuid, ignored -> new Agg());
                            agg.name = rs.getString("actor_name");
                            String cat = nullToEmpty(rs.getString("category"));
                            String action = nullToEmpty(rs.getString("action")).toLowerCase();
                            long ts = rs.getLong("ts");
                            agg.total++;
                            if (action.contains("sudo") || "security".equals(cat)) {
                                agg.sudo++;
                            }
                            if (action.contains("give") || "sudo_give".equals(action)) {
                                agg.give++;
                            }
                            if (action.contains("gamerule") || "set_gamerule".equals(action)) {
                                agg.gamerule++;
                            }
                            int hour = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).getHour();
                            if (hour >= 0 && hour < 5) {
                                agg.night++;
                            }
                        }
                    }
                }

                long now = System.currentTimeMillis();
                for (Map.Entry<String, Agg> entry : aggs.entrySet()) {
                    Agg a = entry.getValue();
                    int score = scoreOf(a);
                    String factors = "{\"sudo\":" + a.sudo + ",\"give\":" + a.give
                            + ",\"gamerule\":" + a.gamerule + ",\"night\":" + a.night
                            + ",\"total\":" + a.total + "}";
                    String suggestion = suggestionOf(a, score);
                    try (PreparedStatement upsert = database.connection().prepareStatement(
                            """
                            INSERT INTO admin_risk_cache (actor_uuid, actor_name, window_days, score, factors_json, suggestion, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT(actor_uuid) DO UPDATE SET
                              actor_name=excluded.actor_name,
                              window_days=excluded.window_days,
                              score=excluded.score,
                              factors_json=excluded.factors_json,
                              suggestion=excluded.suggestion,
                              updated_at=excluded.updated_at
                            """)) {
                        upsert.setString(1, entry.getKey());
                        upsert.setString(2, a.name);
                        upsert.setInt(3, windowDays);
                        upsert.setInt(4, score);
                        upsert.setString(5, factors);
                        upsert.setString(6, suggestion);
                        upsert.setLong(7, now);
                        upsert.executeUpdate();
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("actor_uuid", entry.getKey());
                    row.put("actor_name", a.name);
                    row.put("score", score);
                    row.put("factors_json", factors);
                    row.put("suggestion", suggestion);
                    row.put("sudo", a.sudo);
                    row.put("give", a.give);
                    row.put("gamerule", a.gamerule);
                    row.put("night", a.night);
                    row.put("total", a.total);
                    rows.add(row);

                    if (score >= com.esplus.audit.GlobalEventDao.configInt(Config.ADMIN_RISK_ALERT_SCORE, 50)) {
                        pendingAlerts.add(new AlertPending(entry.getKey(), a.name, score, suggestion));
                    }
                }
            }
            // Alert insert + webhook dispatch happen OUTSIDE the global DB lock.
            // webhook.dispatch() is a blocking HTTP call (up to ~8s); leaving it
            // inside synchronized(database.lock()) freezes every other thread
            // that needs the SQLite connection (audit writer, panel DB access).
            for (AlertPending p : pendingAlerts) {
                raiseRiskAlert(p.uuid, p.name, p.score, p.suggestion);
            }
        } catch (Exception ex) {
            LOGGER.warn("Admin risk recompute failed", ex);
        }
        rows.sort((x, y) -> Integer.compare((Integer) y.get("score"), (Integer) x.get("score")));
        return rows;
    }

    private record AlertPending(String uuid, String name, int score, String suggestion) {
    }

    private void raiseRiskAlert(String uuid, String name, int score, String suggestion) {
        try {
            AlertRecord alert = new AlertRecord(
                    java.util.UUID.randomUUID().toString(),
                    System.currentTimeMillis(),
                    "HIGH",
                    "ADMIN_RISK",
                    "管理员行为风险偏高",
                    (name == null ? uuid : name) + " 风险评分 " + score + "/100 — " + suggestion,
                    uuid,
                    name,
                    null,
                    null,
                    false
            );
            alertDao.insert(alert);
            if (webhook != null) {
                webhook.dispatch(alert);
            }
        } catch (Exception ex) {
            LOGGER.debug("Failed to raise ADMIN_RISK alert", ex);
        }
    }

    private static int scoreOf(Agg a) {
        int score = 0;
        score += Math.min(25, a.sudo / 2);
        score += Math.min(35, a.give / 4);
        score += Math.min(20, a.gamerule);
        score += Math.min(25, a.night * 2);
        if (a.night >= 10 && a.give >= 50) {
            score += 15;
        }
        return Math.min(100, score);
    }

    private static String suggestionOf(Agg a, int score) {
        if (a.night >= 8 && a.give >= 30) {
            return "异常行为：凌晨大量给予物品，建议检查";
        }
        if (a.give >= 100) {
            return "给予次数偏高，建议核对活动/审批记录";
        }
        if (a.sudo >= 40) {
            return "sudo 使用频繁，建议审查会话与角色";
        }
        if (score >= 70) {
            return "综合风险偏高，建议 Owner 复核近期操作";
        }
        if (score >= 40) {
            return "中等风险，保持抽查即可";
        }
        return "风险较低";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Agg {
        String name;
        int total;
        int sudo;
        int give;
        int gamerule;
        int night;
    }
}
