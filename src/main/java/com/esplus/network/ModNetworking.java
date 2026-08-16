package com.esplus.network;

import java.lang.reflect.Method;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.ESPlus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ESPlus.MODID)
public final class ModNetworking {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                OpenPasswordPromptPayload.TYPE,
                OpenPasswordPromptPayload.STREAM_CODEC,
                ModNetworking::dispatchClientOpen
        );
        registrar.playToServer(
                PasswordPromptResultPayload.TYPE,
                PasswordPromptResultPayload.STREAM_CODEC,
                ServerPasswordHandlers::handleResult
        );
        registrar.playToClient(
                ConnectionFingerprintPayload.TYPE,
                ConnectionFingerprintPayload.STREAM_CODEC,
                (payload, context) -> {}
        );
        registrar.playToServer(
                ConnectionFingerprintPayload.TYPE,
                ConnectionFingerprintPayload.STREAM_CODEC,
                com.esplus.security.connect.ServerConnectionFingerprintHandlers::handle
        );
    }

    private static void dispatchClientOpen(OpenPasswordPromptPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.esplus.network.ClientPasswordHandlers");
            Method method = clazz.getMethod("handleOpen", OpenPasswordPromptPayload.class, IPayloadContext.class);
            method.invoke(null, payload, context);
        } catch (ReflectiveOperationException ex) {
            LOGGER.error("Failed to dispatch Qt password prompt on client", ex);
        }
    }
}
