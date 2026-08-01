package com.esplus.panel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.esplus.panel.security.TotpService;

@Service
public class PanelMfaService {
    private final JdbcTemplate jdbc;

    public PanelMfaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEnabled(String username) {
        ensure();
        if (username == null || username.isBlank()) {
            return false;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT enabled FROM panel_mfa WHERE username = ?", username.trim().toLowerCase());
        return !rows.isEmpty() && ((Number) rows.getFirst().get("enabled")).intValue() != 0;
    }

    public boolean verify(String username, String code) {
        ensure();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT secret, enabled FROM panel_mfa WHERE username = ?", username.trim().toLowerCase());
        if (rows.isEmpty()) {
            return true;
        }
        Map<String, Object> row = rows.getFirst();
        if (((Number) row.get("enabled")).intValue() == 0) {
            return true;
        }
        return TotpService.verify(String.valueOf(row.get("secret")), code);
    }

    public Map<String, Object> beginEnroll(String username) {
        ensure();
        String secret = TotpService.generateSecret();
        String user = username.trim().toLowerCase();
        jdbc.update(
                """
                INSERT INTO panel_mfa (username, secret, enabled, updated_at) VALUES (?, ?, 0, ?)
                ON CONFLICT(username) DO UPDATE SET secret=excluded.secret, enabled=0, updated_at=excluded.updated_at
                """,
                user, secret, System.currentTimeMillis());
        Map<String, Object> map = new HashMap<>();
        map.put("secret", secret);
        map.put("uri", TotpService.otpAuthUri("ESPlus-Panel", user, secret));
        return map;
    }

    public boolean confirmEnroll(String username, String code) {
        ensure();
        String user = username.trim().toLowerCase();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT secret FROM panel_mfa WHERE username = ?", user);
        if (rows.isEmpty()) {
            return false;
        }
        String secret = String.valueOf(rows.getFirst().get("secret"));
        if (!TotpService.verify(secret, code)) {
            return false;
        }
        jdbc.update("UPDATE panel_mfa SET enabled = 1, updated_at = ? WHERE username = ?",
                System.currentTimeMillis(), user);
        return true;
    }

    public boolean disable(String username) {
        ensure();
        int n = jdbc.update("UPDATE panel_mfa SET enabled = 0, updated_at = ? WHERE username = ?",
                System.currentTimeMillis(), username.trim().toLowerCase());
        return n > 0;
    }

    private void ensure() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS panel_mfa (
                    username TEXT PRIMARY KEY,
                    secret TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
    }
}
