package com.esplus.audit;

public record AlertRecord(
        String alertId,
        long ts,
        String severity,
        String ruleCode,
        String title,
        String message,
        String actorUuid,
        String actorName,
        String relatedEventId,
        String relatedTraceId,
        boolean acknowledged
) {
}
