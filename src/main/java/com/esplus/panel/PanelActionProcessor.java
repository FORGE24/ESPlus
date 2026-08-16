package com.esplus.panel;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.esplus.Config;
import com.esplus.security.SecurityService;
import com.esplus.security.db.PanelActionDao;
import com.esplus.security.db.PanelActionDao.PanelAction;
import com.esplus.security.db.SqliteDatabase;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Applies panel-queued actions and flushes captured debug logs to SQLite.
 */
public final class PanelActionProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INTERVAL_TICKS = 20;

    private final SecurityService security;
    private final AutomationService automation;
    private int tickCounter;

    public PanelActionProcessor(SecurityService security) {
        this.security = security;
        this.automation = new AutomationService(security);
    }

    public AutomationService automation() {
        return automation;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!security.isReady()) {
            return;
        }
        tickCounter++;
        if (tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        security.logCapture().flush();
        MinecraftServer server = event.getServer();
        if (automation != null) automation.tick();
        if (automation != null) automation.setServer(server);
        process(server);
        processSchedules(server);
    }

    private void process(MinecraftServer server) {
        if (automation != null) automation.ensureTables();
        PanelActionDao dao = security.panelActionDao();
        if (dao == null) {
            return;
        }
        try {
            for (PanelAction action : dao.claimPending(20)) {
                try {
                    switch (action.action()) {
                        case "grant_op" -> applyOp(server, dao, action, true);
                        case "revoke_op" -> applyOp(server, dao, action, false);
                        case "console_cmd" -> applyConsole(server, dao, action);
                        case "kick_player" -> applyKick(server, dao, action);
                        case "ban_player" -> applyBan(server, dao, action, null);
                        case "temp_ban_player" -> applyTempBan(server, dao, action);
                        case "unban_player" -> applyUnban(server, dao, action);
                        case "broadcast" -> applyBroadcast(server, dao, action);
                        case "tell_player" -> applyTell(server, dao, action);
                        case "whitelist_add" -> applyWhitelist(server, dao, action, true);
                        case "whitelist_remove" -> applyWhitelist(server, dao, action, false);
                        case "whitelist_set" -> applyWhitelistToggle(server, dao, action);
                        case "retention_cleanup" -> applyRetention(dao, action);
                        case "set_gamerule" -> applySetGamerule(server, dao, action);
                        case "set_time" -> applySetTime(server, dao, action);
                        case "set_weather" -> applySetWeather(server, dao, action);
                        case "set_difficulty" -> applySetDifficulty(server, dao, action);
                        case "set_default_gamemode" -> applySetDefaultGamemode(server, dao, action);
                        case "set_worldborder" -> applySetWorldBorder(server, dao, action);
                        case "set_player_gamemode" -> applySetPlayerGamemode(server, dao, action);
                        case "clear_inventory" -> applyClearInventory(server, dao, action);
                        case "heal_player" -> applyHealPlayer(server, dao, action);
                        case "feed_player" -> applyFeedPlayer(server, dao, action);
                        case "extinguish_player" -> applyExtinguishPlayer(server, dao, action);
                        case "title_broadcast" -> applyTitle(server, dao, action);
                        case "kill_entities" -> applyKillEntities(server, dao, action);
                        case "give_item" -> applyGiveItem(server, dao, action);
                        case "save_all" -> applySaveAll(server, dao, action);
                        case "reload_server" -> applyReload(server, dao, action);
                        case "stop_server" -> applyStop(server, dao, action);
                        case "bossbar_create" -> applyBossbarCreate(server, dao, action);
                        case "bossbar_update" -> applyBossbarUpdate(server, dao, action);
                        case "bossbar_remove" -> applyBossbarRemove(server, dao, action);
                        case "scoreboard_add" -> applyScoreboardAdd(server, dao, action);
                        case "scoreboard_remove" -> applyScoreboardRemove(server, dao, action);
                        case "scoreboard_display" -> applyScoreboardDisplay(server, dao, action);
                        case "team_create" -> applyTeamCreate(server, dao, action);
                        case "team_remove" -> applyTeamRemove(server, dao, action);
                        case "team_join" -> applyTeamJoin(server, dao, action);
                        case "team_leave" -> applyTeamLeave(server, dao, action);
                        case "team_modify" -> applyTeamModify(server, dao, action);
                        case "set_worldspawn" -> applySetWorldSpawn(server, dao, action);
                        case "teleport_player" -> applyTeleport(server, dao, action);
                        case "clear_enderchest" -> applyClearEnderChest(server, dao, action);
                        case "save_on" -> applySaveToggle(server, dao, action, true);
                        case "save_off" -> applySaveToggle(server, dao, action, false);
                        case "pardon_ip" -> applyPardonIp(server, dao, action);
                        case "ban_ip" -> applyBanIp(server, dao, action);
                        case "set_spawnpoint" -> applySetSpawnpoint(server, dao, action);
                        case "clear_effects" -> applyClearEffects(server, dao, action);
                        case "give_effect" -> applyGiveEffect(server, dao, action);
                        case "scoreboard_set" -> applyScoreboardSet(server, dao, action);
                        case "set_motd" -> applySetMotd(server, dao, action);
                        case "maintenance_kick" -> applyMaintenanceKick(server, dao, action);
                        case "clear_maintenance" -> applyClearMaintenance(server, dao, action);
                        case "set_idle_timeout" -> applyIdleTimeout(server, dao, action);
                        case "create_security_snapshot" -> applyCreateSnapshot(server, dao, action);
                        case "restore_snapshot" -> applyRestoreSnapshot(server, dao, action);
                        case "lockdown_on" -> applyLockdown(server, dao, action, true);
                        case "lockdown_off" -> applyLockdown(server, dao, action, false);
                        case "automation_trigger" -> applyAutomationTrigger(dao, action);
                        case "rollback_blocks" -> applyRollbackBlocks(server, dao, action);
                        case "restore_inventory" -> applyRestoreInventory(server, dao, action);
                        default -> dao.markDone(action.id(), false, "unknown_action");
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Failed panel action id={}", action.id(), ex);
                    try {
                        dao.markDone(action.id(), false, ex.getMessage());
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Panel action poll failed", ex);
        }
    }

    private void applyConsole(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String raw = action.payload();
        if (raw == null || raw.isBlank()) {
            raw = action.targetName();
        }
        if (raw == null || raw.isBlank()) {
            dao.markDone(action.id(), false, "empty_command");
            return;
        }
        String command = raw.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        LOGGER.info("[PanelConsole] executing: {}", command);
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, command);
        dao.markDone(action.id(), true, "executed cmd=" + command);
        security.audit(null, "panel_console_cmd", command, true);
    }

    private void applyOp(MinecraftServer server, PanelActionDao dao, PanelAction action, boolean grant) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        PlayerList list = server.getPlayerList();
        if (grant) {
            list.op(gp);
            dao.markDone(action.id(), true, "opped:" + gp.getName());
            security.audit(gp.getId(), "panel_grant_op", gp.getName(), true);
            notifyPlayer(server, gp.getId(), Component.literal("[ES+] 你已被面板授予 OP，请使用 /setoppw 设置 sudo 密码；受保护指令还需在面板将角色提升为 admin/moderator。"));
            LOGGER.info("Panel granted OP to {} ({})", gp.getName(), gp.getId());
        } else {
            list.deop(gp);
            dao.markDone(action.id(), true, "deopped:" + gp.getName());
            security.audit(gp.getId(), "panel_revoke_op", gp.getName(), true);
            notifyPlayer(server, gp.getId(), Component.literal("[ES+] 你的 OP 已被面板撤销。"));
            LOGGER.info("Panel revoked OP from {} ({})", gp.getName(), gp.getId());
        }
    }

    private void applyKick(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        ServerPlayer online = server.getPlayerList().getPlayer(gp.getId());
        if (online == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String reason = sanitizeReason(action.payload(), "Kicked by panel");
        online.connection.disconnect(Component.literal(reason));
        dao.markDone(action.id(), true, "kicked:" + gp.getName());
        security.audit(gp.getId(), "panel_kick", reason, true);
        LOGGER.info("Panel kicked {} ({}) reason={}", gp.getName(), gp.getId(), reason);
    }

    private void applyTempBan(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String payload = action.payload() == null ? "" : action.payload().trim();
        int minutes = 60;
        int sep = payload.indexOf('|');
        if (sep > 0) {
            try {
                minutes = Integer.parseInt(payload.substring(0, sep).trim());
            } catch (NumberFormatException ignored) {
                minutes = 60;
            }
        }
        minutes = Math.max(1, Math.min(minutes, 60 * 24 * 365));
        Date expires = new Date(System.currentTimeMillis() + minutes * 60_000L);
        applyBan(server, dao, action, expires);
    }

    private void applyBan(MinecraftServer server, PanelActionDao dao, PanelAction action, Date expires) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        String reason = "temp_ban_player".equals(action.action())
                ? sanitizeReason(tailAfterPipe(action.payload()), "Temporarily banned by panel")
                : sanitizeReason(action.payload(), "Banned by panel");
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(new UserBanListEntry(gp, null, "ESPlus-Panel", expires, reason));
        ServerPlayer online = server.getPlayerList().getPlayer(gp.getId());
        if (online != null) {
            online.connection.disconnect(Component.literal(reason));
        }
        String result = expires == null
                ? "banned:" + gp.getName()
                : "temp_banned:" + gp.getName() + " until=" + expires.getTime();
        dao.markDone(action.id(), true, result);
        security.audit(gp.getId(), expires == null ? "panel_ban" : "panel_temp_ban", reason, true);
        LOGGER.info("Panel banned {} ({}) expires={} reason={}", gp.getName(), gp.getId(), expires, reason);
    }

    private void applyUnban(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        UserBanList bans = server.getPlayerList().getBans();
        if (!bans.isBanned(gp)) {
            dao.markDone(action.id(), false, "not_banned");
            return;
        }
        bans.remove(gp);
        dao.markDone(action.id(), true, "unbanned:" + gp.getName());
        security.audit(gp.getId(), "panel_unban", gp.getName(), true);
        LOGGER.info("Panel unbanned {} ({})", gp.getName(), gp.getId());
    }

    private void applyBroadcast(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String payload = action.payload();
        if (payload == null || payload.isBlank()) {
            dao.markDone(action.id(), false, "empty_message");
            return;
        }
        deliverBroadcast(server, payload);
        dao.markDone(action.id(), true, "broadcast");
        security.audit(null, "panel_broadcast", payload.length() > 200 ? payload.substring(0, 200) : payload, true);
    }

    private void applyTell(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        ServerPlayer online = server.getPlayerList().getPlayer(gp.getId());
        if (online == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String message = action.payload();
        if (message == null || message.isBlank()) {
            dao.markDone(action.id(), false, "empty_message");
            return;
        }
        message = message.trim();
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        online.sendSystemMessage(Component.literal("[私信] " + message));
        dao.markDone(action.id(), true, "told:" + gp.getName());
        security.audit(gp.getId(), "panel_tell", message, true);
    }

    private void applyWhitelist(MinecraftServer server, PanelActionDao dao, PanelAction action, boolean add) throws Exception {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            dao.markDone(action.id(), false, "player_not_found");
            return;
        }
        GameProfile gp = profile.get();
        var list = server.getPlayerList().getWhiteList();
        if (add) {
            list.add(new UserWhiteListEntry(gp));
            dao.markDone(action.id(), true, "whitelist_add:" + gp.getName());
            security.audit(gp.getId(), "panel_whitelist_add", gp.getName(), true);
        } else {
            list.remove(gp);
            dao.markDone(action.id(), true, "whitelist_remove:" + gp.getName());
            security.audit(gp.getId(), "panel_whitelist_remove", gp.getName(), true);
        }
    }

    private void applyWhitelistToggle(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String raw = action.payload() == null ? "" : action.payload().trim().toLowerCase();
        boolean enable = raw.equals("on") || raw.equals("1") || raw.equals("true");
        server.getPlayerList().setUsingWhiteList(enable);
        dao.markDone(action.id(), true, "whitelist=" + enable);
        security.audit(null, "panel_whitelist_set", String.valueOf(enable), true);
    }

    private void applyRetention(PanelActionDao dao, PanelAction action) throws Exception {
        int days = Config.AUDIT_RETENTION_DAYS.getAsInt();
        String payload = action.payload();
        if (payload != null && !payload.isBlank()) {
            try {
                days = Integer.parseInt(payload.trim());
            } catch (NumberFormatException ignored) {
                // keep config
            }
        }
        int removed = RetentionCleanup.run(security.database(), days);
        dao.markDone(action.id(), true, "removed=" + removed + " days=" + days);
        security.audit(null, "panel_retention_cleanup", "removed=" + removed, true);
    }

    private void applySetGamerule(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String payload = action.payload() == null ? "" : action.payload().trim();
        int sep = payload.indexOf('|');
        if (sep <= 0 || sep >= payload.length() - 1) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String ruleId = payload.substring(0, sep).trim();
        String value = payload.substring(sep + 1).trim();
        AtomicBoolean found = new AtomicBoolean(false);
        AtomicReference<String> err = new AtomicReference<>();
        AtomicReference<String> oldValue = new AtomicReference<>();
        GameRules rules = server.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                if (!key.getId().equals(ruleId)) {
                    return;
                }
                found.set(true);
                oldValue.set(Boolean.toString(rules.getRule(key).get()));
                boolean bool = value.equalsIgnoreCase("true") || value.equals("1") || value.equalsIgnoreCase("on");
                rules.getRule(key).set(bool, server);
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                if (!key.getId().equals(ruleId)) {
                    return;
                }
                found.set(true);
                oldValue.set(Integer.toString(rules.getRule(key).get()));
                try {
                    rules.getRule(key).set(Integer.parseInt(value), server);
                } catch (NumberFormatException ex) {
                    err.set("bad_int");
                }
            }
        });
        if (err.get() != null) {
            dao.markDone(action.id(), false, err.get());
            return;
        }
        if (!found.get()) {
            dao.markDone(action.id(), false, "unknown_rule");
            return;
        }
        insertConfigRevision("panel", "gamerule", ruleId, oldValue.get(), value, null);
        dao.markDone(action.id(), true, ruleId + "=" + value);
        security.audit(null, "panel_set_gamerule", ruleId + "=" + value, true);
    }

    private void insertConfigRevision(String actor, String scope, String key, String oldValue, String newValue, String snapshotRef) {
        try {
            SqliteDatabase db = security.database();
            if (db == null) {
                return;
            }
            synchronized (db.lock()) {
                try (PreparedStatement statement = db.connection().prepareStatement(
                        """
                        INSERT INTO config_revisions (ts, actor, scope, key, old_value, new_value, snapshot_ref, detail)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setLong(1, System.currentTimeMillis());
                    statement.setString(2, actor);
                    statement.setString(3, scope);
                    statement.setString(4, key);
                    statement.setString(5, oldValue);
                    statement.setString(6, newValue);
                    statement.setString(7, snapshotRef);
                    statement.setString(8, null);
                    statement.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("config revision write failed", ex);
        }
    }

    private void applySetTime(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String raw = action.payload() == null ? "" : action.payload().trim().toLowerCase(Locale.ROOT);
        long dayTime = switch (raw) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> {
                try {
                    yield Long.parseLong(raw);
                } catch (NumberFormatException ex) {
                    yield -1L;
                }
            }
        };
        if (dayTime < 0) {
            dao.markDone(action.id(), false, "bad_time");
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(dayTime);
        }
        dao.markDone(action.id(), true, "time=" + dayTime);
        security.audit(null, "panel_set_time", String.valueOf(dayTime), true);
    }

    private void applySetWeather(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String raw = action.payload() == null ? "" : action.payload().trim().toLowerCase(Locale.ROOT);
        ServerLevel level = server.overworld();
        switch (raw) {
            case "clear" -> level.setWeatherParameters(6000, 0, false, false);
            case "rain" -> level.setWeatherParameters(0, 6000, true, false);
            case "thunder" -> level.setWeatherParameters(0, 6000, true, true);
            default -> {
                dao.markDone(action.id(), false, "bad_weather");
                return;
            }
        }
        dao.markDone(action.id(), true, "weather=" + raw);
        security.audit(null, "panel_set_weather", raw, true);
    }

    private void applySetDifficulty(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String raw = action.payload() == null ? "" : action.payload().trim().toLowerCase(Locale.ROOT);
        Difficulty difficulty = Difficulty.byName(raw);
        if (difficulty == null) {
            dao.markDone(action.id(), false, "bad_difficulty");
            return;
        }
        server.setDifficulty(difficulty, true);
        dao.markDone(action.id(), true, "difficulty=" + difficulty.getKey());
        security.audit(null, "panel_set_difficulty", difficulty.getKey(), true);
    }

    private void applySetDefaultGamemode(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        GameType type = parseGameType(action.payload());
        if (type == null) {
            dao.markDone(action.id(), false, "bad_gamemode");
            return;
        }
        server.setDefaultGameType(type);
        dao.markDone(action.id(), true, "default_gamemode=" + type.getName());
        security.audit(null, "panel_set_default_gamemode", type.getName(), true);
    }

    private void applySetWorldBorder(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] parts = (action.payload() == null ? "" : action.payload()).split("\\|");
        if (parts.length < 1 || parts[0].isBlank()) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        WorldBorder border = server.overworld().getWorldBorder();
        try {
            double size = Double.parseDouble(parts[0].trim());
            border.setSize(size);
            if (parts.length >= 3) {
                border.setCenter(Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[2].trim()));
            }
            if (parts.length >= 4) {
                border.setWarningBlocks(Integer.parseInt(parts[3].trim()));
            }
            if (parts.length >= 5) {
                border.setDamagePerBlock(Double.parseDouble(parts[4].trim()));
            }
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_number");
            return;
        }
        dao.markDone(action.id(), true, "worldborder=" + action.payload());
        security.audit(null, "panel_set_worldborder", action.payload(), true);
    }

    private void applySetPlayerGamemode(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        GameType type = parseGameType(action.payload());
        if (type == null) {
            dao.markDone(action.id(), false, "bad_gamemode");
            return;
        }
        player.setGameMode(type);
        dao.markDone(action.id(), true, "gamemode=" + type.getName());
        security.audit(player.getUUID(), "panel_set_gamemode", type.getName(), true);
    }

    private void applyClearInventory(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.getInventory().clearContent();
        dao.markDone(action.id(), true, "cleared:" + player.getGameProfile().getName());
        security.audit(player.getUUID(), "panel_clear_inventory", player.getGameProfile().getName(), true);
    }

    private void applyHealPlayer(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        dao.markDone(action.id(), true, "healed:" + player.getGameProfile().getName());
        security.audit(player.getUUID(), "panel_heal", player.getGameProfile().getName(), true);
    }

    private void applyFeedPlayer(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        dao.markDone(action.id(), true, "fed:" + player.getGameProfile().getName());
        security.audit(player.getUUID(), "panel_feed", player.getGameProfile().getName(), true);
    }

    private void applyExtinguishPlayer(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.clearFire();
        dao.markDone(action.id(), true, "extinguished:" + player.getGameProfile().getName());
        security.audit(player.getUUID(), "panel_extinguish", player.getGameProfile().getName(), true);
    }

    private void applyTitle(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String payload = action.payload() == null ? "" : action.payload();
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 2 || parts[1].isBlank()) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String kind = parts[0].trim().toLowerCase(Locale.ROOT);
        CommandSourceStack source = server.createCommandSourceStack();
        if ("actionbar".equals(kind)) {
            String text = parts[1].replace("\"", "'");
            server.getCommands().performPrefixedCommand(source, "title @a actionbar {\"text\":\"" + text + "\"}");
        } else if ("subtitle".equals(kind)) {
            String title = parts[1].replace("\"", "'");
            String sub = (parts.length >= 3 ? parts[2] : "").replace("\"", "'");
            server.getCommands().performPrefixedCommand(source, "title @a title {\"text\":\"" + title + "\"}");
            server.getCommands().performPrefixedCommand(source, "title @a subtitle {\"text\":\"" + sub + "\"}");
        } else if ("title".equals(kind)) {
            String text = parts[1].replace("\"", "'");
            server.getCommands().performPrefixedCommand(source, "title @a title {\"text\":\"" + text + "\"}");
        } else {
            dao.markDone(action.id(), false, "bad_kind");
            return;
        }
        dao.markDone(action.id(), true, "title=" + kind);
        security.audit(null, "panel_title", kind, true);
    }

    private void applyKillEntities(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String kind = action.payload() == null ? "" : action.payload().trim().toLowerCase(Locale.ROOT);
        int removed = 0;
        String typeFilter = null;
        if (kind.startsWith("type:")) {
            typeFilter = kind.substring(5).trim();
            if (typeFilter.isBlank()) {
                dao.markDone(action.id(), false, "bad_type");
                return;
            }
        } else if (!kind.equals("items") && !kind.equals("xp") && !kind.equals("hostile")
                && !kind.equals("mobs") && !kind.equals("all_non_players")) {
            dao.markDone(action.id(), false, "bad_kind");
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : List.copyOf(iterableToList(level.getAllEntities()))) {
                boolean kill;
                if (typeFilter != null) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getKey(entity.getType()).toString();
                    kill = !(entity instanceof ServerPlayer) && id.equals(typeFilter);
                } else {
                    kill = switch (kind) {
                        case "items" -> entity instanceof ItemEntity;
                        case "xp" -> entity instanceof ExperienceOrb;
                        case "hostile" -> entity instanceof Enemy || entity instanceof Monster;
                        case "mobs" -> entity instanceof Mob;
                        case "all_non_players" -> !(entity instanceof ServerPlayer);
                        default -> false;
                    };
                }
                if (kill) {
                    entity.discard();
                    removed++;
                }
            }
        }
        dao.markDone(action.id(), true, "removed=" + removed);
        security.audit(null, "panel_kill_entities", kind + " removed=" + removed, true);
    }

    private void applyGiveItem(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String payload = action.payload() == null ? "" : action.payload().trim();
        String[] parts = payload.split("\\|", 2);
        if (parts.length < 1 || parts[0].isBlank()) {
            dao.markDone(action.id(), false, "bad_item");
            return;
        }
        String itemId = parts[0].trim();
        int count = 1;
        if (parts.length == 2) {
            try {
                count = Math.max(1, Math.min(Integer.parseInt(parts[1].trim()), 64 * 9));
            } catch (NumberFormatException ignored) {
                count = 1;
            }
        }
        CommandSourceStack source = server.createCommandSourceStack();
        String cmd = "give " + player.getGameProfile().getName() + " " + itemId + " " + count;
        server.getCommands().performPrefixedCommand(source, cmd);
        dao.markDone(action.id(), true, cmd);
        security.audit(player.getUUID(), "panel_give", cmd, true);
    }

    private void applySaveAll(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        server.getPlayerList().saveAll();
        server.saveEverything(true, true, true);
        dao.markDone(action.id(), true, "saved");
        security.audit(null, "panel_save_all", "ok", true);
    }

    private void applyReload(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        CommandSourceStack source = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(source, "reload");
        dao.markDone(action.id(), true, "reload");
        security.audit(null, "panel_reload", "ok", true);
    }

    private void applyStop(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        dao.markDone(action.id(), true, "stopping");
        security.audit(null, "panel_stop", "requested", true);
        server.halt(false);
    }

    private void applyBossbarCreate(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 2);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String id = sanitizeId(p[0]);
        String name = escapeJson(p[1]);
        String color = p.length > 2 && !p[2].isBlank() ? p[2].trim() : "white";
        String max = p.length > 3 && !p[3].isBlank() ? p[3].trim() : "100";
        CommandSourceStack source = server.createCommandSourceStack();
        runCmd(server, source, "bossbar add " + id + " {\"text\":\"" + name + "\"}");
        runCmd(server, source, "bossbar set " + id + " color " + color);
        runCmd(server, source, "bossbar set " + id + " max " + max);
        runCmd(server, source, "bossbar set " + id + " players @a");
        runCmd(server, source, "bossbar set " + id + " visible true");
        dao.markDone(action.id(), true, "bossbar=" + id);
        security.audit(null, "panel_bossbar_create", id, true);
    }

    private void applyBossbarUpdate(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String id = sanitizeId(p[0]);
        CommandSourceStack source = server.createCommandSourceStack();
        if (p.length > 1 && !p[1].isBlank()) {
            runCmd(server, source, "bossbar set " + id + " name {\"text\":\"" + escapeJson(p[1]) + "\"}");
        }
        if (p.length > 2 && !p[2].isBlank()) {
            runCmd(server, source, "bossbar set " + id + " color " + p[2].trim());
        }
        if (p.length > 3 && !p[3].isBlank()) {
            runCmd(server, source, "bossbar set " + id + " value " + p[3].trim());
        }
        if (p.length > 4 && !p[4].isBlank()) {
            runCmd(server, source, "bossbar set " + id + " max " + p[4].trim());
        }
        if (p.length > 5 && !p[5].isBlank()) {
            runCmd(server, source, "bossbar set " + id + " visible " + p[5].trim());
        }
        runCmd(server, source, "bossbar set " + id + " players @a");
        dao.markDone(action.id(), true, "bossbar_update=" + id);
        security.audit(null, "panel_bossbar_update", id, true);
    }

    private void applyBossbarRemove(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String id = sanitizeId(action.payload());
        if (id == null) {
            dao.markDone(action.id(), false, "bad_id");
            return;
        }
        runCmd(server, server.createCommandSourceStack(), "bossbar remove " + id);
        dao.markDone(action.id(), true, "removed=" + id);
        security.audit(null, "panel_bossbar_remove", id, true);
    }

    private void applyScoreboardAdd(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String name = sanitizeToken(p[0]);
        String criteria = p.length > 1 && !p[1].isBlank() ? sanitizeToken(p[1]) : "dummy";
        String display = p.length > 2 && !p[2].isBlank() ? escapeJson(p[2]) : name;
        runCmd(server, server.createCommandSourceStack(),
                "scoreboard objectives add " + name + " " + criteria + " {\"text\":\"" + display + "\"}");
        dao.markDone(action.id(), true, "objective=" + name);
        security.audit(null, "panel_scoreboard_add", name, true);
    }

    private void applyScoreboardRemove(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String name = sanitizeToken(action.payload());
        if (name == null) {
            dao.markDone(action.id(), false, "bad_name");
            return;
        }
        runCmd(server, server.createCommandSourceStack(), "scoreboard objectives remove " + name);
        dao.markDone(action.id(), true, "removed=" + name);
        security.audit(null, "panel_scoreboard_remove", name, true);
    }

    private void applyScoreboardDisplay(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String slot = sanitizeToken(p[0]);
        String objective = p.length > 1 ? sanitizeToken(p[1]) : null;
        if (objective == null || objective.isBlank()) {
            runCmd(server, server.createCommandSourceStack(), "scoreboard objectives setdisplay " + slot);
        } else {
            runCmd(server, server.createCommandSourceStack(), "scoreboard objectives setdisplay " + slot + " " + objective);
        }
        dao.markDone(action.id(), true, "display=" + slot + ":" + objective);
        security.audit(null, "panel_scoreboard_display", slot + "|" + objective, true);
    }

    private void applyTeamCreate(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String name = sanitizeToken(p[0]);
        CommandSourceStack source = server.createCommandSourceStack();
        runCmd(server, source, "team add " + name);
        if (p.length > 1 && !p[1].isBlank()) {
            runCmd(server, source, "team modify " + name + " displayName {\"text\":\"" + escapeJson(p[1]) + "\"}");
        }
        if (p.length > 2 && !p[2].isBlank()) {
            runCmd(server, source, "team modify " + name + " color " + sanitizeToken(p[2]));
        }
        if (p.length > 3 && !p[3].isBlank()) {
            runCmd(server, source, "team modify " + name + " friendlyFire " + p[3].trim());
        }
        dao.markDone(action.id(), true, "team=" + name);
        security.audit(null, "panel_team_create", name, true);
    }

    private void applyTeamRemove(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String name = sanitizeToken(action.payload());
        if (name == null) {
            dao.markDone(action.id(), false, "bad_name");
            return;
        }
        runCmd(server, server.createCommandSourceStack(), "team remove " + name);
        dao.markDone(action.id(), true, "removed=" + name);
        security.audit(null, "panel_team_remove", name, true);
    }

    private void applyTeamJoin(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 2);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String team = sanitizeToken(p[0]);
        String player = sanitizeToken(p[1]);
        runCmd(server, server.createCommandSourceStack(), "team join " + team + " " + player);
        dao.markDone(action.id(), true, "join=" + player + "->" + team);
        security.audit(null, "panel_team_join", player + "|" + team, true);
    }

    private void applyTeamLeave(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String player = sanitizeToken(action.payload() != null ? action.payload() : action.targetName());
        if (player == null) {
            dao.markDone(action.id(), false, "bad_player");
            return;
        }
        runCmd(server, server.createCommandSourceStack(), "team leave " + player);
        dao.markDone(action.id(), true, "leave=" + player);
        security.audit(null, "panel_team_leave", player, true);
    }

    private void applyTeamModify(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 3);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String team = sanitizeToken(p[0]);
        String option = sanitizeToken(p[1]);
        String value = p[2].trim();
        if ("displayName".equals(option)) {
            value = "{\"text\":\"" + escapeJson(value) + "\"}";
        } else {
            value = sanitizeToken(value);
        }
        runCmd(server, server.createCommandSourceStack(), "team modify " + team + " " + option + " " + value);
        dao.markDone(action.id(), true, "modify=" + team);
        security.audit(null, "panel_team_modify", team + "|" + option, true);
    }

    private void applySetWorldSpawn(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 3);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        try {
            int x = (int) Double.parseDouble(p[0].trim());
            int y = (int) Double.parseDouble(p[1].trim());
            int z = (int) Double.parseDouble(p[2].trim());
            float angle = p.length > 3 && !p[3].isBlank() ? Float.parseFloat(p[3].trim()) : 0f;
            server.overworld().setDefaultSpawnPos(new net.minecraft.core.BlockPos(x, y, z), angle);
            dao.markDone(action.id(), true, "spawn=" + x + "," + y + "," + z);
            security.audit(null, "panel_set_worldspawn", x + "," + y + "," + z, true);
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_number");
        }
    }

    private void applyTeleport(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String mode = p[0].trim().toLowerCase(Locale.ROOT);
        switch (mode) {
            case "spawn" -> {
                var pos = server.overworld().getSharedSpawnPos();
                player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
            }
            case "player" -> {
                if (p.length < 2 || p[1].isBlank()) {
                    dao.markDone(action.id(), false, "missing_target");
                    return;
                }
                ServerPlayer dest = server.getPlayerList().getPlayerByName(p[1].trim());
                if (dest == null) {
                    dao.markDone(action.id(), false, "target_offline");
                    return;
                }
                player.teleportTo((ServerLevel) dest.level(), dest.getX(), dest.getY(), dest.getZ(),
                        dest.getYRot(), dest.getXRot());
            }
            case "coords" -> {
                if (p.length < 4) {
                    dao.markDone(action.id(), false, "bad_coords");
                    return;
                }
                double x = Double.parseDouble(p[1].trim());
                double y = Double.parseDouble(p[2].trim());
                double z = Double.parseDouble(p[3].trim());
                ServerLevel level = (ServerLevel) player.level();
                if (p.length >= 5 && !p[4].isBlank()) {
                    for (ServerLevel candidate : server.getAllLevels()) {
                        if (candidate.dimension().location().toString().equals(p[4].trim())) {
                            level = candidate;
                            break;
                        }
                    }
                }
                player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            }
            default -> {
                dao.markDone(action.id(), false, "bad_mode");
                return;
            }
        }
        dao.markDone(action.id(), true, "tp=" + mode);
        security.audit(player.getUUID(), "panel_teleport", mode + "|" + action.payload(), true);
    }

    private void applyClearEnderChest(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.getEnderChestInventory().clearContent();
        dao.markDone(action.id(), true, "ender_cleared:" + player.getGameProfile().getName());
        security.audit(player.getUUID(), "panel_clear_enderchest", player.getGameProfile().getName(), true);
    }

    private void applySaveToggle(MinecraftServer server, PanelActionDao dao, PanelAction action, boolean on) throws Exception {
        runCmd(server, server.createCommandSourceStack(), on ? "save-on" : "save-off");
        dao.markDone(action.id(), true, on ? "save-on" : "save-off");
        security.audit(null, on ? "panel_save_on" : "panel_save_off", "ok", true);
    }

    private void applyBanIp(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String ip = action.payload() == null ? "" : action.payload().trim();
        if (ip.isBlank() || !ip.matches("[0-9a-fA-F.:]+")) {
            dao.markDone(action.id(), false, "bad_ip");
            return;
        }
        String reason = action.targetName() == null || action.targetName().isBlank()
                ? "Banned by panel" : action.targetName().trim();
        server.getPlayerList().getIpBans().add(new net.minecraft.server.players.IpBanListEntry(
                ip, null, "ESPlus-Panel", null, reason));
        dao.markDone(action.id(), true, "ban_ip=" + ip);
        security.audit(null, "panel_ban_ip", ip + "|" + reason, true);
    }

    private void applyPardonIp(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String ip = action.payload() == null ? "" : action.payload().trim();
        if (ip.isBlank()) {
            dao.markDone(action.id(), false, "bad_ip");
            return;
        }
        var list = server.getPlayerList().getIpBans();
        list.remove(ip);
        dao.markDone(action.id(), true, "pardon_ip=" + ip);
        security.audit(null, "panel_pardon_ip", ip, true);
    }

    private void applySetSpawnpoint(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String[] p = splitPayload(action.payload() == null ? "here" : action.payload(), 1);
        if (p == null) {
            p = new String[]{"here"};
        }
        String mode = p[0].trim().toLowerCase(Locale.ROOT);
        try {
            switch (mode) {
                case "here" -> player.setRespawnPosition(
                        player.level().dimension(), player.blockPosition(), player.getYRot(), true, true);
                case "world" -> {
                    var pos = server.overworld().getSharedSpawnPos();
                    player.setRespawnPosition(
                            server.overworld().dimension(), pos, server.overworld().getSharedSpawnAngle(), true, true);
                }
                case "coords" -> {
                    if (p.length < 4) {
                        dao.markDone(action.id(), false, "bad_coords");
                        return;
                    }
                    int x = (int) Double.parseDouble(p[1].trim());
                    int y = (int) Double.parseDouble(p[2].trim());
                    int z = (int) Double.parseDouble(p[3].trim());
                    float angle = p.length > 4 && !p[4].isBlank() ? Float.parseFloat(p[4].trim()) : player.getYRot();
                    var dim = player.level().dimension();
                    if (p.length > 5 && !p[5].isBlank()) {
                        for (ServerLevel level : server.getAllLevels()) {
                            if (level.dimension().location().toString().equals(p[5].trim())) {
                                dim = level.dimension();
                                break;
                            }
                        }
                    }
                    player.setRespawnPosition(dim, new net.minecraft.core.BlockPos(x, y, z), angle, true, true);
                }
                default -> {
                    dao.markDone(action.id(), false, "bad_mode");
                    return;
                }
            }
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_number");
            return;
        }
        dao.markDone(action.id(), true, "spawnpoint=" + mode);
        security.audit(player.getUUID(), "panel_set_spawnpoint", mode, true);
    }

    private void applyClearEffects(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        player.removeAllEffects();
        dao.markDone(action.id(), true, "effects_cleared");
        security.audit(player.getUUID(), "panel_clear_effects", player.getGameProfile().getName(), true);
    }

    private void applyGiveEffect(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        ServerPlayer player = onlinePlayer(server, action);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        String[] p = splitPayload(action.payload(), 1);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String effectId = sanitizeId(p[0]);
        if (effectId == null) {
            dao.markDone(action.id(), false, "bad_effect");
            return;
        }
        if (!effectId.contains(":")) {
            effectId = "minecraft:" + effectId;
        }
        int seconds = 30;
        int amplifier = 0;
        if (p.length > 1 && !p[1].isBlank()) {
            try {
                seconds = Math.max(1, Math.min(Integer.parseInt(p[1].trim()), 3600));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (p.length > 2 && !p[2].isBlank()) {
            try {
                amplifier = Math.max(0, Math.min(Integer.parseInt(p[2].trim()), 255));
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        String cmd = "effect give " + player.getGameProfile().getName() + " " + effectId + " " + seconds + " " + amplifier;
        runCmd(server, server.createCommandSourceStack(), cmd);
        dao.markDone(action.id(), true, cmd);
        security.audit(player.getUUID(), "panel_give_effect", cmd, true);
    }

    private void applyScoreboardSet(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String[] p = splitPayload(action.payload(), 3);
        if (p == null) {
            dao.markDone(action.id(), false, "bad_payload");
            return;
        }
        String player = sanitizeToken(p[0]);
        String objective = sanitizeToken(p[1]);
        String score = sanitizeToken(p[2]);
        if (player == null || objective == null || score == null) {
            dao.markDone(action.id(), false, "bad_token");
            return;
        }
        runCmd(server, server.createCommandSourceStack(),
                "scoreboard players set " + player + " " + objective + " " + score);
        dao.markDone(action.id(), true, player + "=" + score);
        security.audit(null, "panel_scoreboard_set", player + "|" + objective + "|" + score, true);
    }

    private void applySetMotd(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String motd = action.payload() == null ? "" : action.payload().trim();
        if (motd.length() > 200) {
            motd = motd.substring(0, 200);
        }
        server.setMotd(motd);
        dao.markDone(action.id(), true, "motd");
        security.audit(null, "panel_set_motd", motd, true);
    }

    private void applyIdleTimeout(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        int minutes = 0;
        try {
            minutes = Integer.parseInt(action.payload() == null ? "0" : action.payload().trim());
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_number");
            return;
        }
        minutes = Math.max(0, Math.min(minutes, 1440));
        server.setPlayerIdleTimeout(minutes);
        dao.markDone(action.id(), true, "idle=" + minutes);
        security.audit(null, "panel_idle_timeout", String.valueOf(minutes), true);
    }

    private void applyMaintenanceKick(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        String reason = sanitizeReason(action.payload(), "服务器维护中，请稍后再试");
        boolean enableWhitelist = "1".equals(action.targetName()) || "whitelist".equalsIgnoreCase(action.targetName());
        int kicked = 0;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (server.getPlayerList().isOp(player.getGameProfile())) {
                continue;
            }
            player.connection.disconnect(Component.literal(reason));
            kicked++;
        }
        if (enableWhitelist) {
            server.getPlayerList().setUsingWhiteList(true);
        }
        setMaintenanceFlag(true);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[维护] " + reason), false);
        dao.markDone(action.id(), true, "kicked=" + kicked + " wl=" + enableWhitelist);
        security.audit(null, "panel_maintenance_kick", "kicked=" + kicked, true);
    }

    private void applyClearMaintenance(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        boolean disableWhitelist = "1".equals(action.targetName()) || "whitelist".equalsIgnoreCase(action.targetName());
        if (disableWhitelist) {
            server.getPlayerList().setUsingWhiteList(false);
        }
        setMaintenanceFlag(false);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[维护] 维护模式已解除"), false);
        dao.markDone(action.id(), true, "cleared wl_off=" + disableWhitelist);
        security.audit(null, "panel_clear_maintenance", "ok", true);
    }

    private void applyLockdown(MinecraftServer server, PanelActionDao dao, PanelAction action, boolean on) throws Exception {
        setLockdownFlag(on);
        if (on) {
            setMaintenanceFlag(true);
            int kicked = 0;
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (!server.getPlayerList().isOp(player.getGameProfile())) {
                    player.connection.disconnect(Component.literal("紧急严打模式已开启，非管理员已被移出"));
                    kicked++;
                }
            }
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[ES+] 紧急严打模式已开启：维护模式+踢出非OP+sudo 缩短"), false);
            dao.markDone(action.id(), true, "lockdown_on kicked=" + kicked);
            security.audit(null, "panel_lockdown_on", "kicked=" + kicked, true);
        } else {
            setMaintenanceFlag(false);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[ES+] 紧急严打模式已关闭"), false);
            dao.markDone(action.id(), true, "lockdown_off");
            security.audit(null, "panel_lockdown_off", "ok", true);
        }
    }

    private void applyAutomationTrigger(PanelActionDao dao, PanelAction action) throws Exception {
        if (automation == null) {
            dao.markDone(action.id(), false, "no_automation_service");
            return;
        }
        String raw = action.payload() == null ? "" : action.payload().trim();
        long taskId;
        try {
            taskId = Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_task_id");
            return;
        }
        boolean ok = automation.executeTask(taskId);
        dao.markDone(action.id(), ok, ok ? "automation_triggered" : "execution_failed");
        security.audit(null, "panel_automation_trigger", "task=" + taskId + " ok=" + ok, ok);
    }

    private void applyRollbackBlocks(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        if (!Config.ROLLBACK_ENABLED.getAsBoolean()) {
            dao.markDone(action.id(), false, "rollback_disabled");
            return;
        }
        var audit = security.auditService();
        if (audit == null) {
            dao.markDone(action.id(), false, "no_audit_service");
            return;
        }
        JsonPayload payload = parseJson(action.payload());
        UUID uuid = payload.uuid();
        if (uuid == null) {
            dao.markDone(action.id(), false, "missing_player_uuid");
            return;
        }
        long fromTs = payload.fromTs();
        long toTs = payload.toTs();
        if (fromTs <= 0 || toTs <= 0 || fromTs > toTs) {
            dao.markDone(action.id(), false, "bad_time_range");
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        ServerLevel level = null;
        String dimId = payload.dimension();
        if (dimId != null && !dimId.isBlank()) {
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().location().toString().equals(dimId)) {
                    level = candidate;
                    break;
                }
            }
        }
        if (level == null) {
            if (player != null) {
                level = (ServerLevel) player.level();
            } else {
                level = server.overworld();
            }
        }
        var result = audit.blockRollbackExecutor().rollbackPlayerBlocks(uuid, fromTs, toTs, level);
        String msg = "rollback_blocks ok=" + result.successCount() + " fail=" + result.failCount() + " total=" + result.totalFound();
        dao.markDone(action.id(), true, msg);
        security.audit(uuid, "panel_rollback_blocks", msg, true);
    }

    private void applyRestoreInventory(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        if (!Config.INVENTORY_SNAPSHOT_ENABLED.getAsBoolean()) {
            dao.markDone(action.id(), false, "inventory_snapshot_disabled");
            return;
        }
        var audit = security.auditService();
        if (audit == null) {
            dao.markDone(action.id(), false, "no_audit_service");
            return;
        }
        JsonPayload payload = parseJson(action.payload());
        UUID uuid = payload.uuid();
        if (uuid == null) {
            dao.markDone(action.id(), false, "missing_player_uuid");
            return;
        }
        long targetTs = payload.targetTs();
        if (targetTs <= 0) {
            targetTs = System.currentTimeMillis();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            dao.markDone(action.id(), false, "player_offline");
            return;
        }
        var result = audit.inventorySnapshotTracker().restoreInventory(uuid, targetTs, player);
        String msg = "restore_inventory restored=" + result.restored() + " failed=" + result.failed();
        dao.markDone(action.id(), result.restored() > 0, msg);
        security.audit(uuid, "panel_restore_inventory", msg, result.restored() > 0);
    }

    private static JsonPayload parseJson(String raw) {
        String s = raw == null ? "" : raw.trim();
        UUID uuid = null;
        long fromTs = 0;
        long toTs = 0;
        long targetTs = 0;
        String dimension = null;
        try {
            int uq = s.indexOf("\"playerUuid\"");
            if (uq < 0) uq = s.indexOf("\"player_uuid\"");
            if (uq < 0) uq = s.indexOf("\"uuid\"");
            if (uq >= 0) {
                int colon = s.indexOf(':', uq);
                int q1 = s.indexOf('"', colon + 1);
                int q2 = s.indexOf('"', q1 + 1);
                if (q1 > 0 && q2 > 0) {
                    uuid = UUID.fromString(s.substring(q1 + 1, q2));
                }
            }
            fromTs = extractLong(s, "fromTs", "from_ts");
            toTs = extractLong(s, "toTs", "to_ts");
            targetTs = extractLong(s, "targetTs", "target_ts");
            int dq = s.indexOf("\"dimension\"");
            if (dq >= 0) {
                int colon = s.indexOf(':', dq);
                int q1 = s.indexOf('"', colon + 1);
                int q2 = s.indexOf('"', q1 + 1);
                if (q1 > 0 && q2 > 0) {
                    dimension = s.substring(q1 + 1, q2);
                }
            }
        } catch (Exception ignored) {
        }
        return new JsonPayload(uuid, fromTs, toTs, targetTs, dimension);
    }

    private static long extractLong(String s, String key1, String key2) {
        int idx = s.indexOf('"' + key1 + '"');
        if (idx < 0) idx = s.indexOf('"' + key2 + '"');
        if (idx < 0) return 0;
        int colon = s.indexOf(':', idx);
        int end = colon + 1;
        while (end < s.length() && Character.isWhitespace(s.charAt(end))) end++;
        int start = end;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (start == end) return 0;
        try {
            return Long.parseLong(s.substring(start, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private record JsonPayload(UUID uuid, long fromTs, long toTs, long targetTs, String dimension) {
    }

    private void setLockdownFlag(boolean on) {
        if (security.database() == null) {
            return;
        }
        try (var ps = security.database().connection().prepareStatement(
                "UPDATE server_runtime SET lockdown = ? WHERE id = 1")) {
            ps.setInt(1, on ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception ex) {
            LOGGER.debug("Failed to persist lockdown flag", ex);
        }
    }

    private void setMaintenanceFlag(boolean on) {
        if (security.database() == null) {
            return;
        }
        try (var ps = security.database().connection().prepareStatement(
                "UPDATE server_runtime SET maintenance = ? WHERE id = 1")) {
            ps.setInt(1, on ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception ex) {
            LOGGER.debug("Failed to persist maintenance flag", ex);
        }
    }

    private void processSchedules(MinecraftServer server) {
        if (security.database() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        record Due(long id, String kind, String payload, int interval) {}
        java.util.ArrayList<Due> due = new java.util.ArrayList<>();
        try (var ps = security.database().connection().prepareStatement(
                """
                SELECT id, kind, payload, interval_seconds FROM panel_schedules
                WHERE enabled = 1 AND next_run_at <= ?
                ORDER BY next_run_at ASC LIMIT 10
                """)) {
            ps.setLong(1, now);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(new Due(rs.getLong("id"), rs.getString("kind"), rs.getString("payload"), rs.getInt("interval_seconds")));
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Schedule poll failed", ex);
            return;
        }
        for (Due item : due) {
            try {
                if ("broadcast".equals(item.kind())) {
                    deliverBroadcast(server, item.payload());
                } else if ("save_all".equals(item.kind())) {
                    server.saveEverything(true, true, true);
                } else if ("restart_hint".equals(item.kind())) {
                    server.getPlayerList().broadcastSystemMessage(
                            Component.literal("[调度] " + (item.payload() == null || item.payload().isBlank()
                                    ? "计划维护/重启窗口，请管理员执行重启" : item.payload())),
                            false);
                } else if ("kill_entities".equals(item.kind()) || "create_security_snapshot".equals(item.kind())
                        || "maintenance_kick".equals(item.kind()) || "lockdown_on".equals(item.kind())) {
                    // Re-queue as panel action so markDone works on a real row
                    try (var ins = security.database().connection().prepareStatement(
                            """
                            INSERT INTO panel_actions (action, target_uuid, target_name, payload, status, created_at)
                            VALUES (?, NULL, NULL, ?, 'pending', ?)
                            """)) {
                        ins.setString(1, item.kind());
                        ins.setString(2, item.payload());
                        ins.setLong(3, System.currentTimeMillis());
                        ins.executeUpdate();
                    }
                }
            } catch (Exception ex) {
                LOGGER.debug("Schedule {} failed", item.id(), ex);
            }
            long next = item.interval() > 0 ? now + item.interval() * 1000L : now;
            int enabled = item.interval() > 0 ? 1 : 0;
            try (var upd = security.database().connection().prepareStatement(
                    """
                    UPDATE panel_schedules
                    SET last_run_at = ?, next_run_at = ?, enabled = ?
                    WHERE id = ?
                    """)) {
                upd.setLong(1, now);
                upd.setLong(2, enabled == 1 ? next : now);
                upd.setInt(3, enabled);
                upd.setLong(4, item.id());
                upd.executeUpdate();
            } catch (Exception ex) {
                LOGGER.debug("Schedule update failed id={}", item.id(), ex);
            }
            security.audit(null, "panel_schedule_run", item.kind() + "#" + item.id(), true);
        }
    }

    /** Parses broadcast payload (plain or __v2|prefix|times|msg) and sends to all players. */
    private void deliverBroadcast(MinecraftServer server, String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        payload = payload.trim();
        String prefix = "[公告] ";
        int times = 1;
        String message;
        if (payload.startsWith("__v2|")) {
            String[] parts = payload.substring(5).split("\\|", 3);
            if (parts.length < 3) {
                return;
            }
            prefix = parts[0].isBlank() ? "[公告] " : parts[0] + " ";
            try {
                times = Math.max(1, Math.min(Integer.parseInt(parts[1].trim()), 10));
            } catch (NumberFormatException ignored) {
                times = 1;
            }
            message = parts[2];
        } else {
            message = payload;
        }
        if (message.isBlank()) {
            return;
        }
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        Component component = Component.literal(prefix + message);
        for (int i = 0; i < times; i++) {
            server.getPlayerList().broadcastSystemMessage(component, false);
        }
    }

    private void applyCreateSnapshot(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        SqliteDatabase db = security.database();
        if (db == null) {
            dao.markDone(action.id(), false, "no_db");
            return;
        }
        String label = action.payload() == null || action.payload().isBlank()
                ? "snapshot-" + System.currentTimeMillis()
                : action.payload().trim();
        StringBuilder json = new StringBuilder();
        json.append("{\"gamerules\":{");
        AtomicBoolean first = new AtomicBoolean(true);
        GameRules rules = server.getGameRules();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                appendRule(json, first, key.getId(), Boolean.toString(rules.getRule(key).get()));
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                appendRule(json, first, key.getId(), Integer.toString(rules.getRule(key).get()));
            }
        });
        json.append("},\"whitelist\":").append(server.getPlayerList().isUsingWhitelist() ? "true" : "false");
        json.append(",\"difficulty\":\"").append(server.getWorldData().getDifficulty().getSerializedName()).append("\"");
        json.append(",\"serverId\":\"").append(escapeJson(Config.SERVER_ID.get())).append("\"");
        json.append('}');
        synchronized (db.lock()) {
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "INSERT INTO security_snapshots (ts, actor, label, payload_json) VALUES (?, ?, ?, ?)")) {
                statement.setLong(1, System.currentTimeMillis());
                statement.setString(2, "panel");
                statement.setString(3, label);
                statement.setString(4, json.toString());
                statement.executeUpdate();
            }
        }
        dao.markDone(action.id(), true, "snapshot=" + label);
        security.audit(null, "panel_security_snapshot", label, true);
    }

    private static void appendRule(StringBuilder json, AtomicBoolean first, String key, String value) {
        if (!first.getAndSet(false)) {
            json.append(',');
        }
        json.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private void applyRestoreSnapshot(MinecraftServer server, PanelActionDao dao, PanelAction action) throws Exception {
        SqliteDatabase db = security.database();
        if (db == null) {
            dao.markDone(action.id(), false, "no_db");
            return;
        }
        long snapId;
        try {
            snapId = Long.parseLong(action.payload() == null ? "" : action.payload().trim());
        } catch (NumberFormatException ex) {
            dao.markDone(action.id(), false, "bad_id");
            return;
        }
        String payload;
        synchronized (db.lock()) {
            try (PreparedStatement statement = db.connection().prepareStatement(
                    "SELECT payload_json FROM security_snapshots WHERE id = ?")) {
                statement.setLong(1, snapId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        dao.markDone(action.id(), false, "not_found");
                        return;
                    }
                    payload = rs.getString(1);
                }
            }
        }
        int start = payload.indexOf("\"gamerules\":{");
        if (start < 0) {
            dao.markDone(action.id(), false, "no_gamerules");
            return;
        }
        int mapStart = payload.indexOf('{', start + 11);
        int mapEnd = payload.indexOf("},\"whitelist\"", mapStart);
        if (mapEnd < 0) {
            mapEnd = payload.indexOf('}', mapStart + 1);
        }
        if (mapStart < 0 || mapEnd < 0) {
            dao.markDone(action.id(), false, "bad_json");
            return;
        }
        String map = payload.substring(mapStart + 1, mapEnd);
        int restored = 0;
        GameRules rules = server.getGameRules();
        for (String pair : map.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) {
                continue;
            }
            String ruleId = kv[0].replace("\"", "").trim();
            String value = kv[1].replace("\"", "").trim();
            if (ruleId.isEmpty()) {
                continue;
            }
            AtomicBoolean found = new AtomicBoolean(false);
            AtomicReference<String> oldValue = new AtomicReference<>();
            GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                    if (!key.getId().equals(ruleId)) {
                        return;
                    }
                    found.set(true);
                    oldValue.set(Boolean.toString(rules.getRule(key).get()));
                    boolean bool = value.equalsIgnoreCase("true") || value.equals("1");
                    rules.getRule(key).set(bool, server);
                }

                @Override
                public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                    if (!key.getId().equals(ruleId)) {
                        return;
                    }
                    found.set(true);
                    oldValue.set(Integer.toString(rules.getRule(key).get()));
                    try {
                        rules.getRule(key).set(Integer.parseInt(value), server);
                    } catch (NumberFormatException ignored) {
                        found.set(false);
                    }
                }
            });
            if (found.get()) {
                insertConfigRevision("panel", "gamerule", ruleId, oldValue.get(), value, String.valueOf(snapId));
                restored++;
            }
        }
        insertConfigRevision("panel", "snapshot", "restore", null, "id=" + snapId, String.valueOf(snapId));
        dao.markDone(action.id(), true, "restored_rules=" + restored);
        security.audit(null, "panel_restore_snapshot", "id=" + snapId + " rules=" + restored, true);
    }

    private static void runCmd(MinecraftServer server, CommandSourceStack source, String command) {
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static String[] splitPayload(String payload, int minParts) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String[] parts = payload.split("\\|", -1);
        return parts.length >= minParts ? parts : null;
    }

    private static String sanitizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_./:-]+")) {
            return null;
        }
        return id;
    }

    private static String sanitizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim();
        if (!t.matches("[A-Za-z0-9_./:+-]+")) {
            return null;
        }
        return t;
    }

    private static String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", " ");
    }

    private static ServerPlayer onlinePlayer(MinecraftServer server, PanelAction action) {
        Optional<GameProfile> profile = resolveProfile(server, action.targetUuid(), action.targetName());
        if (profile.isEmpty()) {
            return null;
        }
        return server.getPlayerList().getPlayer(profile.get().getId());
    }

    private static GameType parseGameType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return GameType.byName(raw.trim().toLowerCase(Locale.ROOT), null);
    }

    private static List<Entity> iterableToList(Iterable<Entity> iterable) {
        ArrayList<Entity> list = new ArrayList<>();
        for (Entity e : iterable) {
            list.add(e);
        }
        return list;
    }

    private static String tailAfterPipe(String raw) {
        if (raw == null) {
            return null;
        }
        int sep = raw.indexOf('|');
        return sep >= 0 ? raw.substring(sep + 1) : raw;
    }

    private static String sanitizeReason(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String reason = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (reason.length() > 200) {
            reason = reason.substring(0, 200);
        }
        return reason;
    }

    private static Optional<GameProfile> resolveProfile(MinecraftServer server, String uuid, String name) {
        if (uuid != null && !uuid.isBlank()) {
            try {
                UUID id = UUID.fromString(uuid.trim());
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) {
                    return Optional.of(online.getGameProfile());
                }
                return server.getProfileCache().get(id);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (name != null && !name.isBlank()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name.trim());
            if (online != null) {
                return Optional.of(online.getGameProfile());
            }
            return server.getProfileCache().get(name.trim());
        }
        return Optional.empty();
    }

    private static void notifyPlayer(MinecraftServer server, UUID uuid, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }
}
