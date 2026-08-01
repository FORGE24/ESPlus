package com.esplus.security.risk;

public enum RiskLevel {
    NONE,
    HIGH,
    CRITICAL;

    public boolean requiresSudo() {
        return this != NONE;
    }

    public static RiskLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return HIGH;
        }
        try {
            return RiskLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return HIGH;
        }
    }
}
