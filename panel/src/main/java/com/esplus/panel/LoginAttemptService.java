package com.esplus.panel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Tracks failed login attempts by username and by IP address.
 * Locking is independent: a username may be locked, an IP may be locked, or both.
 */
@Component
public class LoginAttemptService {
    // Username-based locking
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 15 * 60_000L;

    // IP-based locking
    private static final int MAX_IP_ATTEMPTS = 10;
    private static final long IP_LOCK_MS = 15 * 60_000L;

    // MFA verification locking
    private static final int MAX_MFA_IP_ATTEMPTS = 5;
    private static final long MFA_IP_LOCK_MS = 5 * 60_000L;
    private static final int MAX_MFA_SESSION_ATTEMPTS = 10;
    private static final long MFA_SESSION_LOCK_MS = 15 * 60_000L;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, Attempt> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Attempt> mfaIpAttempts = new ConcurrentHashMap<>();
    private final Map<String, Attempt> mfaSessionAttempts = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        String ip = currentIp();
        return isLocked(username, ip);
    }

    public boolean isLocked(String username, String ip) {
        return isUserLocked(username) || isIpLocked(ip);
    }

    public boolean isUserLocked(String username) {
        Attempt a = attempts.get(key(username));
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    public boolean isIpLocked(String ip) {
        Attempt a = ipAttempts.get(ipKey(ip));
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
        onFailure(username, currentIp());
    }

    public void onFailure(String username, String ip) {
        String k = key(username);
        Attempt a = attempts.computeIfAbsent(k, ignored -> new Attempt());
        a.failures++;
        if (a.failures >= MAX_ATTEMPTS) {
            a.lockedUntil = System.currentTimeMillis() + LOCK_MS;
            a.failures = 0;
        }

        // IP-based lockout
        if (ip != null && !ip.isBlank()) {
            Attempt ipA = ipAttempts.computeIfAbsent(ipKey(ip), ignored -> new Attempt());
            ipA.failures++;
            if (ipA.failures >= MAX_IP_ATTEMPTS) {
                ipA.lockedUntil = System.currentTimeMillis() + IP_LOCK_MS;
                ipA.failures = 0;
            }
        }
    }

    // ── MFA verification throttling ─────────────────────────────────

    public boolean isMfaLocked(String ip, String sessionId) {
        return isMfaIpLocked(ip) || isMfaSessionLocked(sessionId);
    }

    private boolean isMfaIpLocked(String ip) {
        if (ip == null || ip.isBlank()) return false;
        Attempt a = mfaIpAttempts.get(ipKey(ip));
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    private boolean isMfaSessionLocked(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        Attempt a = mfaSessionAttempts.get("mfa_sess:" + sessionId);
        return a != null && a.lockedUntil > System.currentTimeMillis();
    }

    public void onMfaFailure(String ip, String sessionId) {
        if (ip != null && !ip.isBlank()) {
            Attempt a = mfaIpAttempts.computeIfAbsent(ipKey(ip), ignored -> new Attempt());
            a.failures++;
            if (a.failures >= MAX_MFA_IP_ATTEMPTS) {
                a.lockedUntil = System.currentTimeMillis() + MFA_IP_LOCK_MS;
                a.failures = 0;
            }
        }
        if (sessionId != null && !sessionId.isBlank()) {
            Attempt a = mfaSessionAttempts.computeIfAbsent("mfa_sess:" + sessionId, ignored -> new Attempt());
            a.failures++;
            if (a.failures >= MAX_MFA_SESSION_ATTEMPTS) {
                a.lockedUntil = System.currentTimeMillis() + MFA_SESSION_LOCK_MS;
                a.failures = 0;
            }
        }
    }

    public void onMfaSuccess(String ip, String sessionId) {
        if (ip != null && !ip.isBlank()) mfaIpAttempts.remove(ipKey(ip));
        if (sessionId != null && !sessionId.isBlank()) mfaSessionAttempts.remove("mfa_sess:" + sessionId);
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attrs.getRequest().getRemoteAddr();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String ipKey(String ip) {
        return ip == null ? "" : ip.trim();
    }

    private static final class Attempt {
        int failures;
        long lockedUntil;
    }
}
