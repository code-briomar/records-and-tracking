package com.courttrack.dao;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.CaseParticipant;
import com.courttrack.model.CaseStageHistory;
import com.courttrack.model.Charge;
import com.courttrack.model.CourtCase;
import com.courttrack.model.AuditLog;
import com.courttrack.sync.CourtContext;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

public class CaseDao {
    private static final Logger LOGGER = Logger.getLogger(CaseDao.class.getName());
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final CaseStageHistoryDao stageHistoryDao = new CaseStageHistoryDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    private String currentUserId() {
        return CourtContext.getInstance().getUserId();
    }

    private String currentUsername() {
        String email = CourtContext.getInstance().getUserEmail();
        return email != null ? email : "system";
    }

    private void recordStageHistory(String caseId, String fromStatus, String toStatus, String notes) {
        if (caseId == null || toStatus == null) return;
        CaseStageHistory history = new CaseStageHistory();
        history.setCaseId(caseId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedByUserId(currentUserId());
        history.setChangedBy(currentUsername());
        history.setChangedAt(LocalDateTime.now());
        history.setNotes(notes);
        history.setCourtId(CourtContext.getInstance().getCourtId());
        stageHistoryDao.insertStageTransition(history);
    }

    private void auditCaseAction(String action, String caseId, String status, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(currentUserId());
        log.setUsername(currentUsername());
        log.setAction(action);
        log.setEntityType("CourtCase");
        log.setEntityId(caseId);
        log.setStatus(status);
        log.setDetails(details);
        log.setCourtId(CourtContext.getInstance().getCourtId());
        auditLogDao.insert(log);
    }

    private String computeChangedFields(CourtCase oldCase, CourtCase newCase) {
        if (oldCase == null) return "All fields initialized.";
        List<String> changes = new ArrayList<>();
        compareField("Case Number", oldCase.getCaseNumber(), newCase.getCaseNumber(), changes);
        compareField("Case Title", oldCase.getCaseTitle(), newCase.getCaseTitle(), changes);
        compareField("Court ID", oldCase.getCourtId(), newCase.getCourtId(), changes);
        compareField("Court Name", oldCase.getCourtName(), newCase.getCourtName(), changes);
        compareField("Filing Date", oldCase.getFilingDate(), newCase.getFilingDate(), changes);
        compareField("Status", oldCase.getCaseStatus(), newCase.getCaseStatus(), changes);
        compareField("Category", oldCase.getCaseCategory(), newCase.getCaseCategory(), changes);
        compareField("Case Type", oldCase.getCaseType(), newCase.getCaseType(), changes);
        compareField("Priority", oldCase.getPriority(), newCase.getPriority(), changes);
        compareField("Description", oldCase.getDescription(), newCase.getDescription(), changes);
        compareField("Judgment Date", oldCase.getDateOfJudgment(), newCase.getDateOfJudgment(), changes);
        compareField("Sentence", oldCase.getSentence(), newCase.getSentence(), changes);
        compareField("Mitigation Notes", oldCase.getMitigationNotes(), newCase.getMitigationNotes(), changes);
        compareField("Prosecution Counsel", oldCase.getProsecutionCounsel(), newCase.getProsecutionCounsel(), changes);
        compareField("Appeal Status", oldCase.getAppealStatus(), newCase.getAppealStatus(), changes);
        compareField("Location", oldCase.getLocationOfOffence(), newCase.getLocationOfOffence(), changes);
        compareField("Evidence Summary", oldCase.getEvidenceSummary(), newCase.getEvidenceSummary(), changes);
        compareField("Hearing Dates", oldCase.getHearingDates(), newCase.getHearingDates(), changes);
        compareField("Court Assistant", oldCase.getCourtAssistant(), newCase.getCourtAssistant(), changes);
        compareField("Accused Name", oldCase.getAccusedName(), newCase.getAccusedName(), changes);
        compareField("Complainant Name", oldCase.getComplainantName(), newCase.getComplainantName(), changes);
        compareField("Defense Witnesses", oldCase.getDefenseWitnesses(), newCase.getDefenseWitnesses(), changes);
        compareField("Prosecution Witnesses", oldCase.getProsecutionWitnesses(), newCase.getProsecutionWitnesses(), changes);
        compareField("Applicable Law", oldCase.getApplicableLaw(), newCase.getApplicableLaw(), changes);
        compareField("Judge Name", oldCase.getJudgeName(), newCase.getJudgeName(), changes);
        compareField("Offender History", oldCase.getOffenderHistory(), newCase.getOffenderHistory(), changes);

        if (changes.isEmpty()) {
            return "No text fields changed.";
        }
        return "Changed fields:\n" + String.join("\n", changes);
    }

    private void compareField(String label, Object oldVal, Object newVal, List<String> changes) {
        String o = oldVal == null ? "" : oldVal.toString().trim();
        String n = newVal == null ? "" : newVal.toString().trim();
        if (!o.equals(n)) {
            changes.add(String.format("- %s: '%s' -> '%s'", label, o.isEmpty() ? "<empty>" : o, n.isEmpty() ? "<empty>" : n));
        }
    }

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
            recordStageHistory(c.getCaseId(), null, c.getCaseStatus(), "Case created");
            auditCaseAction("CREATE", c.getCaseId(), c.getCaseStatus(), "Created case " + c.getCaseNumber());
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void update(CourtCase c) {
        CourtCase existing = findById(c.getCaseId());
        String previousStatus = existing != null ? existing.getCaseStatus() : null;
        String statusChangeNotes = null;
        if (!Objects.equals(previousStatus, c.getCaseStatus())) {
            statusChangeNotes = c.getTransitionNotes();
            if (statusChangeNotes == null || statusChangeNotes.isBlank()) {
                statusChangeNotes = String.format("Status changed from %s to %s",
                        previousStatus == null ? "<none>" : previousStatus,
                        c.getCaseStatus());
            }
        }

        String changedFieldsDesc = computeChangedFields(existing, c);

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
            if (statusChangeNotes != null) {
                recordStageHistory(c.getCaseId(), previousStatus, c.getCaseStatus(), statusChangeNotes);
            }
            auditCaseAction("UPDATE", c.getCaseId(), c.getCaseStatus(),
                    "Updated case " + c.getCaseNumber() + "\n" + changedFieldsDesc +
                    (statusChangeNotes != null ? "\nNotes: " + statusChangeNotes : ""));
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void softDelete(String caseId) {
        CourtCase existing = findById(caseId);
        String previousStatus = existing != null ? existing.getCaseStatus() : null;
        String historyNote = previousStatus != null ? "Case deleted from status " + previousStatus : "Case deleted";
        String sql = "UPDATE court_case SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE case_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, caseId);
            ps.executeUpdate();
            if (previousStatus != null) {
                recordStageHistory(caseId, previousStatus, "DELETED", historyNote);
            }
            auditCaseAction("DELETE", caseId, previousStatus, historyNote);
        } catch (SQLException e) {
            LOGGER.severe("Database error: " + e.getMessage());
        }
    }

    public void updateCaseStatus(String caseId, String newStatus, String notes) {
        CourtCase existing = findById(caseId);
        if (existing == null) return;
        String previousStatus = existing.getCaseStatus();
        if (Objects.equals(previousStatus, newStatus)) return;

        String sql = "UPDATE court_case SET case_status = ?, has_changes = TRUE, updated_at = CURRENT_TIMESTAMP WHERE case_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, caseId);
            ps.executeUpdate();

            recordStageHistory(caseId, previousStatus, newStatus, notes);
            auditCaseAction("UPDATE", caseId, newStatus,
                    String.format("Status transitioned from %s to %s. Notes: %s", previousStatus, newStatus, notes));
        } catch (SQLException e) {
            LOGGER.severe("Database error transitioning status: " + e.getMessage());
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

    public int countFiledThisMonth() {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0 " +
                     "AND YEAR(filing_date) = YEAR(CURRENT_DATE) AND MONTH(filing_date) = MONTH(CURRENT_DATE)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.severe("Error counting cases filed this month: " + e.getMessage());
        }
        return 0;
    }

    public int countOnAppeal() {
        String sql = "SELECT COUNT(*) FROM court_case WHERE is_deleted = 0 " +
                     "AND appeal_status IS NOT NULL AND appeal_status != ''";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.severe("Error counting appeal cases: " + e.getMessage());
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

    public java.util.Map<String, Integer> getDelayCounts() {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        map.put("30-60", 0);
        map.put("60-90", 0);
        map.put("90+", 0);
        String sql = """
            SELECT
                SUM(CASE WHEN DATEDIFF('DAY', filing_date, CURRENT_DATE) > 30 AND DATEDIFF('DAY', filing_date, CURRENT_DATE) <= 60 THEN 1 ELSE 0 END) AS delay_30_60,
                SUM(CASE WHEN DATEDIFF('DAY', filing_date, CURRENT_DATE) > 60 AND DATEDIFF('DAY', filing_date, CURRENT_DATE) <= 90 THEN 1 ELSE 0 END) AS delay_60_90,
                SUM(CASE WHEN DATEDIFF('DAY', filing_date, CURRENT_DATE) > 90 THEN 1 ELSE 0 END) AS delay_90_plus
            FROM court_case
            WHERE is_deleted = 0 AND case_status NOT IN ('CLOSED', 'Closed', 'DISMISSED', 'SETTLED')
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                map.put("30-60", rs.getInt("delay_30_60"));
                map.put("60-90", rs.getInt("delay_60_90"));
                map.put("90+", rs.getInt("delay_90_plus"));
            }
        } catch (SQLException e) {
            LOGGER.severe("Error fetching delay counts: " + e.getMessage());
        }
        return map;
    }

    public List<java.util.Map<String, Object>> getAverageAgeByCourt() {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT COALESCE(court_name, 'Unknown Court') as court, AVG(DATEDIFF('DAY', filing_date, CURRENT_DATE)) as avg_age
            FROM court_case
            WHERE is_deleted = 0 AND case_status NOT IN ('CLOSED', 'Closed', 'DISMISSED', 'SETTLED')
            GROUP BY court_name
            ORDER BY avg_age DESC
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("court", rs.getString("court"));
                map.put("avg_age", rs.getDouble("avg_age"));
                list.add(map);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error fetching average case age by court: " + e.getMessage());
        }
        return list;
    }

    public List<java.util.Map<String, Object>> getStageWiseAging() {
        List<java.util.Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT cc.case_status as stage, AVG(DATEDIFF('DAY', COALESCE(h.changed_at, cc.created_at), CURRENT_TIMESTAMP)) as avg_days
            FROM court_case cc
            LEFT JOIN (
                SELECT case_id, to_status, MAX(changed_at) as changed_at
                FROM case_stage_history
                GROUP BY case_id, to_status
            ) h ON h.case_id = cc.case_id AND h.to_status = cc.case_status
            WHERE cc.is_deleted = 0 AND cc.case_status NOT IN ('CLOSED', 'Closed', 'DISMISSED', 'SETTLED')
            GROUP BY cc.case_status
            ORDER BY avg_days DESC
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("stage", rs.getString("stage"));
                map.put("avg_days", rs.getDouble("avg_days"));
                list.add(map);
            }
        } catch (SQLException e) {
            LOGGER.severe("Error fetching stage wise aging: " + e.getMessage());
        }
        return list;
    }
}
