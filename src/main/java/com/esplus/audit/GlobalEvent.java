package com.esplus.audit;

import java.util.UUID;

public record GlobalEvent(
        String eventId,
        long ts,
        String category,
        String action,
        String actorUuid,
        String actorName,
        String targetUuid,
        String targetName,
        String dimension,
        Double x,
        Double y,
        Double z,
        String itemId,
        String traceId,
        String detail,
        String source
) {
    public static GlobalEvent of(
            String category,
            String action,
            UUID actorUuid,
            String actorName,
            String detail,
            String source
    ) {
        return new GlobalEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                category,
                action,
                actorUuid == null ? null : actorUuid.toString(),
                actorName,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                detail,
                source
        );
    }
}
