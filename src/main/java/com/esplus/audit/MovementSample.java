package com.esplus.audit;

public record MovementSample(
        long ts,
        String playerUuid,
        String playerName,
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean onGround,
        boolean sprinting,
        boolean flying
) {
}
