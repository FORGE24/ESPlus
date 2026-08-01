package com.esplus.panel;

import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.security.db.SqliteDatabase;

/** Deletes old audit / event / log rows according to retention days. */
public final class RetentionCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RetentionCleanup() {
    }

    public static int run(SqliteDatabase database, int retentionDays) throws SQLException {
        if (retentionDays <= 0 || database == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        int total = 0;
        try (Statement statement = database.connection().createStatement()) {
            total += statement.executeUpdate("DELETE FROM audit_log WHERE ts < " + cutoff);
            total += statement.executeUpdate("DELETE FROM global_events WHERE ts < " + cutoff);
            total += statement.executeUpdate("DELETE FROM server_logs WHERE ts < " + cutoff);
            total += statement.executeUpdate("DELETE FROM player_movements WHERE ts < " + cutoff);
            total += statement.executeUpdate("DELETE FROM perf_samples WHERE ts < " + cutoff);
            total += statement.executeUpdate(
                    "DELETE FROM alerts WHERE ts < " + cutoff + " AND acknowledged = 1");
        }
        if (total > 0) {
            LOGGER.info("Retention cleanup removed {} old rows (cutoff days={})", total, retentionDays);
        }
        return total;
    }
}
