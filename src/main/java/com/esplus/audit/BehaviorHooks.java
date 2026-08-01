package com.esplus.audit;

import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.ParseResults;
import com.esplus.security.SecurityService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BehaviorHooks {
    private final SecurityService security;

    public BehaviorHooks(SecurityService security) {
        this.security = security;
    }

    private AuditService audit() {
        return security.auditService();
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        recordPlayer(player, "player", "login", "joined", null, null);
        storeLastIp(player);
        if ((isMaintenanceActive() || isLockdownActive())
                && !player.server.getPlayerList().isOp(player.getGameProfile())) {
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                    isLockdownActive() ? "服务器处于紧急严打模式，仅管理员可进入" : "服务器维护中，请稍后再试"));
        }
    }

    private void storeLastIp(ServerPlayer player) {
        try {
            String ip = player.getIpAddress();
            if (ip == null || ip.isBlank() || security.database() == null) {
                return;
            }
            synchronized (security.database().lock()) {
                try (var ps = security.database().connection().prepareStatement(
                        """
                        INSERT INTO sem_kv (key, value, updated_at) VALUES (?, ?, ?)
                        ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at
                        """)) {
                    ps.setString(1, "last_ip:" + player.getUUID());
                    ps.setString(2, ip);
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
    }

    private boolean isLockdownActive() {
        if (!security.isReady() || security.database() == null) {
            return false;
        }
        try (var ps = security.database().connection().prepareStatement(
                "SELECT lockdown FROM server_runtime WHERE id = 1")) {
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isMaintenanceActive() {
        if (!security.isReady() || security.database() == null) {
            return false;
        }
        try (var ps = security.database().connection().prepareStatement(
                "SELECT maintenance FROM server_runtime WHERE id = 1")) {
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        recordPlayer(player, "player", "logout", "left", null, null);
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String msg = event.getMessage().getString();
        if (msg.length() > 256) {
            msg = msg.substring(0, 256);
        }
        recordPlayer(player, "chat", "chat", msg, null, null);
        if (isMuted(player)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[ES+] 你已被禁言。"));
            return;
        }
        if (shouldBlockChat(msg)) {
            event.setCanceled(true);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[ES+] 消息包含敏感词，已被拦截。"));
        }
    }

    private boolean isMuted(ServerPlayer player) {
        if (!security.isReady() || security.database() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        try (var ps = security.database().connection().prepareStatement(
                """
                SELECT until_ts FROM chat_mutes
                WHERE key = ? OR key = ? OR lower(name) = lower(?)
                LIMIT 1
                """)) {
            ps.setString(1, uuid);
            ps.setString(2, "name:" + name.toLowerCase());
            ps.setString(3, name);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                long until = rs.getLong(1);
                return until == 0 || until > now;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldBlockChat(String message) {
        if (!security.isReady() || security.database() == null || message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        try (var ps = security.database().connection().prepareStatement(
                "SELECT word FROM chat_filter_words WHERE enabled = 1");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String word = rs.getString(1);
                if (word != null && !word.isBlank() && lower.contains(word.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        ParseResults<?> parse = event.getParseResults();
        Object source = parse.getContext().getSource();
        if (!(source instanceof net.minecraft.commands.CommandSourceStack stack)) {
            return;
        }
        String input = parse.getReader().getString();
        if (input.startsWith("/")) {
            input = input.substring(1);
        }
        String redacted = redactSensitive(input);
        ServerPlayer player = stack.getPlayer();
        String actorUuid = player == null ? null : player.getUUID().toString();
        String actorName = player == null ? "console" : player.getGameProfile().getName();
        String dim = null;
        Double x = null, y = null, z = null;
        if (player != null) {
            dim = player.level().dimension().location().toString();
            x = player.getX();
            y = player.getY();
            z = player.getZ();
        }
        String action = event.isCanceled() ? "protected_blocked" : "command";
        GlobalEvent ge = new GlobalEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                "command",
                action,
                actorUuid,
                actorName,
                null,
                null,
                dim,
                x, y, z,
                null,
                null,
                redacted,
                player == null ? "console" : "player"
        );
        AuditService audit = audit();
        if (audit != null) {
            audit.recordAsync(ge);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        GlobalEvent ge = positioned(player, "block", "break", blockId, null, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        AuditService audit = audit();
        if (audit != null) {
            audit.recordAsync(ge);
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = event.getPos();
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
        GlobalEvent ge = positioned(player, "block", "place", blockId, null, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        AuditService audit = audit();
        if (audit != null) {
            audit.recordAsync(ge);
        }
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getEntity().getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AuditService audit = audit();
        if (audit == null) {
            return;
        }
        String traceId = ItemTraceNbt.ensureTrace(stack, audit, itemId, "toss", player.getUUID(), player.getGameProfile().getName(), "player toss");
        GlobalEvent ge = positioned(player, "item", "toss", "dropped " + itemId + " x" + stack.getCount(), itemId, traceId, player.getX(), player.getY(), player.getZ());
        audit.recordAsync(ge);
        audit.linkItem(traceId, null, ge.eventId(), "toss", player.getUUID(), player.getGameProfile().getName(), ge.detail());
    }

    @SubscribeEvent
    public void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AuditService audit = audit();
        if (audit == null) {
            return;
        }
        String traceId = ItemTraceNbt.ensureTrace(stack, audit, itemId, "pickup", player.getUUID(), player.getGameProfile().getName(), "world pickup");
        GlobalEvent ge = positioned(player, "item", "pickup", "picked " + itemId + " x" + stack.getCount(), itemId, traceId, player.getX(), player.getY(), player.getZ());
        audit.recordAsync(ge);
        audit.linkItem(traceId, null, ge.eventId(), "pickup", player.getUUID(), player.getGameProfile().getName(), ge.detail());
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getCrafting();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AuditService audit = audit();
        if (audit == null) {
            return;
        }
        String traceId = ItemTraceNbt.ensureTrace(stack, audit, itemId, "craft", player.getUUID(), player.getGameProfile().getName(), "crafted");
        GlobalEvent ge = positioned(player, "item", "craft", "crafted " + itemId + " x" + stack.getCount(), itemId, traceId, player.getX(), player.getY(), player.getZ());
        audit.recordAsync(ge);
        audit.linkItem(traceId, null, ge.eventId(), "craft", player.getUUID(), player.getGameProfile().getName(), ge.detail());
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String detail = event.getSource().getLocalizedDeathMessage(player).getString();
        recordPlayer(player, "player", "death", detail, null, null);
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String detail = event.getFrom().location() + " -> " + event.getTo().location();
        recordPlayer(player, "player", "dimension", detail, null, null);
    }

    private void recordPlayer(ServerPlayer player, String category, String action, String detail, String itemId, String traceId) {
        AuditService audit = audit();
        if (audit == null) {
            return;
        }
        audit.recordAsync(positioned(player, category, action, detail, itemId, traceId, player.getX(), player.getY(), player.getZ()));
    }

    private static GlobalEvent positioned(
            ServerPlayer player,
            String category,
            String action,
            String detail,
            String itemId,
            String traceId,
            double x,
            double y,
            double z
    ) {
        Level level = player.level();
        return new GlobalEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                category,
                action,
                player.getUUID().toString(),
                player.getGameProfile().getName(),
                null,
                null,
                level.dimension().location().toString(),
                x, y, z,
                itemId,
                traceId,
                detail,
                "player"
        );
    }

    private static String redactSensitive(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String trimmed = input.trim();
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length == 0) {
            return input;
        }
        String root = tokens[0].toLowerCase();
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        if ("sudo".equals(root)) {
            String sub = tokens.length > 1 ? tokens[1].toLowerCase() : "";
            if (!sub.isEmpty() && !Set.of("status", "exit", "give").contains(sub)) {
                return "sudo ****";
            }
        }
        if ("esplus".equals(root) && tokens.length > 1 && "password".equalsIgnoreCase(tokens[1])) {
            return "esplus password ****";
        }
        if ("setoppw".equals(root) || "changepw".equals(root)) {
            return root + " ****";
        }
        return input.length() > 512 ? input.substring(0, 512) : input;
    }
}
