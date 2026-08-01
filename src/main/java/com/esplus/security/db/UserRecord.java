package com.esplus.security.db;

import java.util.UUID;

public record UserRecord(
        UUID uuid,
        String name,
        String passwordCipher,
        boolean opBound,
        String role,
        long createdAt,
        long updatedAt,
        int failedAttempts,
        long lockedUntil
) {
}
