package com.esplus.security.connect;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.Config;

public final class GeoIpResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeoIpResolver.class);
    private static final double C_KM_PER_SEC = 300_000.0;

    private final Map<String, GeoResult> countryIndex = new HashMap<>();
    private final double serverLat;
    private final double serverLon;

    public GeoIpResolver() {
        this.serverLat = Config.SERVER_GEO_LAT.get();
        this.serverLon = Config.SERVER_GEO_LON.get();
        loadEmbeddedCsv();
    }

    public GeoResult resolve(String ip) {
        if (ip == null || ip.isBlank()) {
            return GeoResult.empty();
        }
        String countryIso = guessCountryFromIp(ip);
        GeoResult found = countryIndex.get(countryIso);
        if (found != null) {
            return found;
        }
        LOGGER.debug("GeoIP miss for ip={}, best-effort fallback unknown country", ip);
        return new GeoResult(countryIso, "Unknown", null, null, null, null);
    }

    public GeoResult serverGeo() {
        return new GeoResult("SERVER", "Server", null, null, serverLat, serverLon);
    }

    public double distanceKm(GeoResult a, GeoResult b) {
        if (a == null || b == null || a.lat() == null || a.lon() == null || b.lat() == null || b.lon() == null) {
            return Double.NaN;
        }
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }

    public double minimumRttMs(GeoResult clientGeo) {
        double d = distanceKm(clientGeo, serverGeo());
        if (Double.isNaN(d) || d <= 0) {
            return 0.0;
        }
        return (d / C_KM_PER_SEC) * 1000.0;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private void loadEmbeddedCsv() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("assets/esplus/geoip_simple.csv")) {
            if (is == null) {
                LOGGER.warn("geoip_simple.csv not found on classpath; GeoIP resolver will resolve unknown for all IPs");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                boolean headerSkipped = false;
                while ((line = reader.readLine()) != null) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    String[] parts = line.split(",");
                    if (parts.length < 4) {
                        continue;
                    }
                    String iso = parts[0].trim().toUpperCase();
                    String name = parts[1].trim();
                    Double lat = parseDoubleOrNull(parts[2]);
                    Double lon = parseDoubleOrNull(parts[3]);
                    if (!iso.isEmpty()) {
                        countryIndex.put(iso, new GeoResult(iso, name, null, null, lat, lon));
                    }
                }
                LOGGER.info("GeoIP resolver loaded {} country entries", countryIndex.size());
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load embedded geoip CSV", ex);
        }
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String guessCountryFromIp(String ip) {
        if (ip == null) {
            return "ZZ";
        }
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            return "ZZ";
        }
        try {
            int first = Integer.parseInt(octets[0]);
            if (first >= 1 && first <= 126) {
                return "US";
            }
            if (first == 114 || first == 115 || first == 220 || first == 221 || first == 222 || first == 223) {
                return "CN";
            }
            if (first >= 133 && first <= 133) {
                return "JP";
            }
            if (first >= 121 && first <= 123) {
                return "KR";
            }
            if (first >= 2 && first <= 3 || first >= 77 && first <= 79) {
                return "GB";
            }
            if (first >= 46 && first <= 46 || first >= 80 && first <= 81) {
                return "DE";
            }
            if (first >= 51 && first <= 51 || first >= 82 && first <= 82) {
                return "FR";
            }
            if (first >= 5 && first <= 5 || first >= 31 && first <= 31) {
                return "RU";
            }
            if (first >= 14 && first <= 15 || first >= 49 && first <= 49) {
                return "IN";
            }
            if (first >= 200 && first <= 201 || first >= 177 && first <= 179) {
                return "BR";
            }
            if (first >= 24 && first <= 24 || first >= 96 && first <= 99) {
                return "CA";
            }
            if (first >= 120 && first <= 122) {
                return "AU";
            }
            if (first >= 182 && first <= 183) {
                return "HK";
            }
            if (first >= 128 && first <= 129 || first >= 165 && first <= 165) {
                return "SG";
            }
            if (first >= 39 && first <= 39 || first >= 61 && first <= 61) {
                return "TW";
            }
        } catch (NumberFormatException ignored) {
        }
        return "ZZ";
    }

    public record GeoResult(
            String countryIso,
            String countryName,
            String region,
            String city,
            Double lat,
            Double lon
    ) {
        public static GeoResult empty() {
            return new GeoResult(null, null, null, null, null, null);
        }
    }
}
