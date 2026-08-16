package com.esplus.security.connect;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.network.ConnectionFingerprintPayload;
import com.esplus.security.SecurityService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ConnectionFingerprintTrigger {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFingerprintTrigger.class);

    private final SecurityService securityService;

    public ConnectionFingerprintTrigger(SecurityService securityService) {
        this.securityService = securityService;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ConnectionFingerprintManager manager = securityService.getFingerprintManager();
        if (manager == null) {
            return;
        }

        String clientIp = resolveClientIp(player);
        ConnectionFingerprintManager.FingerprintSession session = manager.createSession(
                player.getUUID(), player.getGameProfile().getName(), clientIp);

        player.connection.latency();
        manager.onLatencyUpdate(player.getUUID(), player.connection.latency());

        if (Config.UDP_PROBE_ENABLED.getAsBoolean() && manager.udpProbe() != null) {
            String token = UUID.randomUUID().toString();
            manager.udpProbe().registerProbeToken(token, player.getUUID());
            int udpPort = manager.udpProbe().start();
            if (udpPort > 0) {
                String serverHost = resolveServerHost(clientIp);
                dispatchUdpProbeToClient(token, serverHost, udpPort, player.getUUID());
            }
        }

        dispatchClientCollector(player.getUUID());
        LOGGER.debug("Fingerprint triggered for {} ({})", player.getGameProfile().getName(), player.getUUID());
    }

    private static String resolveClientIp(ServerPlayer player) {
        try {
            java.net.SocketAddress sa = player.connection.getRemoteAddress();
            if (sa instanceof InetSocketAddress addr && addr.getAddress() != null) {
                return addr.getAddress().getHostAddress();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String resolveServerHost(String clientIp) {
        if (clientIp == null || "unknown".equals(clientIp)) {
            return "127.0.0.1";
        }
        return clientIp;
    }

    private static void dispatchClientCollector(UUID playerUuid) {
        if (FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.esplus.ui.ClientFingerprintCollector");
            Method method = clazz.getMethod("collectAndSendToServer");
            method.invoke(null);
        } catch (ReflectiveOperationException ex) {
            LOGGER.debug("Client fingerprint collector not available (normal on dedicated server)");
        }
    }

    private static void dispatchUdpProbeToClient(String token, String serverHost, int udpPort, UUID playerUuid) {
        if (FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("com.esplus.ui.ClientFingerprintCollector");
            Method method = clazz.getMethod("sendUdpProbe", String.class, String.class, int.class);
            boolean ok = (boolean) method.invoke(null, token, serverHost, udpPort);
            if (ok) {
                ConnectionFingerprintPayload ack = new ConnectionFingerprintPayload("", "", token, "udp_probe_result");
                try {
                    Class<?> pdClass = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
                    Method sendMethod = pdClass.getMethod("sendToServer", com.esplus.network.ConnectionFingerprintPayload.class);
                    sendMethod.invoke(null, ack);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        } catch (ReflectiveOperationException ex) {
            LOGGER.debug("UDP probe client dispatch failed (non-fatal)");
        }
    }
}
