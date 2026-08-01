package com.esplus.audit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.security.db.SqliteDatabase;

/**
 * Auto-response: after repeated high-risk alerts, enqueue kick / temp-ban / optional ban-ip.
 */
public final class AutoResponseEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoResponseEngine.class);

    private final SqliteDatabase database;
    private final Map<String, long[]> memory = new ConcurrentHashMap<>(); // key -> [windowStart, count]

    public AutoResponseEngine(SqliteDatabase database) {
        this.database = database;
    }

    public void onAlert(AlertRecord alert) {
        if (!com.esplus.audit.GlobalEventDao.configBool(Config.AUTO_RESPONSE_ENABLED, false) || alert == null) {
            return;
        }
        String rule = alert.ruleCode() == null ? "" : alert.ruleCode().toUpperCase(Locale.ROOT);
        String action;
        int threshold;
        if ("SUDO_FAIL".equals(rule)) {
            action = "temp_ban";
            threshold = com.esplus.audit.GlobalEventDao.configInt(Config.AUTO_RESPONSE_SUDO_FAIL_THRESHOLD, 3);
        } else if ("CMD_BURST".equals(rule) || "GIVE_BURST".equals(rule)) {
            action = "kick";
            threshold = com.esplus.audit.GlobalEventDao.configInt(Config.AUTO_RESPONSE_CMD_BURST_THRESHOLD, 40);
        } else {
            return;
        }
        String actorKey = alert.actorUuid() == null ? ("name:" + alert.actorName()) : alert.actorUuid();
        String hitKey = rule + "|" + actorKey;
        long now = System.currentTimeMillis();
        long windowMs = com.esplus.audit.GlobalEventDao.configInt(Config.AUTO_RESPONSE_WINDOW_SECONDS, 60) * 1000L;
        int count = bump(hitKey, now, windowMs);
        if (count < threshold) {
            return;
        }
        memory.remove(hitKey);
        enqueue(action, alert);
        if (com.esplus.audit.GlobalEventDao.configBool(Config.AUTO_RESPONSE_BAN_IP, false) && alert.actorUuid() != null) {
            enqueueIpBan(alert);
        }
        markAlert(alert.alertId(), action + "@" + count);
        LOGGER.warn("Auto-response {} for {} rule={} count={}", action, alert.actorName(), rule, count);
    }

    private int bump(String key, long now, long windowMs) {
        long[] state = memory.compute(key, (k, prev) -> {
            if (prev == null || now - prev[0] > windowMs) {
                return new long[]{now, 1L};
            }
            prev[1]++;
            return prev;
        });
        return (int) state[1];
    }

    private void enqueue(String action, AlertRecord alert) {
        try {
            synchronized (database.lock()) {
                String panelAction = "kick".equals(action) ? "kick_player" : "temp_ban_player";
                String payload = "kick".equals(action)
                        ? "ESPlus auto-response"
                        : com.esplus.audit.GlobalEventDao.configInt(Config.AUTO_RESPONSE_TEMP_BAN_MINUTES, 30) + "|ESPlus auto-response";
                try (PreparedStatement statement = database.connection().prepareStatement(
                        """
                        INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                        VALUES (?, ?, ?, ?, 'pending', ?)
                        """)) {
                    statement.setString(1, panelAction);
                    statement.setString(2, alert.actorUuid());
                    statement.setString(3, alert.actorName());
                    statement.setString(4, payload);
                    statement.setLong(5, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Auto-response enqueue failed", ex);
        }
    }

    private void enqueueIpBan(AlertRecord alert) {
        // Resolve last known IP from online_players is not stored; use placeholder note in detail.
        // IP ban requires IP string — skip silently if unknown (BehaviorHooks can extend later).
        try {
            String ip = lookupRecentIp(alert.actorUuid());
            if (ip == null || ip.isBlank()) {
                return;
            }
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        """
                        INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                        VALUES ('ban_ip', NULL, ?, ?, 'pending', ?)
                        """)) {
                    statement.setString(1, "ESPlus auto-response IP ban");
                    statement.setString(2, ip);
                    statement.setLong(3, System.currentTimeMillis());
                    statement.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("auto IP ban skipped", ex);
        }
    }

    private String lookupRecentIp(String uuid) {
        if (uuid == null) {
            return null;
        }
        try (PreparedStatement statement = database.connection().prepareStatement(
                "SELECT value FROM sem_kv WHERE key = ?")) {
            statement.setString(1, "last_ip:" + uuid);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private void markAlert(String alertId, String autoAction) {
        try {
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        "UPDATE alerts SET auto_action = ? WHERE alert_id = ?")) {
                    statement.setString(1, autoAction);
                    statement.setString(2, alertId);
                    statement.executeUpdate();
                }
            }
        } catch (Exception ignored) {
            // column may be missing on old DB until migrate
        }
    }
}
