package com.esplus.audit;

public record ItemTraceLink(
        long id,
        String traceId,
        String parentTraceId,
        String eventId,
        long ts,
        String action,
        String actorUuid,
        String actorName,
        String detail
) {
}
