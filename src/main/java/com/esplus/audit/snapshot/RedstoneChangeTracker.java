package com.esplus.audit.snapshot;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.audit.GlobalEvent;
import com.esplus.security.SecurityService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class RedstoneChangeTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedstoneChangeTracker.class);
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final long WINDOW_MS = 5000L;

    private final SecurityService security;
    private final Map<String, Map<Long, Long>> chunkChangeTimes = new HashMap<>();
    private final Map<String, Integer> lastSignalValues = new HashMap<>();
    private int tickCounter;

    public RedstoneChangeTracker(SecurityService security) {
        this.security = security;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        if (!security.isReady()) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                scanLevel(level, now);
            }
        } catch (Exception ex) {
            LOGGER.debug("Redstone scan failed", ex);
        }
        pruneOldEntries(now);
    }

    private void scanLevel(ServerLevel level, long now) {
        String dim = level.dimension().location().toString();
        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            int startCX = (center.getX() >> 4) - 2;
            int endCX = (center.getX() >> 4) + 2;
            int startCZ = (center.getZ() >> 4) - 2;
            int endCZ = (center.getZ() >> 4) + 2;
            for (int cx = startCX; cx <= endCX; cx++) {
                for (int cz = startCZ; cz <= endCZ; cz++) {
                    scanChunk(level, cx, cz, dim, now);
                }
            }
        }
    }

    private void scanChunk(ServerLevel level, int cx, int cz, String dim, long now) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int ly = 0; ly < level.getMaxBuildHeight(); ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    cursor.set(cx * 16 + lx, ly, cz * 16 + lz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    Block block = state.getBlock();
                    String blockPath = BuiltInRegistries.BLOCK.getKey(block).getPath();
                    if (!isRedstoneBlock(blockPath)) {
                        continue;
                    }
                    int signal = computeSignal(level, cursor, state, blockPath);
                    String key = dim + ":" + cursor.asLong();
                    Integer prev = lastSignalValues.get(key);
                    if (prev == null) {
                        lastSignalValues.put(key, signal);
                        continue;
                    }
                    if (prev != signal) {
                        lastSignalValues.put(key, signal);
                        recordChange(dim, cx, cz, now);
                    }
                }
            }
        }
    }

    private static boolean isRedstoneBlock(String path) {
        return "redstone_block".equals(path)
                || "redstone_repeater".equals(path)
                || "comparator".equals(path);
    }

    private static int computeSignal(ServerLevel level, BlockPos pos, BlockState state, String path) {
        return switch (path) {
            case "redstone_block" -> state.hasProperty(net.minecraft.world.level.block.RedStoneWireBlock.POWER)
                    ? state.getValue(net.minecraft.world.level.block.RedStoneWireBlock.POWER) : 0;
            case "redstone_repeater" -> {
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)) {
                    yield state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? 1 : 0;
                }
                yield 0;
            }
            case "comparator" -> level.getBestNeighborSignal(pos);
            default -> 0;
        };
    }

    private void recordChange(String dim, int cx, int cz, long now) {
        String chunkKey = dim + ":" + cx + "," + cz;
        chunkChangeTimes.computeIfAbsent(chunkKey, k -> new HashMap<>())
                .merge(now, 1L, Long::sum);
        try {
            long count = countInWindow(chunkKey, now);
            int threshold = Config.ANOMALY_REDSTONE_BURST.getAsInt();
            if (count >= threshold) {
                GlobalEvent ge = GlobalEvent.of(
                        "redstone",
                        "redstone_burst",
                        null,
                        null,
                        chunkKey + " changes=" + count + " in window",
                        "redstone_tracker"
                );
                var audit = security.auditService();
                if (audit != null) {
                    audit.recordAsync(ge);
                }
            }
        } catch (Exception ex) {
            LOGGER.debug("Redstone anomaly check failed", ex);
        }
    }

    private long countInWindow(String chunkKey, long now) {
        Map<Long, Long> map = chunkChangeTimes.get(chunkKey);
        if (map == null) {
            return 0;
        }
        long total = 0;
        for (Map.Entry<Long, Long> e : map.entrySet()) {
            if (now - e.getKey() <= WINDOW_MS) {
                total += e.getValue();
            }
        }
        return total;
    }

    private void pruneOldEntries(long now) {
        chunkChangeTimes.entrySet().removeIf(e -> {
            long oldest = Long.MAX_VALUE;
            for (Long ts : e.getValue().keySet()) {
                oldest = Math.min(oldest, ts);
            }
            return oldest != Long.MAX_VALUE && now - oldest > WINDOW_MS * 2;
        });
    }
}
