package com.esplus.panel;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PanelIntegrationTest {
    private static final Path DB_FILE;

    static {
        try {
            Path dir = Files.createTempDirectory("esplus-panel-test");
            DB_FILE = dir.resolve("security.db");
            bootstrap(DB_FILE);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("esplus.db", () -> DB_FILE.toAbsolutePath().toString());
        registry.add("esplus.username", () -> "admin");
        registry.add("esplus.password", () -> "testpass");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PanelQueryService queries;

    @BeforeEach
    void seed() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM global_events");
            statement.executeUpdate("DELETE FROM alerts");
            statement.executeUpdate("""
                    INSERT INTO global_events(event_id, ts, category, action, actor_uuid, actor_name, detail, source)
                    VALUES ('evt-1', %d, 'item', 'sudo_give', '%s', 'Admin', 'minecraft:diamond x1', 'player')
                    """.formatted(System.currentTimeMillis(), UUID.randomUUID()));
            statement.executeUpdate("""
                    INSERT INTO alerts(alert_id, ts, severity, rule_code, title, message, acknowledged)
                    VALUES ('al-1', %d, 'HIGH', 'GIVE_BURST', '物品发放异常', 'burst', 0)
                    """.formatted(System.currentTimeMillis()));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dashboardAndSearchWork() throws Exception {
        var dash = queries.dashboard();
        assertTrue(((Number) dash.get("events24h")).longValue() >= 1);
        assertTrue(queries.search("diamond", "item", null, null, 20).size() >= 1);
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/search").param("q", "diamond")).andExpect(status().isOk());
    }

    private static void bootstrap(Path db) throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS global_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_id TEXT NOT NULL UNIQUE,
                        ts INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        action TEXT NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT,
                        target_uuid TEXT,
                        target_name TEXT,
                        dimension TEXT,
                        x REAL, y REAL, z REAL,
                        item_id TEXT,
                        trace_id TEXT,
                        detail TEXT,
                        source TEXT NOT NULL DEFAULT 'server'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_traces (
                        trace_id TEXT PRIMARY KEY,
                        item_id TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        origin_type TEXT NOT NULL,
                        origin_actor_uuid TEXT,
                        origin_actor_name TEXT,
                        origin_detail TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS item_trace_links (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        trace_id TEXT NOT NULL,
                        parent_trace_id TEXT,
                        event_id TEXT NOT NULL,
                        ts INTEGER NOT NULL,
                        action TEXT NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT,
                        detail TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_movements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ts INTEGER NOT NULL,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        dimension TEXT NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        yaw REAL, pitch REAL,
                        on_ground INTEGER, sprinting INTEGER, flying INTEGER
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        alert_id TEXT NOT NULL UNIQUE,
                        ts INTEGER NOT NULL,
                        severity TEXT NOT NULL,
                        rule_code TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT,
                        related_event_id TEXT,
                        related_trace_id TEXT,
                        acknowledged INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }
}
