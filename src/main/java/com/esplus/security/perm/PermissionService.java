package com.esplus.security.perm;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.security.db.PermissionDao;
import com.esplus.security.db.UserDao;
import com.esplus.security.db.UserRecord;

/**
 * Fine-grained permission checks. Deny by default unless explicitly granted.
 */
public final class PermissionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private volatile PermissionDao permissionDao;
    private volatile UserDao userDao;

    public void bind(PermissionDao permissionDao, UserDao userDao) {
        this.permissionDao = permissionDao;
        this.userDao = userDao;
    }

    public void seedRoleDefaults(UUID uuid, String role) {
        if (permissionDao == null) {
            return;
        }
        try {
            permissionDao.replaceAll(uuid, SemPermissions.defaultsForRole(role));
            LOGGER.info("Seeded role='{}' permissions for {}", role, uuid);
        } catch (Exception ex) {
            LOGGER.warn("Failed to seed permissions for {}", uuid, ex);
        }
    }

    public void ensureSeeded(UUID uuid) {
        if (permissionDao == null || userDao == null) {
            return;
        }
        try {
            if (permissionDao.hasAny(uuid)) {
                return;
            }
            String role = userDao.findByUuid(uuid).map(UserRecord::role).orElse("op");
            seedRoleDefaults(uuid, role);
        } catch (Exception ex) {
            LOGGER.warn("Failed to ensure permissions for {}", uuid, ex);
        }
    }

    public boolean has(UUID uuid, String perm) {
        if (permissionDao == null || perm == null || perm.isBlank()) {
            return false;
        }
        try {
            ensureSeeded(uuid);
            return permissionDao.isAllowed(uuid, perm);
        } catch (Exception ex) {
            LOGGER.warn("Permission check failed for {} / {}", uuid, perm, ex);
            return false;
        }
    }

    public boolean canUseCommand(UUID uuid, String rootCommand) {
        return has(uuid, SemPermissions.commandPerm(rootCommand));
    }

    public boolean canSudoGive(UUID uuid) {
        return has(uuid, SemPermissions.SUDO_GIVE);
    }

    public Set<String> listAllowed(UUID uuid) {
        if (permissionDao == null) {
            return Set.of();
        }
        try {
            ensureSeeded(uuid);
            return permissionDao.findAllowed(uuid);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    public void clear(UUID uuid) {
        if (permissionDao == null) {
            return;
        }
        try {
            permissionDao.deleteAll(uuid);
        } catch (Exception ex) {
            LOGGER.warn("Failed to clear permissions for {}", uuid, ex);
        }
    }

    /**
     * Old builds seeded role=op with nearly all cmd.*. Strip those so only sudo.session remains.
     * admin / moderator rows are left untouched.
     */
    public void reconcilePlainOpRoles() {
        if (permissionDao == null || userDao == null) {
            return;
        }
        try {
            int n = 0;
            for (UUID uuid : userDao.findUuidsByRole("op")) {
                seedRoleDefaults(uuid, "op");
                n++;
            }
            if (n > 0) {
                LOGGER.info("Reconciled {} role=op user(s) to sudo.session-only permissions", n);
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to reconcile op-role permissions", ex);
        }
    }
}
