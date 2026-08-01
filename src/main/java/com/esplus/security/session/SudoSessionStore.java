package com.esplus.security.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SudoSessionStore {
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    public void open(UUID uuid, long ttlMillis) {
        sessions.put(uuid, System.currentTimeMillis() + ttlMillis);
    }

    public boolean isActive(UUID uuid) {
        Long expiresAt = sessions.get(uuid);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            sessions.remove(uuid);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID uuid) {
        Long expiresAt = sessions.get(uuid);
        if (expiresAt == null) {
            return 0L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            sessions.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    public void close(UUID uuid) {
        sessions.remove(uuid);
    }

    /** Sliding renewal: extend active session by ttlMillis from now. */
    public boolean touch(UUID uuid, long ttlMillis) {
        if (!isActive(uuid)) {
            return false;
        }
        sessions.put(uuid, System.currentTimeMillis() + ttlMillis);
        return true;
    }

    public void clear() {
        sessions.clear();
    }
}
