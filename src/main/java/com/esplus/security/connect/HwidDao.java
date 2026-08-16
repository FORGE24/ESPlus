package com.esplus.security.connect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.esplus.security.db.SqliteDatabase;

public final class HwidDao {
    private final SqliteDatabase database;

    public HwidDao(SqliteDatabase database) {
        this.database = database;
    }

    public void insert(String hwid, String name, String reason, long createdAt) throws SQLException {
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "INSERT OR REPLACE INTO hwid_blacklist (hwid, name, reason, created_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, hwid);
                ps.setString(2, name);
                ps.setString(3, reason);
                ps.setLong(4, createdAt);
                ps.executeUpdate();
            }
        }
    }

    public boolean exists(String hwid) {
        if (hwid == null || hwid.isBlank()) {
            return false;
        }
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT 1 FROM hwid_blacklist WHERE hwid = ?")) {
                ps.setString(1, hwid);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException ex) {
                return false;
            }
        }
    }

    public List<HwidRecord> list() {
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT hwid, name, reason, created_at FROM hwid_blacklist ORDER BY created_at DESC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    List<HwidRecord> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new HwidRecord(
                                rs.getString(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getLong(4)
                        ));
                    }
                    return list;
                }
            } catch (SQLException ex) {
                return List.of();
            }
        }
    }

    public boolean remove(String hwid) {
        synchronized (database.lock()) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "DELETE FROM hwid_blacklist WHERE hwid = ?")) {
                ps.setString(1, hwid);
                return ps.executeUpdate() > 0;
            } catch (SQLException ex) {
                return false;
            }
        }
    }

    public record HwidRecord(String hwid, String name, String reason, long createdAt) {
    }
}
