package com.courttrack.dao;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.Charge;
import com.courttrack.model.CourtCase;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

public class CaseDao {
    private static final Logger LOGGER = Logger.getLogger(CaseDao.class.getName());
    private final DatabaseManager db = DatabaseManager.getInstance();

    private static final String SELECT_WITH_CHARGE = """
        SELECT cc.*, ch.particulars AS charge_particulars, ch.verdict AS charge_verdict, ch.plea AS charge_plea, ch.sentence_notes AS sentence_notes
        FROM court_case cc
        LEFT JOIN charge ch ON ch.case_id = cc.case_id
            AND ch.charge_id = (SELECT MIN(c2.charge_id) FROM charge c2 WHERE c2.case_id = cc.case_id)
    """;

    public List<CourtCase> findAll() {
        return findAllPaginated(0, Integer.MAX_VALUE);
    }

    public List<CourtCase> findAllPaginated(int offset, int limit) {
        List<CourtCase> list = new ArrayList<>();
        String sql = SELECT_WITH_CHARGE + " WHERE cc.is_deleted = 0 ORDER BY cc.filing_date DESC LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithCharge(rs));
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.severe("Error counting all cases: " + e.getMessage());
        }
        return 0;
    }

    public int countByStatusAndCategory(String status, String category) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM court_case WHERE is_deleted = 0");
        List<String> params = new ArrayList<>();
        if (status != null && !status.equals("All")) {
            sql.append(" AND case_status = ?");
            params.add(status);
        }
        if (category != null && !category.equals("All")) {
            sql.append(" AND case_category = ?");
            params.add(category);
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (status != null && !status.equals("All")) ps.setString(idx++, status);
            if (category != null && !category.equals("All")) ps.setString(idx++, category);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return 0;
    }

    public List<CourtCase> findByStatusAndCategory(String status, String category) {
        return findByStatusAndCategoryPaginated(status, category, 0, Integer.MAX_VALUE);
    }

    public List<CourtCase> findByStatusAndCategoryPaginated(String status, String category, int offset, int limit) {
        List<CourtCase> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_WITH_CHARGE + " WHERE cc.is_deleted = 0");
        if (status != null && !status.equals("All")) sql.append(" AND cc.case_status = ?");
        if (category != null && !category.equals("All")) sql.append(" AND cc.case_category = ?");
        sql.append(" ORDER BY cc.filing_date DESC LIMIT ? OFFSET ?");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (status != null && !status.equals("All")) ps.setString(idx++, status);
            if (category != null && !category.equals("All")) ps.setString(idx++, category);
            ps.setInt(idx++, limit);
            ps.setInt(idx++, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithCharge(rs));
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    public CourtCase findById(String caseId) {
        String sql = SELECT_WITH_CHARGE + " WHERE cc.case_id = ? AND cc.is_deleted = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRowWithCharge(rs);
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return null;
    }

    public List<CourtCase> search(String query) {
        List<CourtCase> list = new ArrayList<>();
        String sql = SELECT_WITH_CHARGE + """
             WHERE cc.is_deleted = 0
             AND (cc.case_number LIKE ? OR cc.case_title LIKE ? OR cc.case_category LIKE ? OR cc.court_id LIKE ? OR ch.particulars LIKE ?)
             ORDER BY cc.filing_date DESC
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            for (int i = 1; i <= 5; i++) ps.setString(i, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithCharge(rs));
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    public List<CourtCase> findRecent(int limit) {
        List<CourtCase> list = new ArrayList<>();
        String sql = SELECT_WITH_CHARGE + " WHERE cc.is_deleted = 0 ORDER BY cc.created_at DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRowWithCharge(rs));
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    public void insert(CourtCase c) {
        String sql = """
            INSERT INTO court_case (case_id, case_number, case_title, court_id, court_name, filing_date,
                case_status, case_category, case_type, priority, description, date_of_judgment,
                sentence, mitigation_notes, prosecution_counsel, appeal_status, location_of_offence,
                evidence_summary, hearing_dates, court_assistant,
                accused_name, complainant_name, defense_witnesses, prosecution_witnesses,
                applicable_law, judge_name, offender_history)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getCaseId());
            ps.setString(2, c.getCaseNumber());
            ps.setString(3, c.getCaseTitle());
            ps.setString(4, c.getCourtId());
            ps.setString(5, c.getCourtName());
            ps.setString(6, c.getFilingDate() != null ? c.getFilingDate().toString() : null);
            ps.setString(7, c.getCaseStatus());
            ps.setString(8, c.getCaseCategory());
            ps.setString(9, c.getCaseType());
            ps.setString(10, c.getPriority());
            ps.setString(11, c.getDescription());
            ps.setString(12, c.getDateOfJudgment() != null ? c.getDateOfJudgment().toString() : null);
            ps.setString(13, c.getSentence());
            ps.setString(14, c.getMitigationNotes());
            ps.setString(15, c.getProsecutionCounsel());
            ps.setString(16, c.getAppealStatus());
            ps.setString(17, c.getLocationOfOffence());
            ps.setString(18, c.getEvidenceSummary());
            ps.setString(19, c.getHearingDates());
            ps.setString(20, c.getCourtAssistant());
            ps.setString(21, c.getAccusedName());
            ps.setString(22, c.getComplainantName());
            ps.setString(23, c.getDefenseWitnesses());
            ps.setString(24, c.getProsecutionWitnesses());
            ps.setString(25, c.getApplicableLaw());
            ps.setString(26, c.getJudgeName());
            ps.setString(27, c.getOffenderHistory());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void update(CourtCase c) {
        String sql = """
            UPDATE court_case SET case_number = ?, case_title = ?, court_id = ?, court_name = ?,
            filing_date = ?, case_status = ?, case_category = ?, case_type = ?, priority = ?,
            description = ?, date_of_judgment = ?, sentence = ?, mitigation_notes = ?,
            prosecution_counsel = ?, appeal_status = ?, location_of_offence = ?,
            evidence_summary = ?, hearing_dates = ?, court_assistant = ?,
            accused_name = ?, complainant_name = ?, defense_witnesses = ?, prosecution_witnesses = ?,
            applicable_law = ?, judge_name = ?, offender_history = ?,
            has_changes = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE case_id = ?
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getCaseNumber());
            ps.setString(2, c.getCaseTitle());
            ps.setString(3, c.getCourtId());
            ps.setString(4, c.getCourtName());
            ps.setString(5, c.getFilingDate() != null ? c.getFilingDate().toString() : null);
            ps.setString(6, c.getCaseStatus());
            ps.setString(7, c.getCaseCategory());
            ps.setString(8, c.getCaseType());
            ps.setString(9, c.getPriority());
            ps.setString(10, c.getDescription());
            ps.setString(11, c.getDateOfJudgment() != null ? c.getDateOfJudgment().toString() : null);
            ps.setString(12, c.getSentence());
            ps.setString(13, c.getMitigationNotes());
            ps.setString(14, c.getProsecutionCounsel());
            ps.setString(15, c.getAppealStatus());
            ps.setString(16, c.getLocationOfOffence());
            ps.setString(17, c.getEvidenceSummary());
            ps.setString(18, c.getHearingDates());
            ps.setString(19, c.getCourtAssistant());
            ps.setString(20, c.getAccusedName());
            ps.setString(21, c.getComplainantName());
            ps.setString(22, c.getDefenseWitnesses());
            ps.setString(23, c.getProsecutionWitnesses());
            ps.setString(24, c.getApplicableLaw());
            ps.setString(25, c.getJudgeName());
            ps.setString(26, c.getOffenderHistory());
            ps.setString(27, c.getCaseId());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void softDelete(String caseId) {
        String sql = "UPDATE court_case SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE case_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0 AND case_status = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error counting cases by status: " + e.getMessage());
        }
        return 0;
    }

    public int countActive() {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0 AND case_status NOT IN ('CLOSED', 'DISMISSED', 'SETTLED')";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.severe("Error counting active cases: " + e.getMessage());
        }
        return 0;
    }

    public int countFiledToday() {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0 AND filing_date = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error counting cases filed today: " + e.getMessage());
        }
        return 0;
    }

    // --- Case Participants ---

    public void addParticipant(CaseParticipant cp) {
        String sql = "INSERT INTO case_participant (participant_id, case_id, person_id, role_type, is_active) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cp.getParticipantId());
            ps.setString(2, cp.getCaseId());
            ps.setString(3, cp.getPersonId());
            ps.setString(4, cp.getRoleType());
            ps.setInt(5, cp.isActive() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public List<CaseParticipant> getParticipants(String caseId) {
        List<CaseParticipant> list = new ArrayList<>();
        String sql = "SELECT * FROM case_participant WHERE case_id = ? AND is_active = 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                CaseParticipant cp = new CaseParticipant();
                cp.setParticipantId(rs.getString("participant_id"));
                cp.setCaseId(rs.getString("case_id"));
                cp.setPersonId(rs.getString("person_id"));
                cp.setRoleType(rs.getString("role_type"));
                cp.setActive(rs.getInt("is_active") == 1);
                list.add(cp);
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    // --- Charges ---

    public void addCharge(Charge charge) {
        String sql = """
            INSERT INTO charge (charge_id, case_id, accused_person_id, offense_code, particulars, plea, verdict, sentence_notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, charge.getChargeId());
            ps.setString(2, charge.getCaseId());
            ps.setString(3, charge.getAccusedPersonId());
            ps.setString(4, charge.getOffenseCode());
            ps.setString(5, charge.getParticulars());
            ps.setString(6, charge.getPlea());
            ps.setString(7, charge.getVerdict());
            ps.setString(8, charge.getSentenceNotes());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void upsertFirstCharge(String caseId, String particulars, String plea, String verdict) {
        List<Charge> existing = getCharges(caseId);
        if (existing.isEmpty()) {
            if ((particulars != null && !particulars.isBlank()) || plea != null || verdict != null) {
                Charge ch = new Charge();
                ch.setCaseId(caseId);
                ch.setParticulars(particulars);
                ch.setPlea(plea);
                ch.setVerdict(verdict);
                addCharge(ch);
            }
        } else {
            Charge ch = existing.get(0);
            String sql = "UPDATE charge SET particulars = ?, plea = ?, verdict = ? WHERE charge_id = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, particulars);
                ps.setString(2, plea);
                ps.setString(3, verdict);
                ps.setString(4, ch.getChargeId());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Charge> getCharges(String caseId) {
        List<Charge> list = new ArrayList<>();
        String sql = "SELECT * FROM charge WHERE case_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Charge ch = new Charge();
                ch.setChargeId(rs.getString("charge_id"));
                ch.setCaseId(rs.getString("case_id"));
                ch.setAccusedPersonId(rs.getString("accused_person_id"));
                ch.setOffenseCode(rs.getString("offense_code"));
                ch.setParticulars(rs.getString("particulars"));
                ch.setPlea(rs.getString("plea"));
                ch.setVerdict(rs.getString("verdict"));
                ch.setSentenceNotes(rs.getString("sentence_notes"));
                list.add(ch);
            }
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
        return list;
    }

    private CourtCase mapRowWithCharge(ResultSet rs) throws SQLException {
        CourtCase c = new CourtCase(rs.getString("case_id"));
        c.setCaseNumber(rs.getString("case_number"));
        c.setCaseTitle(rs.getString("case_title"));
        c.setCourtId(rs.getString("court_id"));
        c.setCourtName(rs.getString("court_name"));
        String filingDate = rs.getString("filing_date");
        if (filingDate != null) {
            c.setFilingDate(LocalDate.parse(filingDate));
        }
        c.setCaseStatus(rs.getString("case_status"));
        c.setCaseCategory(rs.getString("case_category"));
        c.setCaseType(rs.getString("case_type"));
        c.setPriority(rs.getString("priority"));
        c.setDescription(rs.getString("description"));
        String judgmentDate = rs.getString("date_of_judgment");
        if (judgmentDate != null && !judgmentDate.isBlank()) {
            c.setDateOfJudgment(LocalDate.parse(judgmentDate));
        }
        c.setSentence(rs.getString("sentence"));
        c.setMitigationNotes(rs.getString("mitigation_notes"));
        c.setProsecutionCounsel(rs.getString("prosecution_counsel"));
        c.setAppealStatus(rs.getString("appeal_status"));
        c.setLocationOfOffence(rs.getString("location_of_offence"));
        c.setEvidenceSummary(rs.getString("evidence_summary"));
        c.setHearingDates(rs.getString("hearing_dates"));
        c.setCourtAssistant(rs.getString("court_assistant"));
        c.setDeleted(rs.getInt("is_deleted") == 1);
        c.setChargeParticulars(rs.getString("charge_particulars"));
        c.setChargeVerdict(rs.getString("charge_verdict"));
        c.setChargePlea(rs.getString("charge_plea"));
        c.setSentenceNotes(rs.getString("sentence_notes"));
        try { c.setAccusedName(rs.getString("accused_name")); } catch (SQLException ignored) {}
        try { c.setComplainantName(rs.getString("complainant_name")); } catch (SQLException ignored) {}
        try { c.setDefenseWitnesses(rs.getString("defense_witnesses")); } catch (SQLException ignored) {}
        try { c.setProsecutionWitnesses(rs.getString("prosecution_witnesses")); } catch (SQLException ignored) {}
        try { c.setApplicableLaw(rs.getString("applicable_law")); } catch (SQLException ignored) {}
        try { c.setJudgeName(rs.getString("judge_name")); } catch (SQLException ignored) {}
        try { c.setOffenderHistory(rs.getString("offender_history")); } catch (SQLException ignored) {}
        return c;
    }

    // --- Documents ---

    public void insertDocument(com.courttrack.model.CaseDocument doc) {
        String sql = "INSERT INTO case_document (document_id, case_id, name, mime_type, local_path, file_size, upload_date) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, doc.getDocumentId());
            ps.setString(2, doc.getCaseId());
            ps.setString(3, doc.getName());
            ps.setString(4, doc.getMimeType());
            ps.setString(5, doc.getLocalPath());
            ps.setLong(6, doc.getFileSize());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Error inserting document: " + e.getMessage());
        }
    }

    public List<com.courttrack.model.CaseDocument> getDocuments(String caseId) {
        List<com.courttrack.model.CaseDocument> list = new ArrayList<>();
        String sql = "SELECT * FROM case_document WHERE case_id = ? ORDER BY upload_date DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                com.courttrack.model.CaseDocument doc = new com.courttrack.model.CaseDocument(rs.getString("document_id"));
                doc.setCaseId(rs.getString("case_id"));
                doc.setName(rs.getString("name"));
                doc.setMimeType(rs.getString("mime_type"));
                doc.setLocalPath(rs.getString("local_path"));
                doc.setFileSize(rs.getLong("file_size"));
                list.add(doc);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error loading documents: " + e.getMessage());
        }
        return list;
    }

    public void deleteDocument(String documentId) {
        String sql = "DELETE FROM case_document WHERE document_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, documentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.severe("Error deleting document: " + e.getMessage());
        }
    }
}
