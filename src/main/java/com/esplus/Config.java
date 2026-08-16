package com.esplus;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SUDO_SESSION_MINUTES = BUILDER
            .comment("Timed sudo session length in minutes")
            .defineInRange("sudoSessionMinutes", 5, 1, 120);

    public static final ModConfigSpec.IntValue MAX_FAILED_ATTEMPTS = BUILDER
            .comment("Failed sudo/password attempts before temporary lock")
            .defineInRange("maxFailedAttempts", 5, 1, 50);

    public static final ModConfigSpec.IntValue LOCK_MINUTES = BUILDER
            .comment("Account lock duration in minutes after too many failures")
            .defineInRange("lockMinutes", 15, 1, 1440);

    public static final ModConfigSpec.IntValue MIN_PASSWORD_LENGTH = BUILDER
            .comment("Minimum sudo password length")
            .defineInRange("minPasswordLength", 6, 4, 128);

    public static final ModConfigSpec.ConfigValue<String> DATABASE_PATH = BUILDER
            .comment("SQLite database path relative to the server root (or absolute)")
            .define("databasePath", "esplus/security.db");

    public static final ModConfigSpec.ConfigValue<String> KEYS_DIRECTORY = BUILDER
            .comment("Directory for RSA key material relative to the server config folder")
            .define("keysDirectory", "esplus/keys");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROTECTED_COMMANDS = BUILDER
            .comment("Vanilla/root commands that require an active sudo session")
            .defineListAllowEmpty(
                    "protectedCommands",
                    List.of("give", "gamemode", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick", "stop", "whitelist", "difficulty", "tp", "teleport", "kill", "clear", "effect", "enchant", "xp", "experience", "setblock", "fill", "clone", "summon", "setworldspawn", "time", "weather", "gamerule"),
                    () -> "",
                    obj -> obj instanceof String s && !s.isBlank());

    public static final ModConfigSpec.BooleanValue AUDIT_ENABLED = BUILDER
            .comment("Enable global behavior / item / movement audit recording")
            .define("auditEnabled", true);

    public static final ModConfigSpec.IntValue MOVEMENT_SAMPLE_TICKS = BUILDER
            .comment("Sample player movement every N server ticks (20 ticks = 1 second)")
            .defineInRange("movementSampleTicks", 40, 20, 200);

    public static final ModConfigSpec.DoubleValue MOVEMENT_MIN_DISTANCE = BUILDER
            .comment("Minimum distance moved before a movement sample is stored")
            .defineInRange("movementMinDistance", 1.5, 0.1, 64.0);

    public static final ModConfigSpec.IntValue ANOMALY_WINDOW_SECONDS = BUILDER
            .comment("Anomaly detection sliding window in seconds")
            .defineInRange("anomalyWindowSeconds", 60, 10, 600);

    public static final ModConfigSpec.IntValue ANOMALY_COMMAND_BURST = BUILDER
            .comment("Command count in window that triggers CMD_BURST alert")
            .defineInRange("anomalyCommandBurst", 40, 5, 500);

    public static final ModConfigSpec.IntValue ANOMALY_GIVE_BURST = BUILDER
            .comment("Give/sudo_give count in window that triggers GIVE_BURST alert")
            .defineInRange("anomalyGiveBurst", 8, 2, 200);

    public static final ModConfigSpec.IntValue ANOMALY_BREAK_BURST = BUILDER
            .comment("Block break count in window that triggers BREAK_BURST alert")
            .defineInRange("anomalyBreakBurst", 80, 10, 2000);

    public static final ModConfigSpec.BooleanValue AUTO_SUDO_ADMIN_TO_ADMIN = BUILDER
            .comment("Optional: admin→admin protected actions auto-elevate (one-shot, no password). admin→player still requires manual /sudo")
            .define("autoSudoAdminToAdmin", false);

    public static final ModConfigSpec.BooleanValue PANEL_ENABLED = BUILDER
            .comment("Start the embedded Spring Boot admin panel in an isolated JVM")
            .define("panelEnabled", true);

    public static final ModConfigSpec.IntValue PANEL_PORT = BUILDER
            .comment("Admin panel HTTP port")
            .defineInRange("panelPort", 8088, 1024, 65535);

    public static final ModConfigSpec.ConfigValue<String> PANEL_BIND_ADDRESS = BUILDER
            .comment("Admin panel bind address. Use 127.0.0.1 when exposing via public reverse tunnel; 0.0.0.0 only for local LAN debug")
            .define("panelBindAddress", "127.0.0.1");

    public static final ModConfigSpec.ConfigValue<String> PANEL_USERNAME = BUILDER
            .comment("Admin panel login username")
            .define("panelUsername", "admin");

    public static final ModConfigSpec.ConfigValue<String> PANEL_PASSWORD = BUILDER
            .comment("Admin panel login password (passed to panel JVM via env, not written to disk)")
            .define("panelPassword", "esplus");

    public static final ModConfigSpec.BooleanValue PANEL_ALLOW_DEFAULT_PASSWORD = BUILDER
            .comment("If false, refuse to start the panel when panelPassword is still the default 'esplus'")
            .define("panelAllowDefaultPassword", false);

    public static final ModConfigSpec.ConfigValue<String> PANEL_MOD_USERNAME = BUILDER
            .comment("Moderator panel login username (empty to disable)")
            .define("panelModUsername", "mod");

    public static final ModConfigSpec.ConfigValue<String> PANEL_MOD_PASSWORD = BUILDER
            .comment("Moderator panel login password (env only, not written to disk)")
            .define("panelModPassword", "semimod");

    public static final ModConfigSpec.ConfigValue<String> PANEL_VIEWER_USERNAME = BUILDER
            .comment("Viewer panel login username (empty to disable)")
            .define("panelViewerUsername", "");

    public static final ModConfigSpec.ConfigValue<String> PANEL_VIEWER_PASSWORD = BUILDER
            .comment("Viewer panel login password (env only, not written to disk)")
            .define("panelViewerPassword", "");

    public static final ModConfigSpec.IntValue AUDIT_RETENTION_DAYS = BUILDER
            .comment("Auto-delete audit/events/logs older than N days (0 = never)")
            .defineInRange("auditRetentionDays", 30, 0, 3650);

    public static final ModConfigSpec.ConfigValue<String> PANEL_SSH_HINT = BUILDER
            .comment("Shown on panel remote page: SSH host hint for operators")
            .define("panelSshHint", "ssh user@public-vps");

    public static final ModConfigSpec.BooleanValue GEOIP_BLOCK_ENABLED = BUILDER
            .comment("Block connections from specific countries via geoip_blocks table")
            .define("geoipBlockEnabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> GEOIP_BLOCK_COUNTRIES = BUILDER
            .comment("ISO country codes (2-letter) to block when geoipBlockEnabled=true")
            .defineListAllowEmpty(
                    "geoipBlockCountries",
                    List.of(),
                    () -> "",
                    obj -> obj instanceof String);

    public static final ModConfigSpec.BooleanValue HWID_BLACKLIST_ENABLED = BUILDER
            .comment("Check connecting player HWID against hwid_blacklist table")
            .define("hwidBlacklistEnabled", true);

    public static final ModConfigSpec.IntValue CONFIDENCE_THRESHOLD = BUILDER
            .comment("Auto-kick fingerprint sessions whose confidenceScore drops below this")
            .defineInRange("confidenceThreshold", 30, 0, 100);

    public static final ModConfigSpec.DoubleValue SERVER_GEO_LAT = BUILDER
            .comment("Approximate server geo latitude (degrees) for RTT paradox check")
            .defineInRange("serverGeoLat", 0.0, -90.0, 90.0);

    public static final ModConfigSpec.DoubleValue SERVER_GEO_LON = BUILDER
            .comment("Approximate server geo longitude (degrees) for RTT paradox check")
            .defineInRange("serverGeoLon", 0.0, -180.0, 180.0);

    public static final ModConfigSpec.BooleanValue UDP_PROBE_ENABLED = BUILDER
            .comment("Enable UDP egress-IP probe to detect proxy double-NAT")
            .define("udpProbeEnabled", true);

    public static final ModConfigSpec.BooleanValue ZONE_IP_CHECK_ENABLED = BUILDER
            .comment("Cross-check client ZoneId against GeoIP country (Asia zone + US/EU IP mismatch)")
            .define("zoneIpCheckEnabled", true);

    public static final ModConfigSpec.BooleanValue RTT_PARADOX_ENABLED = BUILDER
            .comment("Detect latency too low to be physically possible given great-circle distance")
            .define("rttParadoxEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> SERVER_ID = BUILDER
            .comment("Logical server id for multi-server center (single-server default)")
            .define("serverId", "local");

    public static final ModConfigSpec.ConfigValue<String> SERVER_NAME = BUILDER
            .comment("Display name for this server in the center page")
            .define("serverName", "ESPlus");

    public static final ModConfigSpec.BooleanValue APPROVAL_ENABLED = BUILDER
            .comment("Route high-risk panel actions through Owner approval before execution")
            .define("approvalEnabled", true);

    public static final ModConfigSpec.IntValue APPROVAL_GIVE_THRESHOLD = BUILDER
            .comment("give_item count at or above this value requires approval when approvalEnabled")
            .defineInRange("approvalGiveThreshold", 64, 1, 2304);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> APPROVAL_ACTIONS = BUILDER
            .comment("Panel action types that always require approval when approvalEnabled")
            .defineListAllowEmpty(
                    "approvalRequiredActions",
                    List.of("give_item", "set_gamerule", "console_cmd", "kill_entities", "stop_server", "restore_snapshot"),
                    () -> "",
                    obj -> obj instanceof String s && !s.isBlank());

    public static final ModConfigSpec.ConfigValue<String> ALERT_WEBHOOK_URL = BUILDER
            .comment("Discord/generic webhook URL for HIGH/CRITICAL alerts (empty = disabled)")
            .define("alertWebhookUrl", "");

    public static final ModConfigSpec.ConfigValue<String> ALERT_WEBHOOK_MIN_SEVERITY = BUILDER
            .comment("Minimum severity to push: CRITICAL, HIGH, MEDIUM, LOW")
            .define("alertWebhookMinSeverity", "HIGH");

    public static final ModConfigSpec.IntValue ALERT_WEBHOOK_COOLDOWN_SEC = BUILDER
            .comment("Per-rule webhook cooldown seconds")
            .defineInRange("alertWebhookCooldownSec", 60, 5, 3600);

    public static final ModConfigSpec.BooleanValue AUDIT_HASH_CHAIN = BUILDER
            .comment("Append SHA-256 hash chain columns on global_events for tamper detection")
            .define("auditHashChain", true);

    public static final ModConfigSpec.IntValue ADMIN_RISK_ALERT_SCORE = BUILDER
            .comment("Raise ADMIN_RISK alert when 7-day admin risk score reaches this value")
            .defineInRange("adminRiskAlertScore", 70, 1, 100);

    public static final ModConfigSpec.BooleanValue SUDO_TOTP_REQUIRED = BUILDER
            .comment("If true, users with MFA enrolled must also pass TOTP after sudo password")
            .define("sudoTotpRequired", true);

    public static final ModConfigSpec.BooleanValue AUTO_RESPONSE_ENABLED = BUILDER
            .comment("Automatically kick/temp-ban actors when certain alert rules hit thresholds")
            .define("autoResponseEnabled", true);

    public static final ModConfigSpec.IntValue AUTO_RESPONSE_SUDO_FAIL_THRESHOLD = BUILDER
            .comment("SUDO_FAIL count in window before auto temp-ban")
            .defineInRange("autoResponseSudoFailThreshold", 5, 2, 50);

    public static final ModConfigSpec.IntValue AUTO_RESPONSE_CMD_BURST_THRESHOLD = BUILDER
            .comment("CMD_BURST alerts in window before auto kick")
            .defineInRange("autoResponseCmdBurstThreshold", 3, 1, 50);

    public static final ModConfigSpec.IntValue AUTO_RESPONSE_WINDOW_SECONDS = BUILDER
            .comment("Sliding window for auto-response counters")
            .defineInRange("autoResponseWindowSeconds", 300, 30, 3600);

    public static final ModConfigSpec.IntValue AUTO_RESPONSE_TEMP_BAN_MINUTES = BUILDER
            .comment("Temp ban duration for auto-response")
            .defineInRange("autoResponseTempBanMinutes", 30, 1, 1440);

    public static final ModConfigSpec.BooleanValue AUTO_RESPONSE_BAN_IP = BUILDER
            .comment("Also queue ban_ip for the actor IP when available (Minecraft ban-ip, not OS iptables)")
            .define("autoResponseBanIp", false);

    public static final ModConfigSpec.BooleanValue AUDIT_BLOCK_SIGNING = BUILDER
            .comment("Periodically RSA-sign audit chain tip blocks for offline integrity proofs")
            .define("auditBlockSigning", true);

    public static final ModConfigSpec.IntValue AUDIT_BLOCK_SIGN_EVERY_EVENTS = BUILDER
            .comment("Sign a new audit block after this many chained events")
            .defineInRange("auditBlockSignEveryEvents", 100, 10, 10000);

    public static final ModConfigSpec.ConfigValue<String> OPS_API_TOKEN = BUILDER
            .comment("Bearer token for /api/ops/* mobile/bot endpoints (empty = disabled)")
            .define("opsApiToken", "");

    public static final ModConfigSpec.IntValue LOCKDOWN_SUDO_MINUTES = BUILDER
            .comment("sudo session minutes while lockdown/emergency mode is active")
            .defineInRange("lockdownSudoMinutes", 1, 1, 30);

    public static final ModConfigSpec.IntValue PERF_HISTORY_HOURS = BUILDER
            .comment("How many hours of perf_samples to retain for trend charts")
            .defineInRange("perfHistoryHours", 168, 1, 720);

    public static final ModConfigSpec.IntValue ANOMALY_CHAT_BURST = BUILDER
            .comment("Chat count in window that triggers CHAT_SPAM alert")
            .defineInRange("anomalyChatBurst", 10, 3, 200);

    public static final ModConfigSpec.IntValue ANOMALY_REDSTONE_BURST = BUILDER
            .comment("Redstone state-change count in window that triggers REDSTONE_BURST alert")
            .defineInRange("anomalyRedstoneBurst", 200, 20, 2000);

    public static final ModConfigSpec.BooleanValue ROLLBACK_ENABLED = BUILDER
            .comment("Enable block rollback (block_snapshots recording + panel rollback_blocks action)")
            .define("rollbackEnabled", true);

    public static final ModConfigSpec.BooleanValue INVENTORY_SNAPSHOT_ENABLED = BUILDER
            .comment("Enable versioned inventory snapshots for panel restore_inventory")
            .define("inventorySnapshotEnabled", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
