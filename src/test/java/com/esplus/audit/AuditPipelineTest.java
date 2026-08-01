package com.esplus.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.esplus.security.db.SqliteDatabase;
import com.esplus.security.crypto.RsaKeyManager;

class AuditPipelineTest {
    @TempDir
    Path temp;

    private SqliteDatabase database;
    private AuditService audit;

    @BeforeEach
    void setUp() throws Exception {
        database = new SqliteDatabase(temp.resolve("security.db"));
        database.open();
        RsaKeyManager rsa = new RsaKeyManager(temp.resolve("keys"));
        rsa.initialize();
        audit = new AuditService(database, rsa, 3, 2, 5, 60_000L);
    }

    @AfterEach
    void tearDown() {
        audit.close();
        try {
            database.close();
        } catch (Exception ignored) {
        }
    }

    @Test
    void recordsSearchTraceIncidentAndAlerts() throws Exception {
        UUID player = UUID.randomUUID();
        String traceId = audit.createItemTrace("minecraft:diamond", "sudo_give", player, "Admin", "test give");
        Thread.sleep(50);

        GlobalEvent give1 = new GlobalEvent(
                UUID.randomUUID().toString(), System.currentTimeMillis(), "item", "sudo_give",
                player.toString(), "Admin", null, null, "minecraft:overworld",
                1.0, 64.0, 1.0, "minecraft:diamond", traceId, "diamond x1", "player");
        GlobalEvent give2 = new GlobalEvent(
                UUID.randomUUID().toString(), System.currentTimeMillis(), "item", "sudo_give",
                player.toString(), "Admin", null, null, "minecraft:overworld",
                1.0, 64.0, 1.0, "minecraft:diamond", traceId, "diamond x1", "player");
        audit.recordSync(give1);
        audit.recordSync(give2);
        audit.linkItem(traceId, null, give1.eventId(), "sudo_give", player, "Admin", "link");
        audit.recordMovement(new MovementSample(
                System.currentTimeMillis(), player.toString(), "Admin", "minecraft:overworld",
                1, 64, 1, 0, 0, true, false, false));

        List<GlobalEvent> found = audit.search("diamond", "item", player.toString(), traceId, 0, 0, 50);
        assertFalse(found.isEmpty());

        Map<String, Object> chain = audit.itemChain(traceId);
        assertEquals(true, chain.get("found"));

        Map<String, Object> incident = audit.incident(give1.eventId(), 60_000L);
        assertEquals(true, incident.get("found"));

        List<AlertRecord> alerts = audit.alerts(true, 50);
        assertTrue(alerts.stream().anyMatch(a -> "GIVE_BURST".equals(a.ruleCode())));
        assertTrue(audit.acknowledgeAlert(alerts.getFirst().alertId()));
    }
}
