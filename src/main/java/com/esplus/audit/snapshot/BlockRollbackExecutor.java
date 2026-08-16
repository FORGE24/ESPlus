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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

public final class BlockRollbackExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockRollbackExecutor.class);

    private final SqliteDatabase database;

    public BlockRollbackExecutor(SqliteDatabase database) {
        this.database = database;
    }

    public RollbackResult rollbackPlayerBlocks(UUID playerUuid, long fromTs, long toTs, ServerLevel level) {
        List<RollbackRow> rows = queryBreakRows(playerUuid, fromTs, toTs);
        int success = 0;
        int fail = 0;
        for (RollbackRow row : rows) {
            try {
                BlockPos pos = new BlockPos(row.x(), row.y(), row.z());
                if (!level.isLoaded(pos)) {
                    fail++;
                    LOGGER.debug("Skipping unloaded chunk at {} for rollback", pos);
                    continue;
                }
                Block block = BuiltInRegistries.BLOCK.get(
                        net.minecraft.resources.ResourceLocation.parse(row.oldBlockId()));
                if (block == null) {
                    fail++;
                    LOGGER.warn("Unknown block id {} during rollback", row.oldBlockId());
                    continue;
                }
                BlockState state = block.defaultBlockState();
                level.setBlock(pos, state, Block.UPDATE_ALL);
                success++;
            } catch (Exception ex) {
                fail++;
                LOGGER.warn("Rollback failed for pos {} blockId={}", row.x() + "," + row.y() + "," + row.z(), row.oldBlockId(), ex);
            }
        }
        LOGGER.info("Rolled back {} blocks for player {} (failed={}, total_found={})",
                success, playerUuid, fail, rows.size());
        return new RollbackResult(success, fail, rows.size());
    }

    private List<RollbackRow> queryBreakRows(UUID playerUuid, long fromTs, long toTs) {
        List<RollbackRow> rows = new ArrayList<>();
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    """
                    SELECT x, y, z, old_block_id
                    FROM block_snapshots
                    WHERE player_uuid = ? AND ts BETWEEN ? AND ? AND action = 'break' AND old_block_id IS NOT NULL
                    ORDER BY ts ASC
                    """)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, fromTs);
                ps.setLong(3, toTs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new RollbackRow(
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("old_block_id")
                        ));
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Block rollback query failed", ex);
            }
        }
        return rows;
    }

    private record RollbackRow(int x, int y, int z, String oldBlockId) {
    }

    public record RollbackResult(int successCount, int failCount, int totalFound) {
    }
}
