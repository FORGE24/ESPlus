package com.esplus.audit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule engine for anomaly detection based on recent event rates and patterns.
 */
public final class AnomalyEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnomalyEngine.class);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)amount[=:]\\s*(-?\\d+)");
    private static final Pattern BIG_NUM = Pattern.compile("(\\d{5,})");

    private final AlertDao alertDao;
    private final AlertWebhookDispatcher webhook;
    private final AutoResponseEngine autoResponse;
    private final Map<String, Deque<Long>> commandBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> giveBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> breakBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> chatBuckets = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> redstoneBuckets = new ConcurrentHashMap<>();
    private final Map<String, Long> alertCooldownUntil = new ConcurrentHashMap<>();

    private final int commandBurstThreshold;
    private final int giveBurstThreshold;
    private final int breakBurstThreshold;
    private final int chatBurstThreshold;
    private final int redstoneBurstThreshold;
    private final long windowMs;
    private static final long ALERT_COOLDOWN_MS = 60_000L;

    public AnomalyEngine(
            AlertDao alertDao,
            AlertWebhookDispatcher webhook,
            AutoResponseEngine autoResponse,
            int commandBurstThreshold,
            int giveBurstThreshold,
            int breakBurstThreshold,
            int chatBurstThreshold,
            int redstoneBurstThreshold,
            long windowMs
    ) {
        this.alertDao = alertDao;
        this.webhook = webhook;
        this.autoResponse = autoResponse;
        this.commandBurstThreshold = commandBurstThreshold;
        this.giveBurstThreshold = giveBurstThreshold;
        this.breakBurstThreshold = breakBurstThreshold;
        this.chatBurstThreshold = chatBurstThreshold;
        this.redstoneBurstThreshold = redstoneBurstThreshold;
        this.windowMs = windowMs;
    }

    public void inspect(GlobalEvent event) {
        try {
            if ("command".equals(event.category()) && "protected_blocked".equals(event.action())) {
                raise("HIGH", "PROTECTED_CMD_BLOCKED", "受保护指令被拦截",
                        event.actorName() + " 尝试执行受保护指令: " + event.detail(),
                        event);
            }
            if ("command".equals(event.category())) {
                if (burst(commandBuckets, key(event), event.ts(), commandBurstThreshold)) {
                    raise("MEDIUM", "CMD_BURST", "指令爆发",
                            event.actorName() + " 在短时间内大量执行指令", event);
                }
            }
            if ("item".equals(event.category()) && ("give".equals(event.action()) || "sudo_give".equals(event.action()))) {
                if (burst(giveBuckets, key(event), event.ts(), giveBurstThreshold)) {
                    raise("HIGH", "GIVE_BURST", "物品发放异常",
                            event.actorName() + " 短时间大量发放物品: " + event.itemId(), event);
                }
            }
            if ("block".equals(event.category()) && "break".equals(event.action())) {
                if (burst(breakBuckets, key(event), event.ts(), breakBurstThreshold)) {
                    raise("MEDIUM", "BREAK_BURST", "破坏速度异常",
                            event.actorName() + " 短时间大量破坏方块", event);
                }
            }
            if ("security".equals(event.category()) && "sudo_auth_fail".equals(event.action())) {
                raise("HIGH", "SUDO_FAIL", "sudo 鉴权失败",
                        event.actorName() + " sudo 密码验证失败", event);
            }
            if ("economy".equals(event.category())) {
                long amount = extractAmount(event.detail());
                if (amount >= 100_000L) {
                    raise("HIGH", "ECONOMY_SPIKE", "经济异动",
                            event.actorName() + " 金额约 " + amount + " — " + event.detail(), event);
                }
            }
            if ("chat".equals(event.category()) && "chat".equals(event.action())) {
                if (burst(chatBuckets, key(event), event.ts(), chatBurstThreshold)) {
                    raise("MEDIUM", "CHAT_SPAM", "聊天刷屏",
                            event.actorName() + " 短时间频繁聊天", event);
                }
            }
            if ("redstone".equals(event.category())) {
                if (burst(redstoneBuckets, key(event), event.ts(), redstoneBurstThreshold)) {
                    raise("MEDIUM", "REDSTONE_BURST", "高频红石脉冲",
                            event.actorName() + " 高频红石脉冲（疑似卡服）", event);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Anomaly inspection failed", ex);
        }
    }

    private static long extractAmount(String detail) {
        if (detail == null || detail.isBlank()) {
            return 0L;
        }
        Matcher m = AMOUNT_PATTERN.matcher(detail);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        m = BIG_NUM.matcher(detail);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private boolean burst(Map<String, Deque<Long>> buckets, String key, long ts, int threshold) {
        Deque<Long> deque = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(ts);
            while (!deque.isEmpty() && ts - deque.peekFirst() > windowMs) {
                deque.removeFirst();
            }
            return deque.size() >= threshold;
        }
    }

    private void raise(String severity, String rule, String title, String message, GlobalEvent event) throws Exception {
        String actor = event.actorUuid() == null ? "console" : event.actorUuid();
        String cooldownKey = rule + "|" + actor;
        long now = System.currentTimeMillis();
        Long until = alertCooldownUntil.get(cooldownKey);
        if (until != null && until > now) {
            return;
        }
        alertCooldownUntil.put(cooldownKey, now + ALERT_COOLDOWN_MS);
        AlertRecord alert = new AlertRecord(
                UUID.randomUUID().toString(),
                now,
                severity,
                rule,
                title,
                message,
                event.actorUuid(),
                event.actorName(),
                event.eventId(),
                event.traceId(),
                false
        );
        alertDao.insert(alert);
        if (webhook != null) {
            webhook.dispatch(alert);
        }
        if (autoResponse != null) {
            autoResponse.onAlert(alert);
        }
    }

    private static String key(GlobalEvent event) {
        return event.actorUuid() == null ? "console" : event.actorUuid();
    }
}
