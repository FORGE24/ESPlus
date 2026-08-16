package com.esplus.security;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.audit.AuditService;
import com.esplus.audit.GlobalEvent;
import com.esplus.Config;
import com.esplus.security.connect.ConnectionFingerprintManager;
import com.esplus.security.connect.GeoIpResolver;
import com.esplus.security.connect.HwidDao;
import com.esplus.security.connect.UdpProbeListener;
import com.esplus.security.crypto.AesCipherService;
import com.esplus.security.crypto.BcryptPasswordService;
import com.esplus.security.crypto.RsaKeyManager;
import com.esplus.log.ServerLogCapture;
import com.esplus.security.db.AuditDao;
import com.esplus.security.db.MetaDao;
import com.esplus.security.db.MfaDao;
import com.esplus.security.db.PanelActionDao;
import com.esplus.security.db.PermissionDao;
import com.esplus.security.db.ProtectedCommandDao;
import com.esplus.security.db.ServerLogDao;
import com.esplus.security.db.SqliteDatabase;
import com.esplus.security.db.UserDao;
import com.esplus.security.db.UserRecord;
import com.esplus.security.perm.PermissionService;
import com.esplus.security.perm.SemPermissions;
import com.esplus.security.risk.RiskDecision;
import com.esplus.security.risk.TaskRiskService;
import com.esplus.security.session.SudoSessionStore;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class SecurityService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCHEMA_VERSION = 3;

    public enum AuthResult {
        SUCCESS,
        BAD_PASSWORD,
        BAD_TOTP,
        NEED_TOTP,
        LOCKED,
        NO_PASSWORD,
        DENIED_PERM,
        NOT_OP,
        NOT_READY,
        ERROR
    }

    public enum PasswordResult {
        SET,
        CHANGED,
        ALREADY_SET,
        NOT_SET,
        WRONG_OLD,
        LOCKED,
        TOO_SHORT,
        NOT_OP,
        NOT_READY,
        ERROR
    }

    private final SudoSessionStore sessions = new SudoSessionStore();
    private final BcryptPasswordService bcrypt = new BcryptPasswordService();
    private final TaskRiskService taskRisk = new TaskRiskService();
    private final PermissionService permissions = new PermissionService();
    private final ServerLogCapture logCapture = new ServerLogCapture();

    private SqliteDatabase database;
    private MetaDao metaDao;
    private UserDao userDao;
    private AuditDao auditDao;
    private ProtectedCommandDao protectedCommandDao;
    private PermissionDao permissionDao;
    private PanelActionDao panelActionDao;
    private ServerLogDao serverLogDao;
    private AuditService auditService;
    private AesCipherService aes;
    private RsaKeyManager rsaKeys;
    private MfaDao mfaDao;
    private Path databasePath;
    private volatile boolean ready;
    private volatile String failureReason;
    private ConnectionFingerprintManager fingerprintManager;

    public void start(MinecraftServer server) {
        failureReason = null;
        try {
            Path serverRoot = server.getServerDirectory();
            databasePath = resolvePath(serverRoot, Config.DATABASE_PATH.get());
            Path keysDir = server.getServerDirectory().resolve("config").resolve(Config.KEYS_DIRECTORY.get());

            RsaKeyManager rsa = new RsaKeyManager(keysDir);
            rsa.initialize();
            this.rsaKeys = rsa;

            database = new SqliteDatabase(databasePath);
            database.open();
            metaDao = new MetaDao(database);
            userDao = new UserDao(database);
            mfaDao = new MfaDao(database);
            auditDao = new AuditDao(database);
            protectedCommandDao = new ProtectedCommandDao(database);
            permissionDao = new PermissionDao(database);
            panelActionDao = new PanelActionDao(database);
            serverLogDao = new ServerLogDao(database);
            taskRisk.bind(protectedCommandDao);
            taskRisk.reloadFromConfig();
            permissions.bind(permissionDao, userDao);
            logCapture.bind(serverLogDao);
            logCapture.attach();

            Optional<byte[]> wrapped = metaDao.findWrappedAesKey();
            SecretKey aesKey;
            if (wrapped.isEmpty()) {
                aesKey = AesCipherService.generateKey();
                byte[] wrappedKey = rsa.wrapAesKey(aesKey);
                metaDao.insertBootstrap(wrappedKey, SCHEMA_VERSION, System.currentTimeMillis());
                LOGGER.info("ESPlus security bootstrap complete (new AES master key wrapped with RSA)");
            } else {
                aesKey = rsa.unwrapAesKey(wrapped.get());
                LOGGER.info("ESPlus security store loaded from {}", databasePath.toAbsolutePath());
            }

            aes = new AesCipherService(aesKey);
            if (Config.AUDIT_ENABLED.getAsBoolean()) {
                auditService = new AuditService(
                        database,
                        rsa,
                        this,
                        Config.ANOMALY_COMMAND_BURST.getAsInt(),
                        Config.ANOMALY_GIVE_BURST.getAsInt(),
                        Config.ANOMALY_BREAK_BURST.getAsInt(),
                        Config.ANOMALY_CHAT_BURST.getAsInt(),
                        Config.ANOMALY_REDSTONE_BURST.getAsInt(),
                        Config.ANOMALY_WINDOW_SECONDS.getAsInt() * 1000L
                );
            }

            GeoIpResolver geoResolver = new GeoIpResolver();
            HwidDao hwidDao = new HwidDao(database);
            UdpProbeListener udpProbe = Config.UDP_PROBE_ENABLED.getAsBoolean()
                    ? new UdpProbeListener()
                    : null;
            fingerprintManager = new ConnectionFingerprintManager(database, geoResolver, hwidDao, udpProbe);

            ready = true;
            failureReason = null;
            permissions.reconcilePlainOpRoles();
        } catch (Exception ex) {
            ready = false;
            failureReason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            LOGGER.error("Failed to start ESPlus security service", ex);
            notifyOps(server, "[ES+] 安全服务启动失败，sudo/审计/面板队列已禁用: " + failureReason);
            stop();
        }
    }

    public void stop() {
        sessions.clear();
        ready = false;
        if (fingerprintManager != null && fingerprintManager.udpProbe() != null) {
            try {
                fingerprintManager.udpProbe().close();
            } catch (Exception ignored) {
            }
        }
        fingerprintManager = null;
        logCapture.detach();
        if (auditService != null) {
            auditService.close();
            auditService = null;
        }
        if (database != null) {
            try {
                database.close();
            } catch (Exception ex) {
                LOGGER.warn("Error closing ESPlus database", ex);
            }
            database = null;
        }
        aes = null;
        metaDao = null;
        userDao = null;
        auditDao = null;
        protectedCommandDao = null;
        permissionDao = null;
        panelActionDao = null;
        serverLogDao = null;
        databasePath = null;
    }

    public String failureReason() {
        return failureReason;
    }

    private static void notifyOps(MinecraftServer server, String message) {
        if (server == null) {
            return;
        }
        Component text = Component.literal(message);
        server.getPlayerList().broadcastSystemMessage(text, false);
        LOGGER.error(message);
    }

    public PanelActionDao panelActionDao() {
        return panelActionDao;
    }

    public ServerLogCapture logCapture() {
        return logCapture;
    }

    public boolean isReady() {
        return ready;
    }

    public AuditService auditService() {
        return auditService;
    }

    public ConnectionFingerprintManager getFingerprintManager() {
        return fingerprintManager;
    }

    public Path databasePath() {
        return databasePath;
    }

    public SqliteDatabase database() {
        return database;
    }

    public SudoSessionStore sessions() {
        return sessions;
    }

    public PermissionService permissions() {
        return permissions;
    }

    public boolean hasPermission(UUID uuid, String perm) {
        return ready && permissions.has(uuid, perm);
    }

    public boolean canUseProtectedCommand(UUID uuid, String rootCommand) {
        return ready && permissions.canUseCommand(uuid, rootCommand);
    }

    public boolean canSudoGive(UUID uuid) {
        return ready && permissions.canSudoGive(uuid);
    }

    /** SEM role == admin (not merely Minecraft OP / role=op). */
    public boolean isSemAdmin(UUID uuid) {
        if (!ready || userDao == null) {
            return false;
        }
        try {
            return userDao.findByUuid(uuid)
                    .map(u -> "admin".equalsIgnoreCase(u.role()))
                    .orElse(false);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Optional one-shot elevate: admin actor → admin target (no lasting session).
     * Admin → player never auto-elevates — manual /sudo required.
     */
    public boolean canAutoElevateAdminToAdmin(UUID actorId, UUID targetId) {
        if (!ready || !Config.AUTO_SUDO_ADMIN_TO_ADMIN.getAsBoolean()) {
            return false;
        }
        if (actorId == null || targetId == null || actorId.equals(targetId)) {
            return false;
        }
        return isSemAdmin(actorId) && isSemAdmin(targetId);
    }

    public boolean hasPassword(UUID uuid) {
        if (!ready) {
            return false;
        }
        try {
            return userDao.findByUuid(uuid).isPresent();
        } catch (Exception ex) {
            LOGGER.error("Failed to query password for {}", uuid, ex);
            return false;
        }
    }

    public PasswordResult setPassword(ServerPlayer player, String password) {
        if (!ready) {
            return PasswordResult.NOT_READY;
        }
        if (!isMinecraftOperator(player)) {
            return PasswordResult.NOT_OP;
        }
        if (password.length() < Config.MIN_PASSWORD_LENGTH.getAsInt()) {
            return PasswordResult.TOO_SHORT;
        }
        try {
            Optional<UserRecord> existing = userDao.findByUuid(player.getUUID());
            if (existing.isPresent()) {
                return PasswordResult.ALREADY_SET;
            }
            String cipher = aes.encryptToBase64(bcrypt.hash(password));
            long now = System.currentTimeMillis();
            userDao.insert(new UserRecord(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    cipher,
                    true,
                    "op",
                    now,
                    now,
                    0,
                    0L
            ));
            // Plain OP: sudo.session only. Promote to admin/moderator in panel for cmd.*
            permissions.seedRoleDefaults(player.getUUID(), "op");
            audit(player.getUUID(), "password_set", player.getGameProfile().getName(), true);
            return PasswordResult.SET;
        } catch (Exception ex) {
            LOGGER.error("Failed to set password", ex);
            return PasswordResult.ERROR;
        }
    }

    public PasswordResult changePassword(ServerPlayer player, String oldPassword, String newPassword) {
        if (!ready) {
            return PasswordResult.NOT_READY;
        }
        if (!isMinecraftOperator(player)) {
            return PasswordResult.NOT_OP;
        }
        if (newPassword.length() < Config.MIN_PASSWORD_LENGTH.getAsInt()) {
            return PasswordResult.TOO_SHORT;
        }
        try {
            Optional<UserRecord> existing = userDao.findByUuid(player.getUUID());
            if (existing.isEmpty()) {
                return PasswordResult.NOT_SET;
            }
            UserRecord user = existing.get();
            AuthResult verify = verifyInternal(user, oldPassword);
            if (verify == AuthResult.LOCKED) {
                return PasswordResult.LOCKED;
            }
            if (verify != AuthResult.SUCCESS) {
                return PasswordResult.WRONG_OLD;
            }
            String cipher = aes.encryptToBase64(bcrypt.hash(newPassword));
            userDao.updatePassword(player.getUUID(), player.getGameProfile().getName(), cipher, System.currentTimeMillis());
            sessions.close(player.getUUID());
            audit(player.getUUID(), "password_change", player.getGameProfile().getName(), true);
            return PasswordResult.CHANGED;
        } catch (Exception ex) {
            LOGGER.error("Failed to change password", ex);
            return PasswordResult.ERROR;
        }
    }

    public AuthResult authenticate(ServerPlayer player, String password) {
        if (!ready) {
            return AuthResult.NOT_READY;
        }
        if (!isMinecraftOperator(player)) {
            audit(player.getUUID(), "sudo_auth", "not_op", false);
            return AuthResult.NOT_OP;
        }
        try {
            Optional<UserRecord> existing = userDao.findByUuid(player.getUUID());
            if (existing.isEmpty()) {
                audit(player.getUUID(), "sudo_auth", "no_password", false);
                return AuthResult.NO_PASSWORD;
            }
            AuthResult result = verifyInternal(existing.get(), password);
            if (result != AuthResult.SUCCESS) {
                if (result == AuthResult.BAD_PASSWORD) {
                    audit(player.getUUID(), "sudo_auth", "bad_password", false);
                    recordSecurityEvent(player, "sudo_auth_fail", "bad password");
                } else if (result == AuthResult.LOCKED) {
                    audit(player.getUUID(), "sudo_auth", "locked", false);
                    recordSecurityEvent(player, "sudo_auth_fail", "locked");
                }
                return result;
            }
            if (!permissions.has(player.getUUID(), SemPermissions.SUDO_SESSION)) {
                audit(player.getUUID(), "sudo_auth", "no_perm_sudo.session", false);
                recordSecurityEvent(player, "sudo_auth_fail", "missing sudo.session");
                return AuthResult.DENIED_PERM;
            }
            if (Config.SUDO_TOTP_REQUIRED.getAsBoolean() && isUserTotpEnabled(player.getUUID())) {
                audit(player.getUUID(), "sudo_auth", "need_totp", true);
                return AuthResult.NEED_TOTP;
            }
            openSudoSession(player);
            return AuthResult.SUCCESS;
        } catch (Exception ex) {
            LOGGER.error("Failed to authenticate sudo", ex);
            return AuthResult.ERROR;
        }
    }

    public AuthResult completeTotp(ServerPlayer player, String totpCode) {
        if (!ready) {
            return AuthResult.NOT_READY;
        }
        if (!isMinecraftOperator(player)) {
            return AuthResult.NOT_OP;
        }
        try {
            Optional<MfaDao.MfaRecord> mfa = mfaDao.findUser(player.getUUID());
            if (mfa.isEmpty() || !mfa.get().enabled()) {
                openSudoSession(player);
                return AuthResult.SUCCESS;
            }
            if (!com.esplus.security.crypto.TotpService.verify(mfa.get().secret(), totpCode)) {
                audit(player.getUUID(), "sudo_auth", "bad_totp", false);
                recordSecurityEvent(player, "sudo_auth_fail", "bad totp");
                return AuthResult.BAD_TOTP;
            }
            openSudoSession(player);
            return AuthResult.SUCCESS;
        } catch (Exception ex) {
            LOGGER.error("Failed TOTP auth", ex);
            return AuthResult.ERROR;
        }
    }

    private void openSudoSession(ServerPlayer player) {
        long minutes = Config.SUDO_SESSION_MINUTES.getAsInt();
        if (isLockdownActive()) {
            minutes = Math.min(minutes, Config.LOCKDOWN_SUDO_MINUTES.getAsInt());
        }
        sessions.open(player.getUUID(), minutes * 60_000L);
        audit(player.getUUID(), "sudo_auth", "ok", true);
        recordSecurityEvent(player, "sudo_auth_ok", "sudo session opened");
    }

    public boolean isUserTotpEnabled(UUID uuid) {
        try {
            return mfaDao != null && mfaDao.findUser(uuid).map(MfaDao.MfaRecord::enabled).orElse(false);
        } catch (Exception ex) {
            return false;
        }
    }

    public String enrollUserTotp(UUID uuid) throws Exception {
        String secret = com.esplus.security.crypto.TotpService.generateSecret();
        mfaDao.upsertUser(uuid, secret, false);
        return secret;
    }

    public void enableUserTotp(UUID uuid, boolean enabled) throws Exception {
        mfaDao.setUserEnabled(uuid, enabled);
    }

    public boolean isLockdownActive() {
        if (database == null) {
            return false;
        }
        try (var ps = database.connection().prepareStatement("SELECT lockdown FROM server_runtime WHERE id = 1")) {
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public RsaKeyManager rsaKeys() {
        return rsaKeys;
    }

    public MfaDao mfaDao() {
        return mfaDao;
    }

    /** Minecraft OP level ≥ 2 (or console). Non-OP must never hold a sudo session. */
    public static boolean isMinecraftOperator(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        return player.hasPermissions(2)
                || player.server.getPlayerList().isOp(player.getGameProfile());
    }

    public boolean resetPassword(UUID uuid) {
        if (!ready) {
            return false;
        }
        try {
            boolean deleted = userDao.delete(uuid);
            permissions.clear(uuid);
            sessions.close(uuid);
            audit(uuid, "password_reset", "console", deleted);
            return deleted;
        } catch (Exception ex) {
            LOGGER.error("Failed to reset password for {}", uuid, ex);
            return false;
        }
    }

    public void audit(UUID uuid, String action, String detail, boolean success) {
        if (!ready || auditDao == null) {
            return;
        }
        try {
            auditDao.log(uuid, action, detail, success);
        } catch (Exception ex) {
            LOGGER.warn("Failed to write audit log", ex);
        }
    }

    public RiskDecision evaluateRisk(String rootCommand) {
        return taskRisk.evaluate(rootCommand);
    }

    public boolean isProtectedCommand(String rootCommand) {
        return taskRisk.isProtected(rootCommand);
    }

    public void refreshSudoSession(UUID uuid) {
        long ttl = Config.SUDO_SESSION_MINUTES.getAsInt() * 60_000L;
        sessions.touch(uuid, ttl);
    }

    public long lockedUntil(UUID uuid) {
        if (!ready) {
            return 0L;
        }
        try {
            return userDao.findByUuid(uuid).map(UserRecord::lockedUntil).orElse(0L);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private AuthResult verifyInternal(UserRecord user, String password) throws Exception {
        long now = System.currentTimeMillis();
        if (user.lockedUntil() > now) {
            return AuthResult.LOCKED;
        }

        String bcryptHash = aes.decryptFromBase64(user.passwordCipher());
        boolean ok = bcrypt.matches(password, bcryptHash);
        if (ok) {
            if (user.failedAttempts() != 0 || user.lockedUntil() != 0L) {
                userDao.updateLockState(user.uuid(), 0, 0L);
            }
            return AuthResult.SUCCESS;
        }

        int attempts = user.failedAttempts() + 1;
        long lockedUntil = 0L;
        if (attempts >= Config.MAX_FAILED_ATTEMPTS.getAsInt()) {
            lockedUntil = now + Config.LOCK_MINUTES.getAsInt() * 60_000L;
            attempts = 0;
        }
        userDao.updateLockState(user.uuid(), attempts, lockedUntil);
        return lockedUntil > 0L ? AuthResult.LOCKED : AuthResult.BAD_PASSWORD;
    }

    private static Path resolvePath(Path serverRoot, String configured) {
        Path path = Path.of(configured);
        return path.isAbsolute() ? path : serverRoot.resolve(path);
    }

    public void recordSecurityEvent(ServerPlayer player, String action, String detail) {
        if (auditService == null) {
            return;
        }
        auditService.recordAsync(new GlobalEvent(
                java.util.UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                "security",
                action,
                player.getUUID().toString(),
                player.getGameProfile().getName(),
                null,
                null,
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                null,
                null,
                detail,
                "player"
        ));
    }

    public static String formatInstant(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString();
    }
}
