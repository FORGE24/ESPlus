package com.esplus.security.risk;

public record RiskDecision(String command, RiskLevel level, boolean requiresSudo, String reason) {
    public static RiskDecision none(String command) {
        return new RiskDecision(command, RiskLevel.NONE, false, "not_protected");
    }

    public static RiskDecision of(String command, RiskLevel level, String reason) {
        return new RiskDecision(command, level, level.requiresSudo(), reason);
    }
}
