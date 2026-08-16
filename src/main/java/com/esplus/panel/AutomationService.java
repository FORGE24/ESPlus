package com.esplus.panel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.esplus.security.SecurityService;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;

/**
 * Automation engine — node-based task scheduler with interval/cron/manual triggers.
 * Each task has ordered nodes; each node has ordered operations.
 * Replaces the old single-purpose panel_schedules with a general-purpose blueprint model.
 */
public final class AutomationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final SecurityService security;
    private MinecraftServer server;
    private long lastTick;
    // P9: per-task retry counters (task_id -> consecutive failure count)
    private final java.util.Map<Long, Integer> failureCounts = new java.util.concurrent.ConcurrentHashMap<>();

    public AutomationService(SecurityService security) {
        this.security = security;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /** Called every tick from PanelActionProcessor. */
    public void tick() {
        if (server == null || !security.isReady() || security.database() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Only process every 20 ticks (1 second) to avoid DB spam
        if (now - lastTick < 1000) {
            return;
        }
        lastTick = now;
        processScheduledTasks();
    }

    // ── Schedule execution ────────────────────────────────────────

    private void processScheduledTasks() {
        var db = security.database();
        record DueTask(long id, String name, String triggerType, int intervalSecs, String cron) {}
        List<DueTask> due = new ArrayList<>();

        try (var ps = db.connection().prepareStatement(
                """
                SELECT id, name, trigger_type, trigger_interval_secs, trigger_cron FROM automation_tasks
                WHERE enabled = 1 AND trigger_type IN ('interval','cron')
                AND (next_run_at IS NULL OR next_run_at <= ?)
                ORDER BY next_run_at ASC LIMIT 20
                """)) {
            ps.setLong(1, System.currentTimeMillis());
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(new DueTask(rs.getLong("id"), rs.getString("name"),
                            rs.getString("trigger_type"), rs.getInt("trigger_interval_secs"),
                            rs.getString("trigger_cron")));
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Automation poll failed", ex);
            return;
        }

        for (DueTask task : due) {
            boolean ok = false;
            String error = null;
            try {
                ok = executeTask(task.id());
            } catch (Exception ex) {
                error = ex.getMessage();
                LOGGER.warn("Automation task {} failed", task.id(), ex);
            }
            long now = System.currentTimeMillis();
            long next = 0;
            try {
                if ("cron".equals(task.triggerType()) && task.cron() != null && !task.cron().isBlank()) {
                    next = CronUtil.nextRunMillis(task.cron(), now);
                } else if (task.intervalSecs() > 0) {
                    next = now + task.intervalSecs() * 1000L;
                }
                try (var upd = db.connection().prepareStatement(
                        """
                        UPDATE automation_tasks
                        SET last_run_at = ?, next_run_at = ?
                        WHERE id = ?
                        """)) {
                    upd.setLong(1, now);
                    upd.setLong(2, next > 0 ? next : 0);
                    upd.setLong(3, task.id());
                    upd.executeUpdate();
                }
                writeAutomationLog(task.id(), ok, error);
                security.audit(null, "automation_run", task.name() + "#" + task.id() + (ok ? " ok" : " fail"), ok);
            } catch (Exception ex) {
                LOGGER.warn("Automation task {} schedule update failed", task.id(), ex);
            }
        }
    }

    // ── Task execution ─────────────────────────────────────────────

    /** Execute all enabled nodes & operations for a task. Callable from schedule or manual trigger. */
    public boolean executeTask(long taskId) {
        var db = security.database();
        if (db == null || server == null) return false;

        record NodeOp(long nodeId, int opPosition, String actionType, String params) {}
        List<NodeOp> ops = new ArrayList<>();
        String taskName;

        try {
            // Get task name for logging
            try (var ps = db.connection().prepareStatement("SELECT name FROM automation_tasks WHERE id = ?")) {
                ps.setLong(1, taskId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) { LOGGER.debug("Automation task {} not found", taskId); return false; }
                    taskName = rs.getString("name");
                }
            }
            // Load enabled operations ordered by node position, then op position
            try (var ps = db.connection().prepareStatement(
                    """
                    SELECT o.node_id, o.position, o.action_type, o.params
                    FROM automation_operations o
                    JOIN automation_nodes n ON o.node_id = n.id
                    WHERE o.task_id = ? AND o.enabled = 1
                    ORDER BY n.position ASC, o.position ASC
                    """)) {
                ps.setLong(1, taskId);
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ops.add(new NodeOp(rs.getLong("node_id"), rs.getInt("position"),
                                rs.getString("action_type"), rs.getString("params")));
                    }
                }
            }

            if (ops.isEmpty()) {
                LOGGER.debug("Automation task {} has no enabled operations", taskId);
                return false;
            }

            LOGGER.info("[Automation] Executing task: {} ({} ops)", taskName, ops.size());
            int done = 0;
            Exception firstError = null;
            for (NodeOp op : ops) {
                try {
                    executeOperation(op.actionType(), op.params());
                    done++;
                } catch (Exception ex) {
                    if (firstError == null) firstError = ex;
                    LOGGER.warn("[Automation] Op {} failed in task {}: {}", op.actionType(), taskId, ex.getMessage());
                }
            }
            boolean ok = done == ops.size();
            LOGGER.info("[Automation] Task {} completed: {}/{} ops", taskName, done, ops.size());
            writeAutomationLog(taskId, ok, firstError != null ? firstError.getMessage() : null);
            return ok;

        } catch (Exception ex) {
            LOGGER.error("Automation task {} execution error", taskId, ex);
            writeAutomationLog(taskId, false, ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void executeOperation(String actionType, String params) {
        if (actionType == null || server == null) return;
        String p = substituteVars(params == null ? "" : params.trim());

        switch (actionType) {
            case "console_cmd" -> {
                String cmd = p;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                if (cmd.isBlank()) return;
                CommandSourceStack source = server.createCommandSourceStack();
                server.getCommands().performPrefixedCommand(source, cmd);
                LOGGER.info("[Automation] console_cmd: {}", cmd);
            }
            case "broadcast" -> {
                if (p.isBlank()) return;
                Component msg = Component.literal(p);
                server.getPlayerList().broadcastSystemMessage(msg, false);
            }
            case "save_all" -> {
                server.saveEverything(true, true, true);
            }
            case "restart_hint" -> {
                String hint = p.isBlank() ? "计划维护/重启窗口，请管理员执行重启" : p;
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[调度] " + hint), false);
            }
            case "kick_player" -> {
                String[] parts = p.split("\\|", 2);
                String playerName = parts[0].trim();
                String reason = parts.length > 1 ? parts[1].trim() : "Kicked by automation";
                if (playerName.isBlank()) return;
                ServerPlayer target = server.getPlayerList().getPlayerByName(playerName);
                if (target != null) {
                    target.connection.disconnect(Component.literal(reason));
                }
            }
            case "ban_player" -> {
                String[] parts = p.split("\\|", 2);
                String playerName = parts[0].trim();
                String reason = parts.length > 1 ? parts[1].trim() : "Banned by automation";
                if (playerName.isBlank()) return;
                Optional<GameProfile> gp = resolveByName(playerName);
                if (gp.isPresent()) {
                    UserBanList bans = server.getPlayerList().getBans();
                    bans.add(new UserBanListEntry(gp.get(), null, "ESPlus-Automation", null, reason));
                    ServerPlayer online = server.getPlayerList().getPlayer(gp.get().getId());
                    if (online != null) online.connection.disconnect(Component.literal(reason));
                }
            }
            case "clear_items" -> {
                int removed = 0;
                for (var level : server.getAllLevels()) {
                    try {
                        for (var e : level.getEntities().getAll()) {
                            if (e instanceof net.minecraft.world.entity.item.ItemEntity) { e.discard(); removed++; }
                        }
                    } catch (Exception ex) { LOGGER.warn("[Automation] clear_items level failed", ex); }
                }
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[自动清理] 已清除 " + removed + " 个地面物品"), false);
            }
            case "kill_monsters" -> {
                int removed = 0;
                for (var level : server.getAllLevels()) {
                    try {
                        for (var e : level.getEntities().getAll()) {
                            if (e instanceof net.minecraft.world.entity.monster.Monster) { e.discard(); removed++; }
                        }
                    } catch (Exception ex) { LOGGER.warn("[Automation] kill_monsters level failed", ex); }
                }
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[自动清理] 已清除 " + removed + " 只怪物"), false);
            }
            case "title_broadcast" -> {
                String[] parts = p.split("\\|", 2);
                String titleText = parts[0].trim();
                String subtitleText = parts.length > 1 ? parts[1].trim() : "";
                if (titleText.isBlank()) return;
                Component title = Component.literal(titleText);
                Component subtitle = subtitleText.isBlank() ? null : Component.literal(subtitleText);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
                    player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
                    if (subtitle != null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
                }
            }
            case "maintenance_kick" -> {
                String reason = p.isBlank() ? "服务器维护中，请稍后再试" : p;
                List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
                int kicked = 0;
                for (ServerPlayer player : players) {
                    if (!server.getPlayerList().isOp(player.getGameProfile())) { player.connection.disconnect(Component.literal(reason)); kicked++; }
                }
                LOGGER.info("[Automation] maintenance_kick: {} kicked", kicked);
            }
            case "conditional_if" -> {
                // params: condition=value (e.g., "online>=1", "time=06..23")
                // This is evaluated BEFORE the op; if false, we throw to skip
                if (!evaluateCondition(p)) {
                    throw new AutomationConditionSkippedException("Condition not met: " + p);
                }
            }
            case "wait_delay" -> {
                // params: milliseconds or "5s" / "3m"
                long delayMs = parseDelay(p);
                if (delayMs > 0) {
                    try { Thread.sleep(Math.min(delayMs, 60000)); } catch (InterruptedException ignored) { }
                }
            }
            default -> LOGGER.debug("[Automation] Unknown action: {}", actionType);
        }
    }

    // ── P6: Variable substitution ──────────────────────────────────

    private String substituteVars(String s) {
        if (s == null || !s.contains("{{")) return s;
        Matcher m = VAR_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = resolveVar(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String resolveVar(String key) {
        if (server == null) return "{{" + key + "}}";
        return switch (key) {
            case "time" -> DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now());
            case "date" -> DateTimeFormatter.ofPattern("yyyy-MM-dd").format(java.time.LocalDate.now());
            case "datetime" -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(java.time.LocalDateTime.now());
            case "online" -> String.valueOf(server.getPlayerList().getPlayerCount());
            case "max_players" -> String.valueOf(server.getMaxPlayers());
            case "tps" -> {
                long avgNanos = server.getAverageTickTimeNanos();
                yield String.format("%.1f", avgNanos > 0 ? (1_000_000_000.0 / avgNanos) : 20.0);
            }
            case "difficulty" -> server.getWorldData().getDifficulty().name();
            case "day" -> String.valueOf(server.overworld() != null ? server.overworld().getDayTime() / 24000L : 0);
            case "uptime" -> {
                long uptimeMs = (long) server.getTickCount() * 50L;
                yield formatDuration(uptimeMs);
            }
            case "random_player" -> {
                var list = server.getPlayerList().getPlayers();
                yield list.isEmpty() ? "(none)" : list.get((int) (Math.random() * list.size())).getGameProfile().getName();
            }
            default -> "{{" + key + "}}";
        };
    }

    private String formatDuration(long ms) {
        long s = ms / 1000, m = s / 60, h = m / 60;
        if (h > 0) return h + "h" + (m % 60) + "m";
        if (m > 0) return m + "m" + (s % 60) + "s";
        return s + "s";
    }

    // ── P7: Conditional evaluation ─────────────────────────────────

    private boolean evaluateCondition(String condition) {
        if (condition == null || condition.isBlank()) return true;
        String cond = condition.trim();
        try {
            if (cond.startsWith("online")) {
                int online = server.getPlayerList().getPlayerCount();
                return compareInt(cond, "online", online);
            }
            if (cond.startsWith("time=")) {
                String range = cond.substring(5).trim(); // "06..23"
                if (range.contains("..")) {
                    String[] parts = range.split("\\.\\.");
                    int start = Integer.parseInt(parts[0]), end = Integer.parseInt(parts[1]);
                    int now = java.time.LocalTime.now().getHour();
                    return now >= start && now <= end;
                }
                return false;
            }
            if (cond.startsWith("playerdim=")) {
                String dim = cond.substring(10).trim();
                return server.getPlayerList().getPlayers().stream()
                        .anyMatch(p -> p.level().dimension().location().toString().contains(dim));
            }
            if (cond.equals("has_players")) {
                return server.getPlayerList().getPlayerCount() > 0;
            }
            if (cond.startsWith("tps<")) {
                long avgNanos = server.getAverageTickTimeNanos();
                double currentTps = avgNanos > 0 ? (1_000_000_000.0 / avgNanos) : 20.0;
                double threshold = Double.parseDouble(cond.substring(4));
                return currentTps < threshold;
            }
            LOGGER.debug("[Automation] Unknown condition: {}", cond);
        } catch (Exception ex) {
            LOGGER.warn("[Automation] Condition eval failed for '{}'", cond, ex);
        }
        return false;
    }

    private boolean compareInt(String expr, String prefix, int actual) {
        String rest = expr.substring(prefix.length()).trim();
        if (rest.startsWith(">=")) return actual >= Integer.parseInt(rest.substring(2).trim());
        if (rest.startsWith("<=")) return actual <= Integer.parseInt(rest.substring(2).trim());
        if (rest.startsWith(">")) return actual > Integer.parseInt(rest.substring(1).trim());
        if (rest.startsWith("<")) return actual < Integer.parseInt(rest.substring(1).trim());
        if (rest.startsWith("==")) return actual == Integer.parseInt(rest.substring(2).trim());
        if (rest.startsWith("!=")) return actual != Integer.parseInt(rest.substring(2).trim());
        return false;
    }

    // ── P8/P9: Delay + Retry ───────────────────────────────────────

    private long parseDelay(String s) {
        if (s == null || s.isBlank()) return 0;
        s = s.trim();
        try {
            if (s.endsWith("ms")) return Long.parseLong(s.substring(0, s.length() - 2));
            if (s.endsWith("s")) return Long.parseLong(s.substring(0, s.length() - 1)) * 1000L;
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60000L;
            return Long.parseLong(s);
        } catch (NumberFormatException e) { return 0; }
    }

    private Optional<GameProfile> resolveByName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name.trim());
        if (online != null) return Optional.of(online.getGameProfile());
        return server.getProfileCache().get(name.trim());
    }

    private void writeAutomationLog(long taskId, boolean success, String error) {
        var db = security.database();
        if (db == null) return;
        try (var ps = db.connection().prepareStatement(
                """
                INSERT INTO automation_logs (task_id, success, error, created_at)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setLong(1, taskId);
            ps.setInt(2, success ? 1 : 0);
            ps.setString(3, error);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Exception ex) {
            LOGGER.debug("Failed to write automation log", ex);
        }
    }

    // ── Ensure tables ──────────────────────────────────────────────

    public void ensureTables() {
        var db = security.database();
        if (db == null) return;
        try {
            db.connection().createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS automation_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        description TEXT,
                        trigger_type TEXT NOT NULL DEFAULT 'manual',
                        trigger_interval_secs INTEGER NOT NULL DEFAULT 0,
                        trigger_cron TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        last_run_at INTEGER,
                        next_run_at INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            db.connection().createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS automation_nodes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        task_id INTEGER NOT NULL,
                        name TEXT,
                        position INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                    """);
            db.connection().createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS automation_operations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        node_id INTEGER NOT NULL,
                        task_id INTEGER NOT NULL,
                        position INTEGER NOT NULL DEFAULT 0,
                        action_type TEXT NOT NULL,
                        params TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL
                    )
                    """);
            db.connection().createStatement().execute("""
                    CREATE TABLE IF NOT EXISTS automation_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        task_id INTEGER NOT NULL,
                        success INTEGER NOT NULL DEFAULT 1,
                        error TEXT,
                        created_at INTEGER NOT NULL
                    )
                    """);
        } catch (Exception ex) {
            LOGGER.error("Failed to create automation tables", ex);
        }
    }
}
