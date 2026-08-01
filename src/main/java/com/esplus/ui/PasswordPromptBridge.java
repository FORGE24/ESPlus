package com.esplus.ui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.network.OpenPasswordPromptPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side coordinator: ask the player's client to open the Qt password window.
 */
public final class PasswordPromptBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MS = 5 * 60_000L;

    public enum Purpose {
        SUDO_AUTH,
        SUDO_TOTP,
        SET_PASSWORD,
        CHANGE_OLD,
        CHANGE_NEW
    }

    public record Pending(
            UUID playerId,
            Purpose purpose,
            long createdAt,
            BiConsumer<ServerPlayer, String> onSuccess,
            Runnable onCancel
    ) {
    }

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void request(
            ServerPlayer player,
            Purpose purpose,
            String title,
            String prompt,
            boolean confirm,
            BiConsumer<ServerPlayer, String> onSuccess,
            Runnable onCancel
    ) {
        purgeExpired();
        // One outstanding prompt per player
        pending.entrySet().removeIf(e -> e.getValue().playerId().equals(player.getUUID()));

        UUID requestId = UUID.randomUUID();
        pending.put(requestId, new Pending(player.getUUID(), purpose, System.currentTimeMillis(), onSuccess, onCancel));
        PacketDistributor.sendToPlayer(player, new OpenPasswordPromptPayload(
                requestId,
                purpose.name(),
                title,
                prompt,
                confirm
        ));
        player.sendSystemMessage(Component.translatable("esplus.password.ui_opening"));
        LOGGER.debug("Requested Qt password UI for {} purpose={}", player.getGameProfile().getName(), purpose);
    }

    public void complete(ServerPlayer player, UUID requestId, String purpose, boolean canceled, String password) {
        Pending entry = pending.remove(requestId);
        if (entry == null) {
            return;
        }
        if (!entry.playerId().equals(player.getUUID())) {
            LOGGER.warn("Password prompt result player mismatch for {}", requestId);
            return;
        }
        if (!entry.purpose().name().equals(purpose)) {
            LOGGER.warn("Password prompt purpose mismatch for {}", requestId);
            return;
        }
        if (canceled || password == null || password.isBlank()) {
            if (entry.onCancel() != null) {
                entry.onCancel().run();
            }
            return;
        }
        entry.onSuccess().accept(player, password);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().createdAt() > TIMEOUT_MS);
    }
}
