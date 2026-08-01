package com.esplus.audit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.esplus.Config;
import com.esplus.security.SecurityService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class MovementTracker {
    private final SecurityService security;
    private final Map<UUID, LastSample> last = new ConcurrentHashMap<>();
    private int tickCounter;

    public MovementTracker(SecurityService security) {
        this.security = security;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        int interval = Math.max(20, Config.MOVEMENT_SAMPLE_TICKS.getAsInt());
        if (tickCounter % interval != 0) {
            return;
        }
        AuditService audit = security.auditService();
        if (audit == null || !audit.isReady()) {
            return;
        }
        double minDist = Config.MOVEMENT_MIN_DISTANCE.get();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            LastSample prev = last.get(id);
            if (prev != null) {
                double dx = x - prev.x;
                double dy = y - prev.y;
                double dz = z - prev.z;
                if (dx * dx + dy * dy + dz * dz < minDist * minDist) {
                    continue;
                }
            }
            last.put(id, new LastSample(x, y, z));
            audit.recordMovement(new MovementSample(
                    System.currentTimeMillis(),
                    id.toString(),
                    player.getGameProfile().getName(),
                    player.level().dimension().location().toString(),
                    x, y, z,
                    player.getYRot(),
                    player.getXRot(),
                    player.onGround(),
                    player.isSprinting(),
                    player.getAbilities().flying
            ));
        }
    }

    private record LastSample(double x, double y, double z) {
    }
}
