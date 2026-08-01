package com.esplus.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esplus.security.crypto.RsaKeyManager;
import com.esplus.security.db.SqliteDatabase;

public final class AuditService implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditService.class);

    private final SqliteDatabase database;
    private final GlobalEventDao eventDao;
    private final ItemTraceDao itemTraceDao;
    private final MovementDao movementDao;
    private final AlertDao alertDao;
    private final AnomalyEngine anomalyEngine;
    private final IncidentChainService incidentChainService;
    private final AlertWebhookDispatcher webhookDispatcher;
    private final AdminRiskScorer adminRiskScorer;
    private final AutoResponseEngine autoResponseEngine;
    private final AuditBlockSigner blockSigner;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "esplus-audit-writer");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean ready;

    public AuditService(
            SqliteDatabase database,
            RsaKeyManager rsa,
            int commandBurstThreshold,
            int giveBurstThreshold,
            int breakBurstThreshold,
            long anomalyWindowMs
    ) {
        this.database = database;
        this.eventDao = new GlobalEventDao(database);
        this.itemTraceDao = new ItemTraceDao(database);
        this.movementDao = new MovementDao(database);
        this.alertDao = new AlertDao(database);
        this.webhookDispatcher = new AlertWebhookDispatcher(database);
        this.autoResponseEngine = new AutoResponseEngine(database);
        this.blockSigner = new AuditBlockSigner(database, rsa);
        this.anomalyEngine = new AnomalyEngine(
                alertDao, webhookDispatcher, autoResponseEngine,
                commandBurstThreshold, giveBurstThreshold, breakBurstThreshold, anomalyWindowMs);
        this.adminRiskScorer = new AdminRiskScorer(database, alertDao, webhookDispatcher);
        this.incidentChainService = new IncidentChainService(eventDao, movementDao, itemTraceDao);
        this.ready = true;
    }

    public GlobalEventDao.IntegrityReport verifyIntegrity(int maxRows) {
        try {
            return eventDao.verifyChain(maxRows);
        } catch (Exception ex) {
            LOGGER.warn("Integrity verify failed", ex);
            return new GlobalEventDao.IntegrityReport(false, 0, "error:" + ex.getMessage(), null, null, false);
        }
    }

    public List<Map<String, Object>> recomputeAdminRisk(int windowDays) {
        return adminRiskScorer.recompute(windowDays);
    }

    public AlertWebhookDispatcher webhookDispatcher() {
        return webhookDispatcher;
    }

    public boolean isReady() {
        return ready;
    }

    public SqliteDatabase database() {
        return database;
    }

    public void recordAsync(GlobalEvent event) {
        if (!ready) {
            return;
        }
        writer.execute(() -> {
            try {
                eventDao.insert(event);
                anomalyEngine.inspect(event);
                blockSigner.onEventPersisted();
            } catch (Exception ex) {
                LOGGER.warn("Failed to persist global event {}", event.eventId(), ex);
            }
        });
    }

    public void recordSync(GlobalEvent event) {
        if (!ready) {
            return;
        }
        try {
            eventDao.insert(event);
            anomalyEngine.inspect(event);
            blockSigner.onEventPersisted();
        } catch (Exception ex) {
            LOGGER.warn("Failed to persist global event {}", event.eventId(), ex);
        }
    }

    public String createItemTrace(String itemId, String originType, UUID actorUuid, String actorName, String detail) {
        String traceId = UUID.randomUUID().toString();
        ItemTrace trace = new ItemTrace(
                traceId,
                itemId,
                System.currentTimeMillis(),
                originType,
                actorUuid == null ? null : actorUuid.toString(),
                actorName,
                detail
        );
        try {
            itemTraceDao.insertTrace(trace);
        } catch (Exception ex) {
            LOGGER.warn("Failed to create item trace {}", traceId, ex);
        }
        return traceId;
    }

    public void linkItem(String traceId, String parentTraceId, String eventId, String action, UUID actorUuid, String actorName, String detail) {
        ItemTraceLink link = new ItemTraceLink(
                0L,
                traceId,
                parentTraceId,
                eventId,
                System.currentTimeMillis(),
                action,
                actorUuid == null ? null : actorUuid.toString(),
                actorName,
                detail
        );
        writer.execute(() -> {
            try {
                itemTraceDao.insertLink(link);
            } catch (Exception ex) {
                LOGGER.warn("Failed to link item trace {}", traceId, ex);
            }
        });
    }

    public void recordMovement(MovementSample sample) {
        if (!ready) {
            return;
        }
        writer.execute(() -> {
            try {
                movementDao.insert(sample);
            } catch (Exception ex) {
                LOGGER.warn("Failed to persist movement", ex);
            }
        });
    }

    public List<GlobalEvent> search(String query, String category, String actorUuid, String traceId, long fromTs, long toTs, int limit) {
        try {
            return eventDao.search(query, category, actorUuid, traceId, fromTs, toTs, limit);
        } catch (Exception ex) {
            LOGGER.warn("Search failed", ex);
            return List.of();
        }
    }

    public List<AlertRecord> alerts(boolean onlyUnacked, int limit) {
        try {
            return alertDao.list(onlyUnacked, limit);
        } catch (Exception ex) {
            LOGGER.warn("Alert list failed", ex);
            return List.of();
        }
    }

    public boolean acknowledgeAlert(String alertId) {
        try {
            return alertDao.acknowledge(alertId);
        } catch (Exception ex) {
            LOGGER.warn("Acknowledge failed", ex);
            return false;
        }
    }

    public Map<String, Object> incident(String eventId, long windowMs) {
        try {
            return incidentChainService.reconstruct(eventId, windowMs);
        } catch (Exception ex) {
            LOGGER.warn("Incident reconstruct failed", ex);
            return Map.of("found", false, "error", ex.getMessage());
        }
    }

    public Map<String, Object> itemChain(String traceId) {
        try {
            return incidentChainService.reconstructByTrace(traceId);
        } catch (Exception ex) {
            LOGGER.warn("Item chain failed", ex);
            return Map.of("found", false, "error", ex.getMessage());
        }
    }

    public List<MovementSample> movements(String playerUuid, long fromTs, long toTs, int limit) {
        try {
            return movementDao.forPlayer(playerUuid, fromTs, toTs, limit);
        } catch (Exception ex) {
            LOGGER.warn("Movement query failed", ex);
            return List.of();
        }
    }

    public DashboardStats stats() {
        long dayAgo = System.currentTimeMillis() - 86_400_000L;
        try {
            return new DashboardStats(
                    eventDao.countSince(dayAgo),
                    alertDao.countUnacked()
            );
        } catch (Exception ex) {
            return new DashboardStats(0L, 0L);
        }
    }

    @Override
    public void close() {
        ready = false;
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("Audit writer did not drain in 5s; forcing shutdown");
                writer.shutdownNow();
                if (!writer.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    LOGGER.warn("Audit writer still running after force shutdown");
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    public record DashboardStats(long eventsLast24h, long unackedAlerts) {
    }
}
