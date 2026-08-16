package com.esplus.security.connect;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.ESPlus;
import com.esplus.network.ConnectionFingerprintPayload;
import com.esplus.security.SecurityService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerConnectionFingerprintHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerConnectionFingerprintHandlers.class);

    private ServerConnectionFingerprintHandlers() {
    }

    public static void handle(ConnectionFingerprintPayload payload, IPayloadContext context) {
        SecurityService securityService = ESPlus.getSecurityService();
        if (securityService == null || !securityService.isReady()) {
            return;
        }
        ConnectionFingerprintManager manager = securityService.getFingerprintManager();
        if (manager == null) {
            return;
        }
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) {
                return;
            }
            UUID uuid = player.getUUID();
            String name = player.getGameProfile().getName();

            if ("hwid_report".equalsIgnoreCase(payload.mode())) {
                manager.onHwidReport(uuid, payload.hwid(), payload.zoneId());
            } else if ("udp_probe_result".equalsIgnoreCase(payload.mode())) {
                boolean ok = manager.udpProbe() != null && manager.udpProbe().observedExternalIp(uuid) != null;
                if (ok) {
                    String ip = manager.udpProbe().observedExternalIp(uuid);
                    manager.onUdpProbeResult(uuid, ip);
                } else {
                    manager.onUdpProbeFailed(uuid);
                }
            }

            ConnectionFingerprintManager.FingerprintSession session = manager.getSession(uuid);
            if (session != null && manager.shouldAutoKick(session)) {
                LOGGER.warn("Auto-kicking player {} ({}) confidence={} reasons={}",
                        name, uuid, session.confidenceScore, session.flaggedReasons);
                player.connection.disconnect(Component.literal(
                        "[ESPlus] 连接指纹可信度过低 (" + session.confidenceScore + ")"
                                + ": " + String.join(", ", session.flaggedReasons)
                                + ". 如有疑虑请联系服务器管理员."
                ));
            }
        });
    }
}
