package com.esplus.security.connect;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;
import com.esplus.security.db.SqliteDatabase;

public final class ConnectionFingerprintManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionFingerprintManager.class);

    private static final int PENALTY_RTT_TOO_LOW = -30;
    private static final int PENALTY_ZONE_IP_MISMATCH = -25;
    private static final int PENALTY_HWID_BLACKLIST = -100;
    private static final int PENALTY_UDP_IP_MISMATCH = -40;
    private static final int PENALTY_GEOIP_BLOCKED = -50;

    private final Map<UUID, FingerprintSession> sessions = new ConcurrentHashMap<>();
    private final SqliteDatabase database;
    private final GeoIpResolver geoResolver;
    private final HwidDao hwidDao;
    private final UdpProbeListener udpProbe;

    public ConnectionFingerprintManager(SqliteDatabase database, GeoIpResolver geoResolver, HwidDao hwidDao, UdpProbeListener udpProbe) {
        this.database = database;
        this.geoResolver = geoResolver;
        this.hwidDao = hwidDao;
        this.udpProbe = udpProbe;
    }

    public UdpProbeListener udpProbe() {
        return udpProbe;
    }

    public GeoIpResolver geoResolver() {
        return geoResolver;
    }

    public FingerprintSession createSession(UUID playerUuid, String playerName, String clientIp) {
        long now = System.currentTimeMillis();
        GeoIpResolver.GeoResult geo = geoResolver.resolve(clientIp);
        FingerprintSession session = new FingerprintSession(
                playerUuid,
                playerName,
                null,
                null,
                clientIp,
                geo.countryIso(),
                geo.region(),
                geo.city(),
                geo.lat(),
                geo.lon(),
                null,
                100,
                new ArrayList<>(),
                now
        );
        sessions.put(playerUuid, session);
        persist(session);
        LOGGER.info("Created fingerprint session for {} ({}) geo={}", playerName, playerUuid, geo.countryIso());
        return session;
    }

    public void removeSession(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    public FingerprintSession getSession(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public void onHwidReport(UUID playerUuid, String hwid, String zoneId) {
        FingerprintSession session = sessions.get(playerUuid);
        if (session == null) {
            LOGGER.warn("HWID report for unknown session {}", playerUuid);
            return;
        }
        session.hwid = hwid;
        session.systemZoneId = zoneId;
        reevaluate(session);
    }

    public void onLatencyUpdate(UUID playerUuid, int latencyMs) {
        FingerprintSession session = sessions.get(playerUuid);
        if (session == null) {
            return;
        }
        session.serverLatencyMs = latencyMs;
        reevaluate(session);
    }

    public void onUdpProbeResult(UUID playerUuid, String externalIp) {
        FingerprintSession session = sessions.get(playerUuid);
        if (session == null) {
            return;
        }
        session.udpProbeExternalIp = externalIp;
        reevaluate(session);
    }

    public void onUdpProbeFailed(UUID playerUuid) {
        FingerprintSession session = sessions.get(playerUuid);
        if (session == null) {
            return;
        }
        session.flaggedReasons.add("UDP_PROBE_FAILED");
        LOGGER.debug("UDP probe failed for {} (non-penalizing)", playerUuid);
    }

    private synchronized void reevaluate(FingerprintSession session) {
        session.flaggedReasons.clear();
        session.confidenceScore = 100;

        if (Config.RTT_PARADOX_ENABLED.getAsBoolean()
                && session.serverLatencyMs != null
                && session.geoLat != null && session.geoLon != null) {
            double minRtt = geoResolver.minimumRttMs(new GeoIpResolver.GeoResult(
                    session.geoCountry, null, null, null, session.geoLat, session.geoLon));
            if (minRtt > 0 && session.serverLatencyMs < minRtt * 1.2) {
                session.flaggedReasons.add("RTT_TOO_LOW");
                session.confidenceScore += PENALTY_RTT_TOO_LOW;
            }
        }

        if (Config.ZONE_IP_CHECK_ENABLED.getAsBoolean()
                && session.systemZoneId != null
                && session.geoCountry != null) {
            boolean asiaZone = session.systemZoneId.startsWith("Asia/");
            boolean usLike = "US".equalsIgnoreCase(session.geoCountry)
                    || "CA".equalsIgnoreCase(session.geoCountry)
                    || "MX".equalsIgnoreCase(session.geoCountry);
            boolean euLike = "GB".equalsIgnoreCase(session.geoCountry)
                    || "DE".equalsIgnoreCase(session.geoCountry)
                    || "FR".equalsIgnoreCase(session.geoCountry)
                    || "RU".equalsIgnoreCase(session.geoCountry);
            if ((usLike || euLike) && asiaZone) {
                session.flaggedReasons.add("ZONE_IP_MISMATCH");
                session.confidenceScore += PENALTY_ZONE_IP_MISMATCH;
            }
        }

        if (Config.HWID_BLACKLIST_ENABLED.getAsBoolean()
                && session.hwid != null && hwidDao.exists(session.hwid)) {
            session.flaggedReasons.add("HWID_BLACKLIST");
            session.confidenceScore += PENALTY_HWID_BLACKLIST;
        }

        if (Config.UDP_PROBE_ENABLED.getAsBoolean()
                && session.udpProbeExternalIp != null
                && session.clientIp != null
                && !session.clientIp.equals(session.udpProbeExternalIp)) {
            session.flaggedReasons.add("UDP_IP_MISMATCH");
            session.confidenceScore += PENALTY_UDP_IP_MISMATCH;
        }

        session.confidenceScore = Math.max(0, Math.min(100, session.confidenceScore));

        persist(session);
        LOGGER.info("Fingerprint re-evaluated for {}: confidence={} flags={}",
                session.playerUuid, session.confidenceScore, session.flaggedReasons);
    }

    public boolean shouldAutoKick(FingerprintSession session) {
        return session.confidenceScore < Config.CONFIDENCE_THRESHOLD.get();
    }

    private void persist(FingerprintSession session) {
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    """
                    INSERT OR REPLACE INTO connection_fingerprints (
                        player_uuid, hwid, zone_id, client_ip, geo_country, geo_region, geo_city,
                        geo_lat, geo_lon, udp_external_ip, server_latency_ms, confidence_score, flagged_reasons, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                ps.setString(1, session.playerUuid.toString());
                ps.setString(2, session.hwid);
                ps.setString(3, session.systemZoneId);
                ps.setString(4, session.clientIp);
                ps.setString(5, session.geoCountry);
                ps.setString(6, session.geoRegion);
                ps.setString(7, session.geoCity);
                if (session.geoLat != null) {
                    ps.setDouble(8, session.geoLat);
                } else {
                    ps.setNull(8, java.sql.Types.REAL);
                }
                if (session.geoLon != null) {
                    ps.setDouble(9, session.geoLon);
                } else {
                    ps.setNull(9, java.sql.Types.REAL);
                }
                ps.setString(10, session.udpProbeExternalIp);
                if (session.serverLatencyMs != null) {
                    ps.setInt(11, session.serverLatencyMs);
                } else {
                    ps.setNull(11, java.sql.Types.INTEGER);
                }
                ps.setInt(12, session.confidenceScore);
                ps.setString(13, String.join("|", session.flaggedReasons));
                ps.setLong(14, session.createdAt);
                ps.executeUpdate();
            } catch (SQLException ex) {
                LOGGER.warn("Failed to persist fingerprint session", ex);
            }
        }
    }

    public static final class FingerprintSession {
        public final UUID playerUuid;
        public final String playerName;
        public String hwid;
        public String systemZoneId;
        public final String clientIp;
        public final String geoCountry;
        public final String geoRegion;
        public final String geoCity;
        public final Double geoLat;
        public final Double geoLon;
        public String udpProbeExternalIp;
        public Integer serverLatencyMs;
        public int confidenceScore;
        public final List<String> flaggedReasons;
        public final long createdAt;

        public FingerprintSession(UUID playerUuid, String playerName, String hwid, String systemZoneId,
                                  String clientIp, String geoCountry, String geoRegion, String geoCity,
                                  Double geoLat, Double geoLon, String udpProbeExternalIp,
                                  int confidenceScore, List<String> flaggedReasons, long createdAt) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.hwid = hwid;
            this.systemZoneId = systemZoneId;
            this.clientIp = clientIp;
            this.geoCountry = geoCountry;
            this.geoRegion = geoRegion;
            this.geoCity = geoCity;
            this.geoLat = geoLat;
            this.geoLon = geoLon;
            this.udpProbeExternalIp = udpProbeExternalIp;
            this.confidenceScore = confidenceScore;
            this.flaggedReasons = flaggedReasons;
            this.createdAt = createdAt;
        }
    }
}
