package com.esplus.security.risk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.Config;
import com.esplus.security.db.ProtectedCommandDao;

/**
 * Pre-gate SEM task risk check: classify root commands and decide whether sudo is required.
 */
public final class TaskRiskService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> EXEMPT = Set.of(
            "sudo", "esplus", "setoppw", "changepw"
    );

    private static final Set<String> CRITICAL_DEFAULTS = Set.of(
            "op", "deop", "ban", "ban-ip", "stop", "whitelist"
    );

    private final Map<String, RiskLevel> protectedCommands = new ConcurrentHashMap<>();
    private ProtectedCommandDao dao;

    public void bind(ProtectedCommandDao dao) {
        this.dao = dao;
    }

    public void reloadFromConfig() {
        Map<String, RiskLevel> next = new HashMap<>();
        for (String raw : Config.PROTECTED_COMMANDS.get()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String cmd = raw.trim().toLowerCase(Locale.ROOT);
            if (EXEMPT.contains(cmd)) {
                continue;
            }
            next.put(cmd, CRITICAL_DEFAULTS.contains(cmd) ? RiskLevel.CRITICAL : RiskLevel.HIGH);
        }
        protectedCommands.clear();
        protectedCommands.putAll(next);
        persist();
        LOGGER.info("SEM task risk table loaded: {} protected command(s)", protectedCommands.size());
    }

    public RiskDecision evaluate(String rootCommand) {
        if (rootCommand == null || rootCommand.isBlank()) {
            return RiskDecision.none("");
        }
        String cmd = rootCommand.toLowerCase(Locale.ROOT);
        if (EXEMPT.contains(cmd)) {
            return RiskDecision.none(cmd);
        }
        RiskLevel level = protectedCommands.get(cmd);
        if (level == null) {
            return RiskDecision.none(cmd);
        }
        return RiskDecision.of(cmd, level, "protected:" + level.name());
    }

    public boolean isProtected(String rootCommand) {
        return evaluate(rootCommand).requiresSudo();
    }

    public Set<String> snapshotCommands() {
        return new HashSet<>(protectedCommands.keySet());
    }

    private void persist() {
        if (dao == null) {
            return;
        }
        try {
            Map<String, String> rows = new HashMap<>();
            for (Map.Entry<String, RiskLevel> entry : protectedCommands.entrySet()) {
                rows.put(entry.getKey(), entry.getValue().name());
            }
            dao.replaceAll(rows);
        } catch (Exception ex) {
            LOGGER.warn("Failed to sync protected_commands table", ex);
        }
    }
}
