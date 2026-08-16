package com.esplus.security.connect;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UdpProbeListener implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UdpProbeListener.class);
    private static final int BUFFER = 1024;
    private static final int SOCKET_TIMEOUT_MS = 60_000;

    private final Map<String, UUID> pendingTokens = new ConcurrentHashMap<>();
    private final Map<UUID, String> observedUdpIps = new ConcurrentHashMap<>();
    private DatagramSocket socket;
    private volatile boolean running;
    private int boundPort = -1;

    public synchronized int start() {
        if (running) {
            return boundPort;
        }
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            boundPort = socket.getLocalPort();
            running = true;
            Thread.ofPlatform().name("esplus-udp-probe").start(this::runLoop);
            LOGGER.info("UDP probe listener bound to port {}", boundPort);
            return boundPort;
        } catch (SocketException ex) {
            LOGGER.warn("UDP probe listener failed to bind; UDP probe disabled: {}", ex.getMessage());
            close();
            return -1;
        }
    }

    public void registerProbeToken(String token, UUID playerUuid) {
        if (token != null && playerUuid != null) {
            pendingTokens.put(token, playerUuid);
        }
    }

    public String observedExternalIp(UUID playerUuid) {
        return observedUdpIps.get(playerUuid);
    }

    private void runLoop() {
        byte[] buf = new byte[BUFFER];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String token = new String(packet.getData(), 0, packet.getLength()).trim();
                UUID playerUuid = pendingTokens.remove(token);
                if (playerUuid != null) {
                    String sourceIp = packet.getAddress().getHostAddress();
                    observedUdpIps.put(playerUuid, sourceIp);
                    LOGGER.debug("UDP probe matched: player={} externalIp={}", playerUuid, sourceIp);
                }
            } catch (java.net.SocketTimeoutException ex) {
                if (!running) {
                    break;
                }
            } catch (IOException ex) {
                if (running) {
                    LOGGER.debug("UDP probe receive loop error (non-fatal): {}", ex.getMessage());
                }
            }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        socket = null;
        boundPort = -1;
        pendingTokens.clear();
    }
}
