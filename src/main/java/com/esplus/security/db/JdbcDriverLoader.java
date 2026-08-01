package com.esplus.security.db;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads JDBC drivers using classloaders visible to the mod (dev runs + shaded production jar).
 */
public final class JdbcDriverLoader {
    private static volatile boolean sqliteRegistered;

    private JdbcDriverLoader() {
    }

    public static synchronized void ensureSqlite() throws SQLException {
        if (sqliteRegistered) {
            return;
        }
        List<ClassLoader> loaders = new ArrayList<>();
        loaders.add(JdbcDriverLoader.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());

        Exception last = null;
        for (ClassLoader cl : loaders) {
            if (cl == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName("org.sqlite.JDBC", true, cl);
                Driver driver = (Driver) clazz.getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(new DriverShim(driver));
                sqliteRegistered = true;
                return;
            } catch (Exception ex) {
                last = ex;
            }
        }
        throw new SQLException("Failed to load org.sqlite.JDBC. Rebuild so sqlite-jdbc classes are embedded into the mod output.", last);
    }

    private static final class DriverShim implements Driver {
        private final Driver delegate;

        private DriverShim(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            try {
                Method method = Driver.class.getMethod("getParentLogger");
                return (Logger) method.invoke(delegate);
            } catch (ReflectiveOperationException ex) {
                throw new SQLFeatureNotSupportedException(ex);
            }
        }
    }
}
