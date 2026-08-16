package com.esplus.audit.snapshot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.security.db.SqliteDatabase;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class InventorySnapshotTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventorySnapshotTracker.class);

    private final SqliteDatabase database;

    public InventorySnapshotTracker(SqliteDatabase database) {
        this.database = database;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        fullSnapshot(player, "login");
    }

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        fullSnapshot(player, "craft");
    }

    @SubscribeEvent
    public void onPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        fullSnapshot(player, "pickup");
    }

    @SubscribeEvent
    public void onToss(net.neoforged.neoforge.event.entity.item.ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        fullSnapshot(player, "toss");
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        fullSnapshot(player, "death");
    }

    public RestoreResult restoreInventory(UUID playerUuid, long targetTs, ServerPlayer targetPlayer) {
        List<SnapshotRow> rows = queryLatestSnapshot(playerUuid, targetTs);
        int restored = 0;
        int failed = 0;
        try {
            Inventory inv = targetPlayer.getInventory();
            inv.clearContent();
            targetPlayer.getEnderChestInventory().clearContent();

            for (SnapshotRow row : rows) {
                try {
                    ResourceLocation id = ResourceLocation.parse(row.itemId());
                    var item = BuiltInRegistries.ITEM.get(id);
                    if (item == null) {
                        failed++;
                        continue;
                    }
                    ItemStack stack = new ItemStack(item);
                    int count = Math.max(1, Math.min(row.count(), stack.getMaxStackSize()));
                    stack.setCount(count);
                    switch (row.section()) {
                        case "main" -> inv.setItem(row.slot(), stack);
                        case "armor" -> {
                            if (row.slot() >= 0 && row.slot() < inv.armor.size()) {
                                inv.armor.set(row.slot(), stack);
                            } else {
                                failed++;
                                continue;
                            }
                        }
                        case "offhand" -> inv.offhand.set(0, stack);
                        case "ender" -> targetPlayer.getEnderChestInventory().setItem(row.slot(), stack);
                        default -> {
                            failed++;
                            continue;
                        }
                    }
                    restored++;
                } catch (Exception ex) {
                    failed++;
                    LOGGER.warn("Restore failed for slot={} section={}", row.slot(), row.section(), ex);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Inventory restore failed for player={}", playerUuid, ex);
        }
        LOGGER.info("Restored inventory for player={} at ts={}: ok={}, failed={}", playerUuid, targetTs, restored, failed);
        return new RestoreResult(restored, failed);
    }

    public void fullSnapshot(ServerPlayer player, String source) {
        if (database == null) {
            return;
        }
        long ts = System.currentTimeMillis();
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        Inventory inv = player.getInventory();
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    """
                    INSERT INTO inventory_snapshots (player_uuid, player_name, ts, section, slot, item_id, count, display_name, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                writeSection(ps, uuid, name, ts, source, "main", 36, slot -> inv.getItem(slot));
                writeSection(ps, uuid, name, ts, source, "armor", inv.armor.size(), slot -> inv.armor.get(slot));
                if (!inv.offhand.isEmpty()) {
                    writeSection(ps, uuid, name, ts, source, "offhand", 1, slot -> inv.offhand.get(slot));
                }
                var ender = player.getEnderChestInventory();
                writeSection(ps, uuid, name, ts, source, "ender", ender.getContainerSize(), slot -> ender.getItem(slot));
            } catch (Exception ex) {
                LOGGER.warn("Failed to write inventory snapshot for {}", player.getGameProfile().getName(), ex);
            }
        }
    }

    private void writeSection(
            PreparedStatement ps,
            String uuid,
            String name,
            long ts,
            String source,
            String section,
            int size,
            SlotAccessor accessor
    ) throws java.sql.SQLException {
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = accessor.get(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String display = stack.getHoverName().getString();
            if (display != null && display.length() > 64) {
                display = display.substring(0, 64);
            }
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setLong(3, ts);
            ps.setString(4, section);
            ps.setInt(5, slot);
            ps.setString(6, itemId);
            ps.setInt(7, stack.getCount());
            ps.setString(8, display);
            ps.setString(9, source);
            ps.addBatch();
        }
        ps.executeBatch();
    }

    private List<SnapshotRow> queryLatestSnapshot(UUID playerUuid, long targetTs) {
        List<SnapshotRow> rows = new ArrayList<>();
        synchronized (database.lock()) {
            try (PreparedStatement tsPs = database.connection().prepareStatement(
                    """
                    SELECT MAX(ts) AS ts FROM inventory_snapshots
                    WHERE player_uuid = ? AND ts <= ?
                    """)) {
                tsPs.setString(1, playerUuid.toString());
                tsPs.setLong(2, targetTs);
                try (ResultSet tsRs = tsPs.executeQuery()) {
                    if (!tsRs.next()) {
                        return rows;
                    }
                    long actualTs = tsRs.getLong("ts");
                    try (PreparedStatement rowPs = database.connection().prepareStatement(
                            """
                            SELECT section, slot, item_id, count
                            FROM inventory_snapshots
                            WHERE player_uuid = ? AND ts = ?
                            ORDER BY section, slot
                            """)) {
                        rowPs.setString(1, playerUuid.toString());
                        rowPs.setLong(2, actualTs);
                        try (ResultSet rs = rowPs.executeQuery()) {
                            while (rs.next()) {
                                rows.add(new SnapshotRow(
                                        rs.getString("section"),
                                        rs.getInt("slot"),
                                        rs.getString("item_id"),
                                        rs.getInt("count")
                                ));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Inventory snapshot query failed", ex);
            }
        }
        return rows;
    }

    private interface SlotAccessor {
        ItemStack get(int slot);
    }

    private record SnapshotRow(String section, int slot, String itemId, int count) {
    }

    public record RestoreResult(int restored, int failed) {
    }
}
