package com.esplus.audit.snapshot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.security.db.SqliteDatabase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class BlockSnapshotTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockSnapshotTracker.class);

    private final SqliteDatabase database;

    public BlockSnapshotTracker(SqliteDatabase database) {
        this.database = database;
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        insertSnapshot(
                player,
                "break",
                event.getPos(),
                player.level().dimension().location().toString(),
                null,
                blockId
        );
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String newBlockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
        String oldBlockId = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).toString();
        insertSnapshot(
                player,
                "place",
                event.getPos(),
                player.level().dimension().location().toString(),
                oldBlockId,
                newBlockId
        );
    }

    public List<BlockSnapshot> findByPlayerAndTimeRange(UUID playerUuid, long fromTs, long toTs) {
        List<BlockSnapshot> rows = new ArrayList<>();
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    """
                    SELECT id, ts, player_uuid, player_name, action, dimension, x, y, z, block_id, old_block_id
                    FROM block_snapshots
                    WHERE player_uuid = ? AND ts BETWEEN ? AND ?
                    ORDER BY ts ASC
                    """)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, fromTs);
                ps.setLong(3, toTs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new BlockSnapshot(
                                rs.getLong("id"),
                                rs.getLong("ts"),
                                rs.getString("player_uuid"),
                                rs.getString("player_name"),
                                rs.getString("action"),
                                rs.getString("dimension"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("block_id"),
                                rs.getString("old_block_id")
                        ));
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Block snapshot query failed for player={}", playerUuid, ex);
            }
        }
        return rows;
    }

    private void insertSnapshot(
            ServerPlayer player,
            String action,
            BlockPos pos,
            String dimension,
            String oldBlockId,
            String blockId
    ) {
        if (database == null) {
            return;
        }
        long ts = System.currentTimeMillis();
        String playerUuid = player.getUUID().toString();
        String playerName = player.getGameProfile().getName();
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    """
                    INSERT INTO block_snapshots (ts, player_uuid, player_name, action, dimension, x, y, z, block_id, old_block_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setLong(1, ts);
                ps.setString(2, playerUuid);
                ps.setString(3, playerName);
                ps.setString(4, action);
                ps.setString(5, dimension);
                ps.setInt(6, pos.getX());
                ps.setInt(7, pos.getY());
                ps.setInt(8, pos.getZ());
                ps.setString(9, blockId);
                ps.setString(10, oldBlockId);
                ps.executeUpdate();
            } catch (Exception ex) {
                LOGGER.warn("Failed to write block snapshot for {} {} at {}", action, blockId, pos, ex);
            }
        }
    }

    public record BlockSnapshot(
            long id,
            long ts,
            String playerUuid,
            String playerName,
            String action,
            String dimension,
            int x,
            int y,
            int z,
            String blockId,
            String oldBlockId
    ) {
        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }
}
