package com.esplus.log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import com.esplus.security.db.ServerLogDao;
import com.esplus.security.db.ServerLogDao.LogLine;

/**
 * Captures Log4j root logs into memory and flushes to SQLite for the panel.
 */
public final class ServerLogCapture {
    private static final String APPENDER_NAME = "ESPlusPanelLog";
    private static final int MAX_QUEUE = 5000;

    private final ConcurrentLinkedQueue<LogLine> queue = new ConcurrentLinkedQueue<>();
    private ServerLogDao dao;
    private volatile boolean attached;

    public void bind(ServerLogDao dao) {
        this.dao = dao;
    }

    public synchronized void attach() {
        if (attached) {
            return;
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        Layout<? extends Serializable> layout = PatternLayout.newBuilder()
                .withPattern("%m")
                .withConfiguration(config)
                .build();
        Appender appender = new CaptureAppender(APPENDER_NAME, null, layout, true, Property.EMPTY_ARRAY, queue);
        appender.start();
        config.addAppender(appender);
        LoggerConfig root = config.getRootLogger();
        root.addAppender(appender, Level.DEBUG, null);
        ctx.updateLoggers();
        attached = true;
    }

    public synchronized void detach() {
        if (!attached) {
            return;
        }
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig root = config.getRootLogger();
        root.removeAppender(APPENDER_NAME);
        Appender appender = config.getAppender(APPENDER_NAME);
        if (appender != null) {
            appender.stop();
            config.getAppenders().remove(APPENDER_NAME);
        }
        ctx.updateLoggers();
        attached = false;
    }

    public void flush() {
        if (dao == null) {
            queue.clear();
            return;
        }
        List<LogLine> batch = new ArrayList<>(256);
        LogLine line;
        while ((line = queue.poll()) != null) {
            batch.add(line);
            if (batch.size() >= 256) {
                break;
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            dao.insertBatch(batch);
            dao.prune(2500);
        } catch (Exception ignored) {
            // drop on failure to avoid log recursion
        }
    }

    private static final class CaptureAppender extends AbstractAppender {
        private final ConcurrentLinkedQueue<LogLine> queue;

        private CaptureAppender(
                String name,
                Filter filter,
                Layout<? extends Serializable> layout,
                boolean ignoreExceptions,
                Property[] properties,
                ConcurrentLinkedQueue<LogLine> queue
        ) {
            super(name, filter, layout, ignoreExceptions, properties);
            this.queue = queue;
        }

        @Override
        public void append(LogEvent event) {
            if (queue.size() >= MAX_QUEUE) {
                queue.poll();
            }
            String message = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            if (event.getThrown() != null) {
                message = message + " | " + event.getThrown().toString();
            }
            queue.offer(new LogLine(
                    event.getTimeMillis(),
                    event.getLevel() == null ? "INFO" : event.getLevel().name(),
                    event.getLoggerName(),
                    message
            ));
        }
    }
}
