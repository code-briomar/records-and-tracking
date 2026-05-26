package com.courttrack.dao;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.CaseStageHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CaseStageHistoryDao {
    private static final Logger LOGGER = Logger.getLogger(CaseStageHistoryDao.class.getName());
    private final DatabaseManager db = DatabaseManager.getInstance();

    public void insertStageTransition(CaseStageHistory history) {
        String sql = """
            INSERT INTO case_stage_history (
                history_id, case_id, from_status, to_status,
                changed_by_user_id, changed_by, changed_at, notes, court_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, history.getHistoryId());
            ps.setString(2, history.getCaseId());
            ps.setString(3, history.getFromStatus());
            ps.setString(4, history.getToStatus());
            ps.setString(5, history.getChangedByUserId());
            ps.setString(6, history.getChangedBy());
            ps.setTimestamp(7, history.getChangedAt() != null ? java.sql.Timestamp.valueOf(history.getChangedAt()) : null);
            ps.setString(8, history.getNotes());
            ps.setString(9, history.getCourtId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Failed to insert case stage history: " + e.getMessage());
        }
    }

    public List<CaseStageHistory> findByCaseId(String caseId) {
        List<CaseStageHistory> list = new ArrayList<>();
        String sql = "SELECT * FROM case_stage_history WHERE case_id = ? ORDER BY changed_at ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CaseStageHistory history = new CaseStageHistory(rs.getString("history_id"));
                history.setCaseId(rs.getString("case_id"));
                history.setFromStatus(rs.getString("from_status"));
                history.setToStatus(rs.getString("to_status"));
                history.setChangedByUserId(rs.getString("changed_by_user_id"));
                history.setChangedBy(rs.getString("changed_by"));
                java.sql.Timestamp changedAt = rs.getTimestamp("changed_at");
                if (changedAt != null) {
                    history.setChangedAt(changedAt.toLocalDateTime());
                }
                history.setNotes(rs.getString("notes"));
                history.setCourtId(rs.getString("court_id"));
                list.add(history);
            }
        } catch (SQLException e) {
            LOGGER.severe("Failed to query case stage history: " + e.getMessage());
        }
        return list;
    }
}
