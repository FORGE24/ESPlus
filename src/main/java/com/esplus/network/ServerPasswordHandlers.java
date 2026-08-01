package com.esplus.network;

import com.esplus.ESPlus;
import com.esplus.ui.PasswordPromptBridge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPasswordHandlers {
    private ServerPasswordHandlers() {
    }

    public static void handleResult(PasswordPromptResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            PasswordPromptBridge bridge = ESPlus.getPasswordBridge();
            if (bridge == null) {
                return;
            }
            bridge.complete(player, payload.requestId(), payload.purpose(), payload.canceled(), payload.password());
        });
    }
}
