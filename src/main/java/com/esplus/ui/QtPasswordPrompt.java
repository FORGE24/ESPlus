package com.esplus.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Launches the bundled Qt6 password window. Password never appears in argv —
 * only returned via redirected stdout as one Base64 line.
 */
public final class QtPasswordPrompt {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "META-INF/esplus/esplus-pwprompt-win64.zip";
    private static final Object EXTRACT_LOCK = new Object();

    private QtPasswordPrompt() {
    }

    public record PromptResult(Status status, String password) {
        public enum Status {
            OK,
            CANCELED,
            UNAVAILABLE,
            ERROR
        }

        public static PromptResult ok(String password) {
            return new PromptResult(Status.OK, password);
        }

        public static PromptResult canceled() {
            return new PromptResult(Status.CANCELED, null);
        }

        public static PromptResult unavailable() {
            return new PromptResult(Status.UNAVAILABLE, null);
        }

        public static PromptResult error() {
            return new PromptResult(Status.ERROR, null);
        }
    }

    public static PromptResult prompt(Path gameDir, String title, String prompt, boolean confirm) {
        try {
            Path exe = ensureExtracted(gameDir);
            if (exe == null || !Files.isRegularFile(exe)) {
                return PromptResult.unavailable();
            }
            List<String> command = new ArrayList<>();
            command.add(exe.toAbsolutePath().toString());
            command.add("--title");
            command.add(title == null ? "ESPlus" : title);
            command.add("--prompt");
            command.add(prompt == null ? "Enter password" : prompt);
            command.add("--mode");
            command.add(confirm ? "confirm" : "single");

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(exe.getParent().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.US_ASCII))) {
                line = reader.readLine();
            }
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return PromptResult.error();
            }
            int code = process.exitValue();
            if (code == 2) {
                return PromptResult.canceled();
            }
            if (code != 0 || line == null || line.isBlank()) {
                return PromptResult.error();
            }
            byte[] decoded = Base64.getDecoder().decode(line.trim());
            String password = new String(decoded, StandardCharsets.UTF_8);
            if (password.isEmpty()) {
                return PromptResult.error();
            }
            return PromptResult.ok(password);
        } catch (Exception ex) {
            LOGGER.error("Qt password prompt failed", ex);
            return PromptResult.error();
        }
    }

    private static Path ensureExtracted(Path gameDir) throws IOException {
        Path targetDir = gameDir.resolve("esplus").resolve("native").resolve("pwprompt")
                .toAbsolutePath().normalize();
        Path exe = targetDir.resolve("esplus-pwprompt.exe");
        synchronized (EXTRACT_LOCK) {
            if (Files.isRegularFile(exe)) {
                return exe;
            }
            Optional<Path> fromResource = extractZipResource(targetDir);
            if (fromResource.isPresent()) {
                return fromResource.get();
            }
            // Dev fallback: native/pwprompt/dist
            Path fallback = Path.of("native", "pwprompt", "dist", "esplus-pwprompt.exe");
            if (!Files.isRegularFile(fallback)) {
                fallback = Path.of("..", "native", "pwprompt", "dist", "esplus-pwprompt.exe").normalize();
            }
            if (Files.isRegularFile(fallback)) {
                LOGGER.info("Using Qt pwprompt from {}", fallback.toAbsolutePath());
                return fallback.toAbsolutePath().normalize();
            }
            LOGGER.error("Qt pwprompt binary missing. Build with native/build-pwprompt.bat then rebuild mod.");
            return null;
        }
    }

    private static Optional<Path> extractZipResource(Path targetDir) throws IOException {
        Path base = targetDir.toAbsolutePath().normalize();
        try (InputStream in = QtPasswordPrompt.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Optional.empty();
            }
            Files.createDirectories(base);
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().replace('\\', '/');
                    if (name.isBlank() || name.contains("..") || name.startsWith("/")
                            || name.matches("^[A-Za-z]:.*")) {
                        throw new IOException("Zip slip blocked: " + entry.getName());
                    }
                    Path out = base.resolve(name).normalize();
                    // Must compare against absolute+normalized base; otherwise Windows Path.startsWith
                    // falsely rejects entries when gameDir contains "." / ".." components.
                    if (!out.startsWith(base)) {
                        throw new IOException("Zip slip blocked: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Files.createDirectories(out.getParent());
                        Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Path exe = base.resolve("esplus-pwprompt.exe");
            if (Files.isRegularFile(exe)) {
                LOGGER.info("Extracted Qt pwprompt to {}", exe);
                return Optional.of(exe);
            }
            return Optional.empty();
        }
    }
}
