package com.esplus.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconstructs an incident timeline from events, movements and item links.
 */
public final class IncidentChainService {
    private final GlobalEventDao eventDao;
    private final MovementDao movementDao;
    private final ItemTraceDao itemTraceDao;

    public IncidentChainService(GlobalEventDao eventDao, MovementDao movementDao, ItemTraceDao itemTraceDao) {
        this.eventDao = eventDao;
        this.movementDao = movementDao;
        this.itemTraceDao = itemTraceDao;
    }

    public Map<String, Object> reconstruct(String seedEventId, long windowMs) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        GlobalEvent seed = eventDao.findByEventId(seedEventId).orElse(null);
        result.put("seedEventId", seedEventId);
        if (seed == null) {
            result.put("found", false);
            return result;
        }
        result.put("found", true);
        result.put("seed", seed);

        List<GlobalEvent> timeline = eventDao.around(seed.ts(), windowMs, seed.actorUuid(), 1000);
        result.put("events", timeline);

        if (seed.actorUuid() != null) {
            result.put("movements", movementDao.forPlayer(
                    seed.actorUuid(),
                    seed.ts() - windowMs,
                    seed.ts() + windowMs,
                    2000
            ));
        } else {
            result.put("movements", List.of());
        }

        if (seed.traceId() != null && !seed.traceId().isBlank()) {
            result.put("itemTrace", itemTraceDao.findTrace(seed.traceId()).orElse(null));
            result.put("itemLinks", itemTraceDao.linksForTrace(seed.traceId()));
        } else {
            result.put("itemTrace", null);
            result.put("itemLinks", List.of());
        }

        List<String> summary = new ArrayList<>();
        summary.add("中心事件: " + seed.category() + "/" + seed.action() + " @ " + seed.ts());
        summary.add("关联事件数: " + timeline.size());
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> reconstructByTrace(String traceId) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        var trace = itemTraceDao.findTrace(traceId);
        result.put("found", trace.isPresent());
        result.put("itemTrace", trace.orElse(null));
        result.put("itemLinks", itemTraceDao.linksForTrace(traceId));
        result.put("events", eventDao.search(null, null, null, traceId, 0L, 0L, 500));
        return result;
    }
}
