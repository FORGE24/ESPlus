package com.esplus.panel;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.security.db.SqliteDatabase;

/**
 * Deletes old audit / event / log rows according to retention days.
 *
 * Concurrency: acquires the shared SQLite write lock for the full cleanup
 * window so it never races with the audit writer thread (sqlite-jdbc
 * single-connection is not thread-safe).
 *
 * Audit hash chain: after deleting old global_events, the remaining rows
 * no longer form a contiguous chain. We therefore RESEED meta.audit_chain_tip
 * to NULL — the next GlobalEventDao.insert will use GENESIS as prevHash and
 * start a fresh chain from the oldest surviving row, which keeps
 * verifyChain() correct.
 */
public final class RetentionCleanup {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RetentionCleanup() {
    }

    public static int run(SqliteDatabase database, int retentionDays) throws SQLException {
        if (retentionDays <= 0 || database == null) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - retentionDays * 86_400_000L;
        int total;
        synchronized (database.lock()) {
            int before = countGlobalEventsUnlocked(database);
            try (Statement statement = database.connection().createStatement()) {
                total = 0;
                total += statement.executeUpdate("DELETE FROM audit_log WHERE ts < " + cutoff);
                total += statement.executeUpdate("DELETE FROM global_events WHERE ts < " + cutoff);
                total += statement.executeUpdate("DELETE FROM server_logs WHERE ts < " + cutoff);
                total += statement.executeUpdate("DELETE FROM player_movements WHERE ts < " + cutoff);
                total += statement.executeUpdate("DELETE FROM perf_samples WHERE ts < " + cutoff);
                total += statement.executeUpdate(
                        "DELETE FROM alerts WHERE ts < " + cutoff + " AND acknowledged = 1");
            }

            int after = countGlobalEventsUnlocked(database);
            int removedFromChain = before - after;
            if (removedFromChain > 0) {
                reseedChainTipUnlocked(database);
                LOGGER.info("Retention cleanup reseeded audit chain tip (removed {} chain events)",
                        removedFromChain);
            }
        }
        if (total > 0) {
            LOGGER.info("Retention cleanup removed {} old rows (cutoff days={})", total, retentionDays);
        }
        return total;
    }

    private static int countGlobalEventsUnlocked(SqliteDatabase database) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "SELECT COUNT(*) FROM global_events")) {
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Reseed {@code meta.audit_chain_tip} to NULL so the next insert picks up
     * {@code GENESIS} as prevHash and starts a fresh contiguous chain.
     * Called while holding {@code database.lock()}.
     */
    private static void reseedChainTipUnlocked(SqliteDatabase database) throws SQLException {
        try (PreparedStatement ps = database.connection().prepareStatement(
                "UPDATE meta SET audit_chain_tip = NULL WHERE id = 1")) {
            ps.executeUpdate();
        }
    }
}
