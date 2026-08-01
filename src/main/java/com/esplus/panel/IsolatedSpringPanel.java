package com.esplus.panel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.esplus.Config;

/**
 * Embeds the Spring Boot panel fat-jar inside the mod, then starts it in a
 * separate JVM so NeoForge and Spring ClassLoaders stay fully independent.
 * Credentials are passed via process environment (not argv, not disk).
 */
public final class IsolatedSpringPanel {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "META-INF/esplus/esplus-panel.jar";
    private static final String DEFAULT_ADMIN_PASSWORD = "esplus";

    private Process process;

    public synchronized void start(Path databasePath, Path serverRoot) {
        if (!Config.PANEL_ENABLED.getAsBoolean()) {
            LOGGER.info("ESPlus Spring panel disabled by config");
            return;
        }
        if (process != null && process.isAlive()) {
            return;
        }

        String adminPassword = Config.PANEL_PASSWORD.get();
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword) && !Config.PANEL_ALLOW_DEFAULT_PASSWORD.getAsBoolean()) {
            LOGGER.error(
                    "Refusing to start panel: panelPassword is still the default '{}'. "
                            + "Change it in config, or set panelAllowDefaultPassword=true for local debug only.",
                    DEFAULT_ADMIN_PASSWORD);
            return;
        }

        try {
            Path panelDir = serverRoot.resolve("esplus").resolve("panel");
            Files.createDirectories(panelDir);
            Path panelJar = panelDir.resolve("esplus-panel.jar");
            extractEmbeddedJar(panelJar);

            Path logDir = serverRoot.resolve("logs");
            Files.createDirectories(logDir);
            Path springLog = logDir.resolve("spring-panel.log");

            Path runtimeProps = panelDir.resolve("application-runtime.properties");
            writeRuntimeProperties(runtimeProps, databasePath);

            String javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("-Xmx512M");
            command.add("-Dfile.encoding=UTF-8");
            command.add("-Dstdout.encoding=UTF-8");
            command.add("-Dstderr.encoding=UTF-8");
            command.add("-jar");
            command.add(panelJar.toAbsolutePath().toString());
            String bindAddress = Config.PANEL_BIND_ADDRESS.get();
            if (bindAddress == null || bindAddress.isBlank()) {
                bindAddress = "127.0.0.1";
            }
            int panelPort = Config.PANEL_PORT.getAsInt();
            // Non-secret config in properties; passwords only via env (Spring relaxed binding).
            command.add("--spring.config.additional-location=optional:file:./application-runtime.properties");
            command.add("--server.address=" + bindAddress);
            command.add("--server.port=" + panelPort);
            command.add("--spring.main.banner-mode=console");
            command.add("--spring.output.ansi.enabled=ALWAYS");
            command.add("--logging.charset.console=UTF-8");
            command.add("--logging.charset.file=UTF-8");
            command.add("--logging.file.name=" + springLog.toAbsolutePath());
            command.add("--logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss.SSS} %5p ${PID:- } --- [%15.15t] %-40.40logger{39} : %m%n");
            command.add("--logging.pattern.file=%d{yyyy-MM-dd HH:mm:ss.SSS} %5p ${PID:- } --- [%t] %-40.40logger{39} : %m%n");
            command.add("--logging.level.root=INFO");
            command.add("--logging.level.org.springframework=INFO");
            command.add("--logging.level.org.springframework.boot=INFO");
            command.add("--logging.level.org.springframework.web=INFO");
            command.add("--logging.level.org.springframework.security=INFO");
            command.add("--logging.level.org.apache.catalina=INFO");
            command.add("--logging.level.org.apache.tomcat=INFO");
            command.add("--logging.level.com.esplus=INFO");

            if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
                LOGGER.warn("Panel admin password is still the default — allowed only because panelAllowDefaultPassword=true");
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(panelDir.toFile());
            putCredentialEnv(builder.environment());
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            process = builder.start();

            LOGGER.info("ESPlus Spring panel started (isolated JVM) at {}:{} using {}",
                    bindAddress, panelPort, panelJar.toAbsolutePath());
            if ("127.0.0.1".equals(bindAddress) || "localhost".equalsIgnoreCase(bindAddress)) {
                LOGGER.info("Panel is localhost-only; expose via public reverse tunnel (see deploy/public-ingress/)");
            }
            LOGGER.info("Spring logs: live console (full) + file {}", springLog.toAbsolutePath());
        } catch (Exception ex) {
            LOGGER.error("Failed to start isolated Spring panel", ex);
            stop();
        }
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(8, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
        LOGGER.info("ESPlus Spring panel stopped");
    }

    /** Passwords never touch disk or argv — Spring maps ESPLUS_* env to esplus.* */
    private static void putCredentialEnv(Map<String, String> env) {
        env.put("ESPLUS_USERNAME", nullToEmpty(Config.PANEL_USERNAME.get()));
        env.put("ESPLUS_PASSWORD", nullToEmpty(Config.PANEL_PASSWORD.get()));
        env.put("ESPLUS_MODUSERNAME", nullToEmpty(Config.PANEL_MOD_USERNAME.get()));
        env.put("ESPLUS_MODPASSWORD", nullToEmpty(Config.PANEL_MOD_PASSWORD.get()));
        env.put("ESPLUS_VIEWERUSERNAME", nullToEmpty(Config.PANEL_VIEWER_USERNAME.get()));
        env.put("ESPLUS_VIEWERPASSWORD", nullToEmpty(Config.PANEL_VIEWER_PASSWORD.get()));
    }

    private static void writeRuntimeProperties(Path file, Path databasePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("esplus.db=").append(escapeProp(databasePath.toAbsolutePath().toString())).append('\n');
        sb.append("esplus.username=").append(escapeProp(nullToEmpty(Config.PANEL_USERNAME.get()))).append('\n');
        // Intentionally omit passwords — supplied via env only.
        sb.append("esplus.modUsername=").append(escapeProp(nullToEmpty(Config.PANEL_MOD_USERNAME.get()))).append('\n');
        sb.append("esplus.viewerUsername=").append(escapeProp(nullToEmpty(Config.PANEL_VIEWER_USERNAME.get()))).append('\n');
        sb.append("esplus.sshHint=").append(escapeProp(nullToEmpty(Config.PANEL_SSH_HINT.get()))).append('\n');
        sb.append("esplus.auditRetentionDays=").append(Config.AUDIT_RETENTION_DAYS.getAsInt()).append('\n');
        sb.append("esplus.sudoSessionMinutes=").append(Config.SUDO_SESSION_MINUTES.getAsInt()).append('\n');
        sb.append("esplus.maxFailedAttempts=").append(Config.MAX_FAILED_ATTEMPTS.getAsInt()).append('\n');
        sb.append("esplus.lockMinutes=").append(Config.LOCK_MINUTES.getAsInt()).append('\n');
        sb.append("esplus.protectedCommands=").append(escapeProp(String.join(",", Config.PROTECTED_COMMANDS.get()))).append('\n');
        sb.append("esplus.securityReady=true\n");
        sb.append("esplus.serverId=").append(escapeProp(nullToEmpty(Config.SERVER_ID.get()))).append('\n');
        sb.append("esplus.serverName=").append(escapeProp(nullToEmpty(Config.SERVER_NAME.get()))).append('\n');
        sb.append("esplus.approvalEnabled=").append(Config.APPROVAL_ENABLED.getAsBoolean()).append('\n');
        sb.append("esplus.approvalGiveThreshold=").append(Config.APPROVAL_GIVE_THRESHOLD.getAsInt()).append('\n');
        sb.append("esplus.approvalRequiredActions=").append(escapeProp(String.join(",", Config.APPROVAL_ACTIONS.get()))).append('\n');
        sb.append("esplus.alertWebhookUrl=").append(escapeProp(nullToEmpty(Config.ALERT_WEBHOOK_URL.get()))).append('\n');
        sb.append("esplus.alertWebhookMinSeverity=").append(escapeProp(nullToEmpty(Config.ALERT_WEBHOOK_MIN_SEVERITY.get()))).append('\n');
        sb.append("esplus.auditHashChain=").append(Config.AUDIT_HASH_CHAIN.getAsBoolean()).append('\n');
        sb.append("esplus.opsApiToken=").append(escapeProp(nullToEmpty(Config.OPS_API_TOKEN.get()))).append('\n');
        Files.writeString(file, sb.toString());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeProp(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static void extractEmbeddedJar(Path target) throws IOException {
        try (InputStream in = IsolatedSpringPanel.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Path fallback = Path.of("panel", "build", "libs");
                if (Files.isDirectory(fallback)) {
                    try (var stream = Files.list(fallback)) {
                        Path found = stream
                                .filter(p -> {
                                    String n = p.getFileName().toString();
                                    return n.startsWith("esplus-panel") && n.endsWith(".jar") && !n.contains("plain");
                                })
                                .findFirst()
                                .orElse(null);
                        if (found != null) {
                            Files.copy(found, target, StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info("Spring panel jar loaded from dev fallback {}", found.toAbsolutePath());
                            return;
                        }
                    }
                }
                throw new IOException("Missing embedded resource " + RESOURCE
                        + ". Run: gradlew -p panel bootJar  then rebuild the mod.");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Extracted embedded Spring panel jar to {}", target.toAbsolutePath());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
