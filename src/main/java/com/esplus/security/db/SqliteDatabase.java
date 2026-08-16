package com.esplus.security.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteDatabase implements AutoCloseable {
    private final Path dbPath;
    private final Object lock = new Object();
    private Connection connection;

    public SqliteDatabase(Path dbPath) {
        this.dbPath = dbPath;
    }

    public Path path() {
        return dbPath;
    }

    /** Shared monitor for multi-threaded SQLite access (audit writer + server tick). */
    public Object lock() {
        return lock;
    }

    public void open() throws SQLException, IOException {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        JdbcDriverLoader.ensureSqlite();
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA synchronous = NORMAL");
            createSchema(statement);
        }
    }

    private static void createSchema(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS meta (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    wrapped_aes_key BLOB NOT NULL,
                    schema_version INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    password_cipher TEXT NOT NULL,
                    op_bound INTEGER NOT NULL DEFAULT 1,
                    role TEXT NOT NULL DEFAULT 'op',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    locked_until INTEGER NOT NULL DEFAULT 0
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT,
                    action TEXT NOT NULL,
                    detail TEXT,
                    success INTEGER NOT NULL,
                    ts INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS protected_commands (
                    command TEXT PRIMARY KEY,
                    risk TEXT NOT NULL DEFAULT 'HIGH'
                )
                """);
        ensureColumn(statement, "protected_commands", "risk", "TEXT NOT NULL DEFAULT 'HIGH'");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS user_permissions (
                    uuid TEXT NOT NULL,
                    perm TEXT NOT NULL,
                    allowed INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY (uuid, perm)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_user_permissions_uuid ON user_permissions(uuid)");
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
                    x REAL,
                    y REAL,
                    z REAL,
                    item_id TEXT,
                    trace_id TEXT,
                    detail TEXT,
                    source TEXT NOT NULL DEFAULT 'server'
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_global_events_ts ON global_events(ts)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_global_events_actor ON global_events(actor_uuid)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_global_events_trace ON global_events(trace_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_global_events_item ON global_events(item_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_global_events_category ON global_events(category)");

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
                    detail TEXT,
                    FOREIGN KEY(trace_id) REFERENCES item_traces(trace_id)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_item_trace_links_trace ON item_trace_links(trace_id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_item_trace_links_parent ON item_trace_links(parent_trace_id)");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_movements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    dimension TEXT NOT NULL,
                    x REAL NOT NULL,
                    y REAL NOT NULL,
                    z REAL NOT NULL,
                    yaw REAL,
                    pitch REAL,
                    on_ground INTEGER,
                    sprinting INTEGER,
                    flying INTEGER
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_movements_player_ts ON player_movements(player_uuid, ts)");

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
        statement.execute("CREATE INDEX IF NOT EXISTS idx_alerts_ts ON alerts(ts)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_alerts_ack ON alerts(acknowledged)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_ts ON audit_log(ts)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS panel_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action TEXT NOT NULL,
                    target_uuid TEXT,
                    target_name TEXT,
                    payload TEXT,
                    status TEXT NOT NULL DEFAULT 'pending',
                    result TEXT,
                    created_at INTEGER NOT NULL,
                    processed_at INTEGER
                )
                """);
        ensureColumn(statement, "panel_actions", "payload", "TEXT");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_panel_actions_status ON panel_actions(status)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    level TEXT NOT NULL,
                    logger TEXT,
                    message TEXT NOT NULL
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_server_logs_id ON server_logs(id)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_server_logs_ts ON server_logs(ts)");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS online_players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    ping INTEGER NOT NULL DEFAULT 0,
                    dimension TEXT,
                    x REAL,
                    y REAL,
                    z REAL,
                    game_mode TEXT,
                    updated_at INTEGER NOT NULL
                )
                """);
        ensureColumn(statement, "online_players", "health", "REAL");
        ensureColumn(statement, "online_players", "food", "INTEGER");
        ensureColumn(statement, "online_players", "xp_level", "INTEGER");
        ensureColumn(statement, "online_players", "is_op", "INTEGER NOT NULL DEFAULT 0");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_bans (
                    uuid TEXT PRIMARY KEY,
                    name TEXT,
                    reason TEXT,
                    source TEXT,
                    created_at INTEGER,
                    expires_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_whitelist (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_runtime (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    whitelist_enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        ensureColumn(statement, "server_runtime", "player_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "max_players", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "mspt_ms", "REAL NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "tps_approx", "REAL NOT NULL DEFAULT 20");
        ensureColumn(statement, "server_runtime", "entity_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "chunk_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "world_count", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "memory_used_mb", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "memory_max_mb", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "game_port", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "online_mode", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn(statement, "server_runtime", "motd", "TEXT");
        ensureColumn(statement, "server_runtime", "difficulty", "TEXT");
        ensureColumn(statement, "server_runtime", "hardcore", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "default_gamemode", "TEXT");
        ensureColumn(statement, "server_runtime", "day_time", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "raining", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "thundering", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "border_size", "REAL NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "border_center_x", "REAL NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "border_center_z", "REAL NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "border_warning", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "border_damage", "REAL NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "uptime_ticks", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "mc_version", "TEXT");
        ensureColumn(statement, "server_runtime", "neoforge_version", "TEXT");
        ensureColumn(statement, "server_runtime", "mod_version", "TEXT");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_gamerules (
                    rule_id TEXT PRIMARY KEY,
                    category TEXT,
                    value_type TEXT NOT NULL,
                    value TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_dimensions (
                    dimension TEXT PRIMARY KEY,
                    player_count INTEGER NOT NULL DEFAULT 0,
                    entity_count INTEGER NOT NULL DEFAULT 0,
                    chunk_count INTEGER NOT NULL DEFAULT 0,
                    day_time INTEGER NOT NULL DEFAULT 0,
                    raining INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        ensureColumn(statement, "server_runtime", "spawn_x", "REAL");
        ensureColumn(statement, "server_runtime", "spawn_y", "REAL");
        ensureColumn(statement, "server_runtime", "spawn_z", "REAL");
        ensureColumn(statement, "server_runtime", "spawn_angle", "REAL");
        ensureColumn(statement, "server_runtime", "sudo_session_minutes", "INTEGER");
        ensureColumn(statement, "server_runtime", "max_failed_attempts", "INTEGER");
        ensureColumn(statement, "server_runtime", "lock_minutes", "INTEGER");
        ensureColumn(statement, "server_runtime", "protected_commands", "TEXT");
        ensureColumn(statement, "server_runtime", "audit_retention_days", "INTEGER");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_bossbars (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    color TEXT,
                    overlay TEXT,
                    value INTEGER NOT NULL DEFAULT 0,
                    max_value INTEGER NOT NULL DEFAULT 100,
                    visible INTEGER NOT NULL DEFAULT 1,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_scoreboard (
                    name TEXT PRIMARY KEY,
                    criteria TEXT,
                    display_name TEXT,
                    display_slot TEXT,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_teams (
                    name TEXT PRIMARY KEY,
                    display_name TEXT,
                    color TEXT,
                    friendly_fire INTEGER NOT NULL DEFAULT 1,
                    see_friendly_invisibles INTEGER NOT NULL DEFAULT 1,
                    members TEXT,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_filter_words (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    word TEXT NOT NULL UNIQUE,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_ip_bans (
                    ip TEXT PRIMARY KEY,
                    reason TEXT,
                    source TEXT,
                    created_at INTEGER,
                    expires_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS server_entity_types (
                    entity_type TEXT PRIMARY KEY,
                    count INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS panel_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    interval_seconds INTEGER NOT NULL DEFAULT 0,
                    next_run_at INTEGER NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    last_run_at INTEGER,
                    created_at INTEGER NOT NULL,
                    note TEXT
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_panel_schedules_next ON panel_schedules(enabled, next_run_at)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS chat_mutes (
                    key TEXT PRIMARY KEY,
                    name TEXT,
                    reason TEXT,
                    until_ts INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """);
        ensureColumn(statement, "server_runtime", "idle_timeout", "INTEGER");
        ensureColumn(statement, "server_runtime", "maintenance", "INTEGER NOT NULL DEFAULT 0");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS perf_samples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    tps REAL NOT NULL,
                    mspt_ms REAL NOT NULL,
                    memory_used_mb INTEGER NOT NULL DEFAULT 0,
                    entity_count INTEGER NOT NULL DEFAULT 0,
                    player_count INTEGER NOT NULL DEFAULT 0
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_perf_samples_ts ON perf_samples(ts)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS player_inventory (
                    uuid TEXT NOT NULL,
                    name TEXT,
                    section TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER NOT NULL DEFAULT 1,
                    display_name TEXT,
                    updated_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, section, slot)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_player_inventory_name ON player_inventory(name)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS panel_accounts (
                    username TEXT PRIMARY KEY,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    failed_attempts INTEGER NOT NULL DEFAULT 0,
                    locked_until INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);

        ensureColumn(statement, "global_events", "event_hash", "TEXT");
        ensureColumn(statement, "global_events", "prev_hash", "TEXT");
        ensureColumn(statement, "meta", "audit_chain_tip", "TEXT");
        ensureColumn(statement, "server_runtime", "server_id", "TEXT");
        ensureColumn(statement, "server_runtime", "server_name", "TEXT");
        ensureColumn(statement, "server_runtime", "entity_share_hint", "TEXT");
        ensureColumn(statement, "server_runtime", "approval_enabled", "INTEGER NOT NULL DEFAULT 1");
        ensureColumn(statement, "perf_samples", "entity_share_hint", "TEXT");
        ensureColumn(statement, "perf_samples", "chunk_count", "INTEGER NOT NULL DEFAULT 0");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS sem_kv (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
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
        statement.execute("CREATE INDEX IF NOT EXISTS idx_approval_status ON approval_requests(status, created_at)");
        statement.execute("""
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
        statement.execute("CREATE INDEX IF NOT EXISTS idx_config_revisions_ts ON config_revisions(ts)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS security_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    actor TEXT,
                    label TEXT NOT NULL,
                    payload_json TEXT NOT NULL
                )
                """);
        statement.execute("""
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
        statement.execute("""
                CREATE TABLE IF NOT EXISTS webhook_delivery_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    severity TEXT,
                    rule_code TEXT,
                    http_status INTEGER,
                    ok INTEGER NOT NULL DEFAULT 0,
                    detail TEXT
                )
                """);
        ensureColumn(statement, "meta", "server_id", "TEXT");
        ensureColumn(statement, "meta", "server_name", "TEXT");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS user_mfa (
                    uuid TEXT PRIMARY KEY,
                    secret TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS panel_mfa (
                    username TEXT PRIMARY KEY,
                    secret TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS auto_response_hits (
                    hit_key TEXT PRIMARY KEY,
                    count INTEGER NOT NULL DEFAULT 0,
                    window_start INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS hwid_blacklist (
                    hwid TEXT PRIMARY KEY,
                    name TEXT,
                    reason TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS connection_fingerprints (
                    player_uuid TEXT PRIMARY KEY,
                    hwid TEXT,
                    zone_id TEXT,
                    client_ip TEXT,
                    geo_country TEXT,
                    geo_region TEXT,
                    geo_city TEXT,
                    geo_lat REAL,
                    geo_lon REAL,
                    udp_external_ip TEXT,
                    server_latency_ms INTEGER,
                    confidence_score INTEGER,
                    flagged_reasons TEXT,
                    created_at INTEGER NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS geoip_blocks (
                    country TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    reason TEXT
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_connection_fingerprints_hwid ON connection_fingerprints(hwid)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_geoip_blocks_country ON geoip_blocks(country)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS audit_block_signatures (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    tip_hash TEXT NOT NULL,
                    signature_b64 TEXT NOT NULL,
                    event_count INTEGER NOT NULL,
                    detail TEXT
                )
                """);
        ensureColumn(statement, "server_runtime", "lockdown", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "server_runtime", "setup_complete", "INTEGER NOT NULL DEFAULT 0");
        ensureColumn(statement, "alerts", "auto_action", "TEXT");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS block_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ts INTEGER NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT,
                    action TEXT NOT NULL,
                    dimension TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    block_id TEXT NOT NULL,
                    old_block_id TEXT
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_block_snapshots_player ON block_snapshots(player_uuid, ts)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_block_snapshots_pos ON block_snapshots(dimension, x, y, z)");

        statement.execute("""
                CREATE TABLE IF NOT EXISTS inventory_snapshots (
                    snapshot_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT,
                    ts INTEGER NOT NULL,
                    section TEXT NOT NULL,
                    slot INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    count INTEGER NOT NULL DEFAULT 1,
                    display_name TEXT,
                    source TEXT
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS idx_inv_snap_player ON inventory_snapshots(player_uuid, ts)");
        statement.execute("CREATE INDEX IF NOT EXISTS idx_inv_snap_source ON inventory_snapshots(source)");

        // Existing installs: skip wizard if already used
        statement.execute("""
                UPDATE server_runtime SET setup_complete = 1
                WHERE id = 1 AND setup_complete = 0
                  AND (
                    EXISTS (SELECT 1 FROM users LIMIT 1)
                    OR EXISTS (SELECT 1 FROM global_events LIMIT 1)
                    OR EXISTS (SELECT 1 FROM audit_log LIMIT 1)
                  )
                """);
    }

    private static void ensureColumn(Statement statement, String table, String column, String definition)
            throws SQLException {
        boolean exists = false;
        try (var rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    public Connection connection() {
        if (connection == null) {
            throw new IllegalStateException("Database is not open");
        }
        return connection;
    }

    @Override
    public void close() throws SQLException {
        synchronized (lock) {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }
    }
}
