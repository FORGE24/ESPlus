package com.esplus.panel;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.esplus.Config;
import com.esplus.ESPlus;
import com.esplus.security.SecurityService;
import com.esplus.security.db.SqliteDatabase;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;

/**
 * Mirrors online players / bans / whitelist / runtime / gamerules into SQLite for the panel.
 */
public final class ServerSnapshotSync {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INTERVAL_TICKS = 20;
    private static final int INV_EVERY_SYNCS = 5;
    private static final long PERF_KEEP_FALLBACK_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int RETENTION_EVERY_TICKS = 20 * 60 * 30;

    private final SecurityService security;
    private int tickCounter;
    private int retentionCounter;
    private int syncCounter;

    public ServerSnapshotSync(SecurityService security) {
        this.security = security;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!security.isReady() || security.database() == null) {
            return;
        }
        tickCounter++;
        retentionCounter++;
        if (tickCounter >= INTERVAL_TICKS) {
            tickCounter = 0;
            try {
                MinecraftServer server = event.getServer();
                SqliteDatabase db = security.database();
                synchronized (db.lock()) {
                    Connection conn = db.connection();
                    boolean previous = conn.getAutoCommit();
                    conn.setAutoCommit(false);
                    try {
                        sync(server, db);
                        syncCounter++;
                        if (syncCounter >= INV_EVERY_SYNCS) {
                            syncCounter = 0;
                            syncInventories(server, db);
                        }
                        conn.commit();
                    } catch (Exception ex) {
                        try {
                            conn.rollback();
                        } catch (SQLException ignored) {
                            // keep original
                        }
                        throw ex;
                    } finally {
                        try {
                            conn.setAutoCommit(previous);
                        } catch (SQLException ignored) {
                            // ignore
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.debug("Server snapshot sync failed", ex);
            }
        }
        if (retentionCounter >= RETENTION_EVERY_TICKS) {
            retentionCounter = 0;
            try {
                RetentionCleanup.run(security.database(), Config.AUDIT_RETENTION_DAYS.getAsInt());
            } catch (Exception ex) {
                LOGGER.debug("Retention cleanup failed", ex);
            }
        }
    }

    private static void sync(MinecraftServer server, SqliteDatabase db) throws SQLException {
        long now = System.currentTimeMillis();
        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM online_players");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO online_players
                (uuid, name, ping, dimension, x, y, z, game_mode, updated_at, health, food, xp_level, is_op)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                insert.setString(1, player.getUUID().toString());
                insert.setString(2, player.getGameProfile().getName());
                insert.setInt(3, player.connection.latency());
                insert.setString(4, player.level().dimension().location().toString());
                insert.setDouble(5, player.getX());
                insert.setDouble(6, player.getY());
                insert.setDouble(7, player.getZ());
                insert.setString(8, player.gameMode.getGameModeForPlayer().getName());
                insert.setLong(9, now);
                insert.setDouble(10, player.getHealth());
                insert.setInt(11, player.getFoodData().getFoodLevel());
                insert.setInt(12, player.experienceLevel);
                insert.setInt(13, server.getPlayerList().isOp(player.getGameProfile()) ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        Date nowDate = new Date();
        for (UserBanListEntry entry : List.copyOf(server.getPlayerList().getBans().getEntries())) {
            Date expires = entry.getExpires();
            if (expires != null && expires.before(nowDate)) {
                GameProfile profile = userOf(entry);
                if (profile != null) {
                    server.getPlayerList().getBans().remove(profile);
                }
            }
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_bans");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_bans (uuid, name, reason, source, created_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (UserBanListEntry entry : server.getPlayerList().getBans().getEntries()) {
                GameProfile profile = userOf(entry);
                if (profile == null) {
                    continue;
                }
                insert.setString(1, profile.getId().toString());
                insert.setString(2, profile.getName());
                insert.setString(3, entry.getReason());
                insert.setString(4, entry.getSource());
                insert.setLong(5, entry.getCreated() == null ? 0L : entry.getCreated().getTime());
                Date expires = entry.getExpires();
                insert.setLong(6, expires == null ? 0L : expires.getTime());
                insert.setLong(7, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_whitelist");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_whitelist (uuid, name, updated_at) VALUES (?, ?, ?)
                """)) {
            for (UserWhiteListEntry entry : server.getPlayerList().getWhiteList().getEntries()) {
                GameProfile profile = userOf(entry);
                if (profile == null) {
                    continue;
                }
                insert.setString(1, profile.getId().toString());
                insert.setString(2, profile.getName());
                insert.setLong(3, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_ip_bans");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_ip_bans (ip, reason, source, created_at, expires_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (IpBanListEntry entry : server.getPlayerList().getIpBans().getEntries()) {
                String ip = userOf(entry);
                if (ip == null || ip.isBlank()) {
                    continue;
                }
                insert.setString(1, ip);
                insert.setString(2, entry.getReason());
                insert.setString(3, entry.getSource());
                insert.setLong(4, entry.getCreated() == null ? 0L : entry.getCreated().getTime());
                Date expires = entry.getExpires();
                insert.setLong(5, expires == null ? 0L : expires.getTime());
                insert.setLong(6, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        Map<String, Integer> typeCounts = new HashMap<>();
        int entityCount = 0;
        int chunkCount = 0;
        int worldCount = 0;
        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_dimensions");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_dimensions
                (dimension, player_count, entity_count, chunk_count, day_time, raining, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ServerLevel level : server.getAllLevels()) {
                worldCount++;
                AtomicInteger entities = new AtomicInteger();
                for (Entity entity : level.getAllEntities()) {
                    entities.incrementAndGet();
                    if (!(entity instanceof ServerPlayer)) {
                        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                        typeCounts.merge(type, 1, Integer::sum);
                    }
                }
                int levelEntities = entities.get();
                int levelChunks = level.getChunkSource().getLoadedChunksCount();
                entityCount += levelEntities;
                chunkCount += levelChunks;
                insert.setString(1, level.dimension().location().toString());
                insert.setInt(2, level.players().size());
                insert.setInt(3, levelEntities);
                insert.setInt(4, levelChunks);
                insert.setLong(5, level.getDayTime());
                insert.setInt(6, level.isRaining() ? 1 : 0);
                insert.setLong(7, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_entity_types");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                "INSERT INTO server_entity_types (entity_type, count, updated_at) VALUES (?, ?, ?)")) {
            typeCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(80)
                    .forEach(e -> {
                        try {
                            insert.setString(1, e.getKey());
                            insert.setInt(2, e.getValue());
                            insert.setLong(3, now);
                            insert.addBatch();
                        } catch (SQLException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_gamerules");
        }
        GameRules rules = server.getGameRules();
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_gamerules (rule_id, category, value_type, value, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                    writeRule(insert, key, "bool", rules.getRule(key).serialize(), now);
                }

                @Override
                public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                    writeRule(insert, key, "int", rules.getRule(key).serialize(), now);
                }
            });
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_bossbars");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_bossbars (id, name, color, overlay, value, max_value, visible, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (CustomBossEvent bar : server.getCustomBossEvents().getEvents()) {
                insert.setString(1, bar.getTextId().toString());
                insert.setString(2, bar.getName().getString());
                insert.setString(3, bar.getColor().getName());
                insert.setString(4, bar.getOverlay().getName());
                insert.setInt(5, bar.getValue());
                insert.setInt(6, bar.getMax());
                insert.setInt(7, bar.isVisible() ? 1 : 0);
                insert.setLong(8, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        Scoreboard scoreboard = server.getScoreboard();
        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_scoreboard");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_scoreboard (name, criteria, display_name, display_slot, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (Objective objective : scoreboard.getObjectives()) {
                String slots = "";
                StringBuilder sb = new StringBuilder();
                for (DisplaySlot slot : DisplaySlot.values()) {
                    Objective shown = scoreboard.getDisplayObjective(slot);
                    if (shown != null && shown.getName().equals(objective.getName())) {
                        if (!sb.isEmpty()) {
                            sb.append(',');
                        }
                        sb.append(slot.getSerializedName());
                    }
                }
                slots = sb.toString();
                insert.setString(1, objective.getName());
                insert.setString(2, objective.getCriteria().getName());
                insert.setString(3, objective.getDisplayName().getString());
                insert.setString(4, slots.isEmpty() ? null : slots);
                insert.setLong(5, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM server_teams");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO server_teams
                (name, display_name, color, friendly_fire, see_friendly_invisibles, members, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (PlayerTeam team : scoreboard.getPlayerTeams()) {
                insert.setString(1, team.getName());
                insert.setString(2, team.getDisplayName().getString());
                insert.setString(3, team.getColor().getSerializedName());
                insert.setInt(4, team.isAllowFriendlyFire() ? 1 : 0);
                insert.setInt(5, team.canSeeFriendlyInvisibles() ? 1 : 0);
                insert.setString(6, String.join(",", team.getPlayers()));
                insert.setLong(7, now);
                insert.addBatch();
            }
            insert.executeBatch();
        }

        ServerLevel overworld = server.overworld();
        WorldBorder border = overworld.getWorldBorder();
        BlockPos spawn = overworld.getSharedSpawnPos();
        double msptMs = server.getAverageTickTimeNanos() / 1_000_000.0;
        double tps = msptMs <= 0 ? 20.0 : Math.min(20.0, 1000.0 / msptMs);
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        String modVersion = ModList.get().getModContainerById(ESPlus.MODID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
        String protectedCmds = String.join(",", Config.PROTECTED_COMMANDS.get());

        try (PreparedStatement upsert = db.connection().prepareStatement(
                """
                INSERT INTO server_runtime (
                    id, whitelist_enabled, updated_at, player_count, max_players, mspt_ms, tps_approx,
                    entity_count, chunk_count, world_count, memory_used_mb, memory_max_mb,
                    game_port, online_mode, motd, difficulty, hardcore, default_gamemode,
                    day_time, raining, thundering, border_size, border_center_x, border_center_z,
                    border_warning, border_damage, uptime_ticks, mc_version, neoforge_version, mod_version,
                    spawn_x, spawn_y, spawn_z, spawn_angle,
                    sudo_session_minutes, max_failed_attempts, lock_minutes, protected_commands, audit_retention_days
                ) VALUES (
                    1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?
                )
                ON CONFLICT(id) DO UPDATE SET
                    whitelist_enabled = excluded.whitelist_enabled,
                    updated_at = excluded.updated_at,
                    player_count = excluded.player_count,
                    max_players = excluded.max_players,
                    mspt_ms = excluded.mspt_ms,
                    tps_approx = excluded.tps_approx,
                    entity_count = excluded.entity_count,
                    chunk_count = excluded.chunk_count,
                    world_count = excluded.world_count,
                    memory_used_mb = excluded.memory_used_mb,
                    memory_max_mb = excluded.memory_max_mb,
                    game_port = excluded.game_port,
                    online_mode = excluded.online_mode,
                    motd = excluded.motd,
                    difficulty = excluded.difficulty,
                    hardcore = excluded.hardcore,
                    default_gamemode = excluded.default_gamemode,
                    day_time = excluded.day_time,
                    raining = excluded.raining,
                    thundering = excluded.thundering,
                    border_size = excluded.border_size,
                    border_center_x = excluded.border_center_x,
                    border_center_z = excluded.border_center_z,
                    border_warning = excluded.border_warning,
                    border_damage = excluded.border_damage,
                    uptime_ticks = excluded.uptime_ticks,
                    mc_version = excluded.mc_version,
                    neoforge_version = excluded.neoforge_version,
                    mod_version = excluded.mod_version,
                    spawn_x = excluded.spawn_x,
                    spawn_y = excluded.spawn_y,
                    spawn_z = excluded.spawn_z,
                    spawn_angle = excluded.spawn_angle,
                    sudo_session_minutes = excluded.sudo_session_minutes,
                    max_failed_attempts = excluded.max_failed_attempts,
                    lock_minutes = excluded.lock_minutes,
                    protected_commands = excluded.protected_commands,
                    audit_retention_days = excluded.audit_retention_days
                """)) {
            int i = 1;
            upsert.setInt(i++, server.getPlayerList().isUsingWhitelist() ? 1 : 0);
            upsert.setLong(i++, now);
            upsert.setInt(i++, server.getPlayerCount());
            upsert.setInt(i++, server.getMaxPlayers());
            upsert.setDouble(i++, msptMs);
            upsert.setDouble(i++, tps);
            upsert.setInt(i++, entityCount);
            upsert.setInt(i++, chunkCount);
            upsert.setInt(i++, worldCount);
            upsert.setInt(i++, (int) usedMb);
            upsert.setInt(i++, (int) maxMb);
            upsert.setInt(i++, server.getPort());
            upsert.setInt(i++, server.usesAuthentication() ? 1 : 0);
            upsert.setString(i++, server.getMotd());
            upsert.setString(i++, server.getWorldData().getDifficulty().getKey());
            upsert.setInt(i++, server.isHardcore() ? 1 : 0);
            upsert.setString(i++, server.getDefaultGameType().getName());
            upsert.setLong(i++, overworld.getDayTime());
            upsert.setInt(i++, overworld.isRaining() ? 1 : 0);
            upsert.setInt(i++, overworld.isThundering() ? 1 : 0);
            upsert.setDouble(i++, border.getSize());
            upsert.setDouble(i++, border.getCenterX());
            upsert.setDouble(i++, border.getCenterZ());
            upsert.setInt(i++, border.getWarningBlocks());
            upsert.setDouble(i++, border.getDamagePerBlock());
            upsert.setInt(i++, server.getTickCount());
            upsert.setString(i++, SharedConstants.getCurrentVersion().getName());
            upsert.setString(i++, NeoForgeVersion.getVersion());
            upsert.setString(i++, modVersion);
            upsert.setDouble(i++, spawn.getX());
            upsert.setDouble(i++, spawn.getY());
            upsert.setDouble(i++, spawn.getZ());
            upsert.setDouble(i++, overworld.getSharedSpawnAngle());
            upsert.setInt(i++, Config.SUDO_SESSION_MINUTES.getAsInt());
            upsert.setInt(i++, Config.MAX_FAILED_ATTEMPTS.getAsInt());
            upsert.setInt(i++, Config.LOCK_MINUTES.getAsInt());
            upsert.setString(i++, protectedCmds);
            upsert.setInt(i, Config.AUDIT_RETENTION_DAYS.getAsInt());
            upsert.executeUpdate();
        }
        try (PreparedStatement idle = db.connection().prepareStatement(
                "UPDATE server_runtime SET idle_timeout = ? WHERE id = 1")) {
            idle.setInt(1, server.getPlayerIdleTimeout());
            idle.executeUpdate();
        }
        String entityHint = entityCount > 8000
                ? "Entity tick pressure likely high (" + entityCount + " entities). Consider cleanup."
                : (entityCount > 4000 ? "Entity count elevated (" + entityCount + ")." : "Entity load normal.");
        try (PreparedStatement extra = db.connection().prepareStatement(
                """
                UPDATE server_runtime SET
                  server_id = ?, server_name = ?, entity_share_hint = ?, approval_enabled = ?
                WHERE id = 1
                """)) {
            extra.setString(1, Config.SERVER_ID.get());
            extra.setString(2, Config.SERVER_NAME.get());
            extra.setString(3, entityHint);
            extra.setInt(4, Config.APPROVAL_ENABLED.getAsBoolean() ? 1 : 0);
            extra.executeUpdate();
        }
        recordPerfSample(db, now, tps, msptMs, (int) usedMb, entityCount, server.getPlayerCount(), chunkCount, entityHint);
    }

    private static void recordPerfSample(
            SqliteDatabase db,
            long now,
            double tps,
            double msptMs,
            int usedMb,
            int entityCount,
            int playerCount,
            int chunkCount,
            String entityHint
    ) throws SQLException {
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO perf_samples (ts, tps, mspt_ms, memory_used_mb, entity_count, player_count, chunk_count, entity_share_hint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setLong(1, now);
            insert.setDouble(2, tps);
            insert.setDouble(3, msptMs);
            insert.setInt(4, usedMb);
            insert.setInt(5, entityCount);
            insert.setInt(6, playerCount);
            insert.setInt(7, chunkCount);
            insert.setString(8, entityHint);
            insert.executeUpdate();
        }
        try (PreparedStatement prune = db.connection().prepareStatement(
                "DELETE FROM perf_samples WHERE ts < ?")) {
            long keepMs = PERF_KEEP_FALLBACK_MS;
            try {
                keepMs = Math.max(60L * 60L * 1000L, Config.PERF_HISTORY_HOURS.getAsInt() * 3600L * 1000L);
            } catch (Exception ignored) {
                // config not ready
            }
            prune.setLong(1, now - keepMs);
            prune.executeUpdate();
        }
    }

    private static void syncInventories(MinecraftServer server, SqliteDatabase db) throws SQLException {
        long now = System.currentTimeMillis();
        try (Statement wipe = db.connection().createStatement()) {
            wipe.executeUpdate("DELETE FROM player_inventory");
        }
        try (PreparedStatement insert = db.connection().prepareStatement(
                """
                INSERT INTO player_inventory
                (uuid, name, section, slot, item_id, count, display_name, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String uuid = player.getUUID().toString();
                String name = player.getGameProfile().getName();
                Inventory inv = player.getInventory();
                for (int i = 0; i < 36; i++) {
                    addInvRow(insert, uuid, name, "main", i, inv.getItem(i), now);
                }
                for (int i = 0; i < inv.armor.size(); i++) {
                    addInvRow(insert, uuid, name, "armor", i, inv.armor.get(i), now);
                }
                if (!inv.offhand.isEmpty()) {
                    addInvRow(insert, uuid, name, "offhand", 0, inv.offhand.get(0), now);
                }
                var ender = player.getEnderChestInventory();
                for (int i = 0; i < ender.getContainerSize(); i++) {
                    addInvRow(insert, uuid, name, "ender", i, ender.getItem(i), now);
                }
            }
            insert.executeBatch();
        }
    }

    private static void addInvRow(
            PreparedStatement insert,
            String uuid,
            String name,
            String section,
            int slot,
            ItemStack stack,
            long now
    ) throws SQLException {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String display = stack.getHoverName().getString();
        if (display != null && display.length() > 64) {
            display = display.substring(0, 64);
        }
        insert.setString(1, uuid);
        insert.setString(2, name);
        insert.setString(3, section);
        insert.setInt(4, slot);
        insert.setString(5, itemId);
        insert.setInt(6, stack.getCount());
        insert.setString(7, display);
        insert.setLong(8, now);
        insert.addBatch();
    }

    private static void writeRule(
            PreparedStatement insert,
            GameRules.Key<?> key,
            String valueType,
            String value,
            long now
    ) {
        try {
            insert.setString(1, key.getId());
            insert.setString(2, key.getCategory().name());
            insert.setString(3, valueType);
            insert.setString(4, value);
            insert.setLong(5, now);
            insert.addBatch();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T userOf(StoredUserEntry<T> entry) {
        try {
            Method method = StoredUserEntry.class.getDeclaredMethod("getUser");
            method.setAccessible(true);
            return (T) method.invoke(entry);
        } catch (ReflectiveOperationException ex) {
            LOGGER.debug("Unable to read StoredUserEntry user", ex);
            return null;
        }
    }
}
