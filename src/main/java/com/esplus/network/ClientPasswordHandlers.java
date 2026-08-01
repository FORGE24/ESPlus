package com.esplus.network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.ui.QtPasswordPrompt;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPasswordHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService WORKERS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "esplus-qt-pw");
        t.setDaemon(true);
        return t;
    });

    private ClientPasswordHandlers() {
    }

    public static void handleOpen(OpenPasswordPromptPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            var gameDir = mc.gameDirectory.toPath();
            WORKERS.execute(() -> {
                QtPasswordPrompt.PromptResult result = QtPasswordPrompt.prompt(
                        gameDir,
                        payload.title(),
                        payload.prompt(),
                        payload.confirm()
                );
                boolean canceled = result.status() != QtPasswordPrompt.PromptResult.Status.OK;
                String password = canceled ? "" : result.password();
                context.enqueueWork(() -> PacketDistributor.sendToServer(new PasswordPromptResultPayload(
                        payload.requestId(),
                        payload.purpose(),
                        canceled,
                        password
                )));
                if (result.status() == QtPasswordPrompt.PromptResult.Status.UNAVAILABLE) {
                    LOGGER.error("Qt password prompt unavailable on client");
                }
            });
        });
    }
}
