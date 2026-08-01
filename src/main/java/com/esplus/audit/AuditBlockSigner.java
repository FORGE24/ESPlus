package com.esplus.audit;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.security.crypto.RsaKeyManager;
import com.esplus.security.db.SqliteDatabase;

/** Periodically RSA-signs the audit chain tip for offline integrity proofs. */
public final class AuditBlockSigner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditBlockSigner.class);

    private final SqliteDatabase database;
    private final RsaKeyManager rsa;
    private final AtomicInteger sinceLast = new AtomicInteger();

    public AuditBlockSigner(SqliteDatabase database, RsaKeyManager rsa) {
        this.database = database;
        this.rsa = rsa;
    }

    public void onEventPersisted() {
        if (!com.esplus.audit.GlobalEventDao.configBool(Config.AUDIT_BLOCK_SIGNING, false) || rsa == null) {
            return;
        }
        int n = sinceLast.incrementAndGet();
        if (n < com.esplus.audit.GlobalEventDao.configInt(Config.AUDIT_BLOCK_SIGN_EVERY_EVENTS, 100)) {
            return;
        }
        sinceLast.set(0);
        signNow(n);
    }

    public void signNow(int eventCount) {
        try {
            synchronized (database.lock()) {
                String tip;
                try (PreparedStatement statement = database.connection().prepareStatement(
                        "SELECT audit_chain_tip FROM meta WHERE id = 1")) {
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next() || rs.getString(1) == null || rs.getString(1).isBlank()) {
                            return;
                        }
                        tip = rs.getString(1);
                    }
                }
                String payload = "SEM-AUDIT-BLOCK|" + tip + "|" + eventCount + "|" + System.currentTimeMillis();
                String sig = rsa.signSha256Base64(payload.getBytes(StandardCharsets.UTF_8));
                try (PreparedStatement insert = database.connection().prepareStatement(
                        """
                        INSERT INTO audit_block_signatures (ts, tip_hash, signature_b64, event_count, detail)
                        VALUES (?, ?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, System.currentTimeMillis());
                    insert.setString(2, tip);
                    insert.setString(3, sig);
                    insert.setInt(4, eventCount);
                    insert.setString(5, payload);
                    insert.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Audit block signing failed", ex);
        }
    }
}
