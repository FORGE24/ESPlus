package com.esplus.ui;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.network.ConnectionFingerprintPayload;

import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientFingerprintCollector {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientFingerprintCollector.class);
    private static final String HwidSalt = "esplus-fingerprint-v1";

    private ClientFingerprintCollector() {
    }

    public static String collectHwid() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(HwidSalt.getBytes(StandardCharsets.UTF_8));
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                md.update(mac);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            LOGGER.warn("HWID collection failed", ex);
            return "unknown-hwid";
        }
    }

    public static String collectZoneId() {
        try {
            return ZoneId.systemDefault().getId();
        } catch (Exception ex) {
            LOGGER.warn("ZoneId collection failed", ex);
            return "UTC";
        }
    }

    public static void collectAndSendToServer() {
        String hwid = collectHwid();
        String zoneId = collectZoneId();
        ConnectionFingerprintPayload payload = new ConnectionFingerprintPayload(hwid, zoneId, "", "hwid_report");
        PacketDistributor.sendToServer(payload);
        LOGGER.debug("Sent HWID fingerprint hwid={} zone={}", hwid, zoneId);
    }

    public static boolean sendUdpProbe(String token, String serverHost, int serverUdpPort) {
        if (token == null || serverHost == null || serverUdpPort <= 0) {
            return false;
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            byte[] data = token.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(serverHost);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, serverUdpPort);
            socket.send(packet);
            LOGGER.debug("Sent UDP probe token={} to {}:{}", token, serverHost, serverUdpPort);
            return true;
        } catch (Exception ex) {
            LOGGER.debug("UDP probe send failed (may be blocked by firewall): {}", ex.getMessage());
            return false;
        }
    }
}
