package com.esplus.audit;

public record ItemTrace(
        String traceId,
        String itemId,
        long createdAt,
        String originType,
        String originActorUuid,
        String originActorName,
        String originDetail
) {
}
