package com.esplus.audit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.security.db.SqliteDatabase;

/**
 * Pushes alerts to a Discord-compatible or generic JSON webhook.
 */
public final class AlertWebhookDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertWebhookDispatcher.class);

    private final SqliteDatabase database;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<String, Long> cooldownUntil = new ConcurrentHashMap<>();

    public AlertWebhookDispatcher(SqliteDatabase database) {
        this.database = database;
    }

    public void dispatch(AlertRecord alert) {
        String url = com.esplus.audit.GlobalEventDao.configStr(Config.ALERT_WEBHOOK_URL, "");
        if (url == null || url.isBlank() || alert == null) {
            return;
        }
        if (!severityAtLeast(alert.severity(), com.esplus.audit.GlobalEventDao.configStr(Config.ALERT_WEBHOOK_MIN_SEVERITY, "MEDIUM"))) {
            return;
        }
        String key = alert.ruleCode() + "|" + (alert.actorUuid() == null ? "na" : alert.actorUuid());
        long now = System.currentTimeMillis();
        long coolMs = com.esplus.audit.GlobalEventDao.configInt(Config.ALERT_WEBHOOK_COOLDOWN_SEC, 60) * 1000L;
        Long until = cooldownUntil.get(key);
        if (until != null && until > now) {
            return;
        }
        cooldownUntil.put(key, now + coolMs);

        String body = discordBody(alert);
        int status = -1;
        boolean ok = false;
        String detail = "";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            status = response.statusCode();
            ok = status >= 200 && status < 300;
            detail = ok ? "ok" : truncate(response.body(), 200);
            if (!ok) {
                LOGGER.warn("Webhook push failed status={} rule={}", status, alert.ruleCode());
            }
        } catch (Exception ex) {
            detail = truncate(ex.getMessage(), 200);
            LOGGER.warn("Webhook push error rule={}: {}", alert.ruleCode(), detail);
        }
        logDelivery(alert, status, ok, detail);
    }

    public boolean testPing() {
        String url = com.esplus.audit.GlobalEventDao.configStr(Config.ALERT_WEBHOOK_URL, "");
        if (url == null || url.isBlank()) {
            return false;
        }
        AlertRecord probe = new AlertRecord(
                "test-" + System.currentTimeMillis(),
                System.currentTimeMillis(),
                "HIGH",
                "WEBHOOK_TEST",
                "ESPlus Webhook Test",
                "连通性测试：若你看到此消息，告警通道正常。",
                null,
                "panel",
                null,
                null,
                false
        );
        dispatch(probe);
        return true;
    }

    private void logDelivery(AlertRecord alert, int status, boolean ok, String detail) {
        try {
            synchronized (database.lock()) {
                try (PreparedStatement statement = database.connection().prepareStatement(
                        """
                        INSERT INTO webhook_delivery_log (ts, severity, rule_code, http_status, ok, detail)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setString(2, alert.severity());
                    statement.setString(3, alert.ruleCode());
                    statement.setInt(4, status);
                    statement.setInt(5, ok ? 1 : 0);
                    statement.setString(6, detail);
                    statement.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Failed to log webhook delivery", ex);
        }
    }

    private static String discordBody(AlertRecord alert) {
        String content = "ESPlus Alert [" + alert.severity() + "] " + alert.title()
                + "\\n" + escapeJson(alert.message())
                + "\\nactor=" + escapeJson(alert.actorName() == null ? "-" : alert.actorName())
                + " rule=" + escapeJson(alert.ruleCode())
                + "\\nack via POST /api/ops/ack with X-SEM-Token";
        return "{\"content\":\"" + content + "\"}";
    }

    private static boolean severityAtLeast(String actual, String min) {
        return rank(actual) >= rank(min);
    }

    private static int rank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.trim().toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
