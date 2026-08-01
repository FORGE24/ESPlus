package com.esplus.panel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 15 * 60_000L;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        Attempt a = attempts.get(key(username));
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    public long lockedUntil(String username) {
        Attempt a = attempts.get(key(username));
        return a == null ? 0L : a.lockedUntil;
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    public void onFailure(String username) {
        String k = key(username);
        Attempt a = attempts.computeIfAbsent(k, ignored -> new Attempt());
        a.failures++;
        if (a.failures >= MAX_ATTEMPTS) {
            a.lockedUntil = System.currentTimeMillis() + LOCK_MS;
            a.failures = 0;
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static final class Attempt {
        int failures;
        long lockedUntil;
    }
}
