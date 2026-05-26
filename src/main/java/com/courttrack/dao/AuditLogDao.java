package com.courttrack.dao;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AuditLogDao {
    private static final Logger LOGGER = Logger.getLogger(AuditLogDao.class.getName());
    private final DatabaseManager db = DatabaseManager.getInstance();

    public void insert(AuditLog log) {
        String sql = """
            INSERT INTO audit_log (
                log_id, timestamp, user_id, username, action,
                entity_type, entity_id, status, details, court_id,
                ip_address, device_info
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getLogId());
            ps.setTimestamp(2, log.getTimestamp() != null ? java.sql.Timestamp.valueOf(log.getTimestamp()) : null);
            ps.setString(3, log.getUserId());
            ps.setString(4, log.getUsername());
            ps.setString(5, log.getAction());
            ps.setString(6, log.getEntityType());
            ps.setString(7, log.getEntityId());
            ps.setString(8, log.getStatus());
            ps.setString(9, log.getDetails());
            ps.setString(10, log.getCourtId());
            ps.setString(11, log.getIpAddress());
            ps.setString(12, log.getDeviceInfo());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to insert audit log: " + e.getMessage());
        }
    }

    public List<AuditLog> findByEntityId(String entityId) {
        List<AuditLog> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_log WHERE entity_id = ? ORDER BY timestamp DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entityId);
            var rs = ps.executeQuery();
            while (rs.next()) {
                AuditLog log = new AuditLog(rs.getString("log_id"));
                java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                if (timestamp != null) {
                    log.setTimestamp(timestamp.toLocalDateTime());
                }
                log.setUserId(rs.getString("user_id"));
                log.setUsername(rs.getString("username"));
                log.setAction(rs.getString("action"));
                log.setEntityType(rs.getString("entity_type"));
                log.setEntityId(rs.getString("entity_id"));
                log.setStatus(rs.getString("status"));
                log.setDetails(rs.getString("details"));
                log.setCourtId(rs.getString("court_id"));
                log.setIpAddress(rs.getString("ip_address"));
                log.setDeviceInfo(rs.getString("device_info"));
                list.add(log);
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to query audit logs: " + e.getMessage());
        }
        return list;
    }

    public List<java.util.Map<String, Object>> findRecentCaseActivity(int limit) {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT a.*, cc.case_number, cc.case_title
            FROM audit_log a
            LEFT JOIN court_case cc ON a.entity_id = cc.case_id
            WHERE a.entity_type = 'CourtCase'
            ORDER BY a.timestamp DESC
            LIMIT ?
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("log_id", rs.getString("log_id"));
                    java.sql.Timestamp ts = rs.getTimestamp("timestamp");
                    if (ts != null) {
                        map.put("timestamp", ts.toLocalDateTime());
                    }
                    map.put("user_id", rs.getString("user_id"));
                    map.put("username", rs.getString("username"));
                    map.put("action", rs.getString("action"));
                    map.put("status", rs.getString("status"));
                    map.put("details", rs.getString("details"));
                    map.put("court_id", rs.getString("court_id"));
                    map.put("case_number", rs.getString("case_number"));
                    map.put("case_title", rs.getString("case_title"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to query recent case activity: " + e.getMessage());
        }
        return list;
    }

    public List<java.util.Map<String, Object>> getOfficerActivityStats(boolean weekly) {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String interval = weekly ? "DATEADD('DAY', -7, CURRENT_TIMESTAMP)" : "DATEADD('DAY', -30, CURRENT_TIMESTAMP)";
        String sql = String.format("""
            SELECT COALESCE(username, 'System') as officer, COUNT(*) as action_count
            FROM audit_log
            WHERE timestamp >= %s AND action IN ('CREATE', 'UPDATE')
            GROUP BY username
            ORDER BY action_count DESC
        """, interval);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("officer", rs.getString("officer"));
                map.put("action_count", rs.getInt("action_count"));
                list.add(map);
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to query officer activity stats: " + e.getMessage());
        }
        return list;
    }
}
