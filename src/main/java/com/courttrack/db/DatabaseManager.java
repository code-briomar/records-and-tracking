package com.courttrack.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:court_records.db";
    private static DatabaseManager instance;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public void initialize() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");

            // --- Core tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS court (
                    court_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    location TEXT,
                    email TEXT,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT (datetime('now')),
                    created_by TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS person (
                    person_id TEXT PRIMARY KEY,
                    national_id TEXT UNIQUE,
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    other_names TEXT,
                    gender TEXT,
                    dob TEXT,
                    phone_number TEXT,
                    email TEXT,
                    photo_local_uri TEXT,
                    is_deleted INTEGER DEFAULT 0,
                    alias TEXT,
                    nationality TEXT,
                    marital_status TEXT,
                    occupation TEXT,
                    address TEXT,
                    first_offender INTEGER DEFAULT 0,
                    criminal_history TEXT,
                    known_associates TEXT,
                    arrest_date TEXT,
                    arresting_officer TEXT,
                    place_of_arrest TEXT,
                    penalty TEXT,
                    notes TEXT,
                    eye_color TEXT,
                    hair_color TEXT,
                    emergency_contact_name TEXT,
                    emergency_contact_phone TEXT,
                    emergency_contact_relationship TEXT,
                    legal_representation TEXT,
                    medical_conditions TEXT,
                    risk_level TEXT,
                    distinguishing_marks TEXT,
                    type TEXT,
                    status TEXT,
                    facility TEXT,
                    offense_type TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now'))
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS court_case (
                    case_id TEXT PRIMARY KEY,
                    case_number TEXT UNIQUE NOT NULL,
                    case_title TEXT,
                    court_id TEXT,
                    court_name TEXT,
                    filing_date TEXT NOT NULL,
                    case_status TEXT NOT NULL DEFAULT 'OPEN',
                    case_category TEXT NOT NULL,
                    case_type TEXT,
                    priority TEXT,
                    description TEXT,
                    date_of_judgment TEXT,
                    sentence TEXT,
                    mitigation_notes TEXT,
                    prosecution_counsel TEXT,
                    appeal_status TEXT,
                    location_of_offence TEXT,
                    evidence_summary TEXT,
                    hearing_dates TEXT,
                    court_assistant TEXT,
                    is_deleted INTEGER DEFAULT 0,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now'))
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS case_participant (
                    participant_id TEXT PRIMARY KEY,
                    case_id TEXT NOT NULL,
                    person_id TEXT NOT NULL,
                    role_type TEXT NOT NULL,
                    is_active INTEGER DEFAULT 1,
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id),
                    FOREIGN KEY (person_id) REFERENCES person(person_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS charge (
                    charge_id TEXT PRIMARY KEY,
                    case_id TEXT NOT NULL,
                    accused_person_id TEXT,
                    offense_code TEXT,
                    particulars TEXT,
                    plea TEXT,
                    verdict TEXT,
                    sentence_notes TEXT,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id),
                    FOREIGN KEY (accused_person_id) REFERENCES person(person_id)
                )
            """);

            // --- Auth & security tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    user_id TEXT PRIMARY KEY,
                    email TEXT NOT NULL,
                    full_name TEXT,
                    username TEXT,
                    password_hash TEXT,
                    salt TEXT,
                    court_id TEXT,
                    title TEXT,
                    professional_title TEXT,
                    role TEXT NOT NULL DEFAULT 'CLERK',
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    failed_login_attempts INTEGER DEFAULT 0,
                    password_expiry_date TEXT,
                    account_lock_timestamp TEXT,
                    require_password_change INTEGER DEFAULT 0,
                    last_login_date TEXT,
                    language TEXT DEFAULT 'en',
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now')),
                    FOREIGN KEY (court_id) REFERENCES court(court_id)
                )
            """);

            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_court_email
                ON app_user(court_id, email)
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auth_token (
                    token_id TEXT PRIMARY KEY,
                    token TEXT NOT NULL,
                    user_id TEXT NOT NULL,
                    issued_at TEXT DEFAULT (datetime('now')),
                    expires_at TEXT,
                    is_revoked INTEGER DEFAULT 0,
                    revoked_at TEXT,
                    last_used_at TEXT,
                    refresh_token TEXT,
                    refresh_expires_at TEXT,
                    device_info TEXT,
                    ip_address TEXT,
                    FOREIGN KEY (user_id) REFERENCES app_user(user_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS login_attempt (
                    attempt_id TEXT PRIMARY KEY,
                    username TEXT,
                    user_id TEXT,
                    email TEXT,
                    status TEXT NOT NULL,
                    failure_reason TEXT,
                    timestamp TEXT DEFAULT (datetime('now')),
                    ip_address TEXT,
                    device_info TEXT
                )
            """);

            // --- Document & audit tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS case_document (
                    document_id TEXT PRIMARY KEY,
                    case_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    mime_type TEXT,
                    local_path TEXT,
                    file_size INTEGER DEFAULT 0,
                    upload_date TEXT DEFAULT (datetime('now')),
                    uploaded_by TEXT,
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    log_id TEXT PRIMARY KEY,
                    timestamp TEXT DEFAULT (datetime('now')),
                    user_id TEXT,
                    username TEXT,
                    action TEXT NOT NULL,
                    entity_type TEXT,
                    entity_id TEXT,
                    status TEXT,
                    details TEXT,
                    court_id TEXT,
                    ip_address TEXT,
                    device_info TEXT
                )
            """);

            // --- Migrations for existing databases ---
            migrateSchema(stmt);

            seedDataIfEmpty(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void migrateSchema(Statement stmt) {
        // court_case new columns
        safeAddColumn(stmt, "court_case", "case_title", "TEXT");
        safeAddColumn(stmt, "court_case", "court_name", "TEXT");
        safeAddColumn(stmt, "court_case", "case_type", "TEXT");
        safeAddColumn(stmt, "court_case", "priority", "TEXT");
        safeAddColumn(stmt, "court_case", "description", "TEXT");
        safeAddColumn(stmt, "court_case", "date_of_judgment", "TEXT");
        safeAddColumn(stmt, "court_case", "sentence", "TEXT");
        safeAddColumn(stmt, "court_case", "mitigation_notes", "TEXT");
        safeAddColumn(stmt, "court_case", "prosecution_counsel", "TEXT");
        safeAddColumn(stmt, "court_case", "appeal_status", "TEXT");
        safeAddColumn(stmt, "court_case", "location_of_offence", "TEXT");
        safeAddColumn(stmt, "court_case", "evidence_summary", "TEXT");
        safeAddColumn(stmt, "court_case", "hearing_dates", "TEXT");
        safeAddColumn(stmt, "court_case", "court_assistant", "TEXT");

        // person new columns
        safeAddColumn(stmt, "person", "alias", "TEXT");
        safeAddColumn(stmt, "person", "nationality", "TEXT");
        safeAddColumn(stmt, "person", "marital_status", "TEXT");
        safeAddColumn(stmt, "person", "occupation", "TEXT");
        safeAddColumn(stmt, "person", "address", "TEXT");
        safeAddColumn(stmt, "person", "first_offender", "INTEGER DEFAULT 0");
        safeAddColumn(stmt, "person", "criminal_history", "TEXT");
        safeAddColumn(stmt, "person", "known_associates", "TEXT");
        safeAddColumn(stmt, "person", "arrest_date", "TEXT");
        safeAddColumn(stmt, "person", "arresting_officer", "TEXT");
        safeAddColumn(stmt, "person", "place_of_arrest", "TEXT");
        safeAddColumn(stmt, "person", "penalty", "TEXT");
        safeAddColumn(stmt, "person", "notes", "TEXT");
        safeAddColumn(stmt, "person", "eye_color", "TEXT");
        safeAddColumn(stmt, "person", "hair_color", "TEXT");
        safeAddColumn(stmt, "person", "emergency_contact_name", "TEXT");
        safeAddColumn(stmt, "person", "emergency_contact_phone", "TEXT");
        safeAddColumn(stmt, "person", "emergency_contact_relationship", "TEXT");
        safeAddColumn(stmt, "person", "legal_representation", "TEXT");
        safeAddColumn(stmt, "person", "medical_conditions", "TEXT");
        safeAddColumn(stmt, "person", "risk_level", "TEXT");
        safeAddColumn(stmt, "person", "distinguishing_marks", "TEXT");
        safeAddColumn(stmt, "person", "type", "TEXT");
        safeAddColumn(stmt, "person", "status", "TEXT");
        safeAddColumn(stmt, "person", "facility", "TEXT");
        safeAddColumn(stmt, "person", "offense_type", "TEXT");

        // charge new columns
        safeAddColumn(stmt, "charge", "created_at", "TEXT DEFAULT (datetime('now'))");
        safeAddColumn(stmt, "charge", "updated_at", "TEXT DEFAULT (datetime('now'))");
    }

    private void safeAddColumn(Statement stmt, String table, String column, String type) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    private void seedDataIfEmpty(Connection conn) throws SQLException {
        var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM person");
        rs.next();
        if (rs.getInt(1) > 0) return;

        try (Statement stmt = conn.createStatement()) {
            // Seed courts
            stmt.execute("""
                INSERT INTO court (court_id, name, location, email)
                VALUES
                ('court-nairobi-mc', 'Nairobi Magistrates Court', 'Nairobi CBD', 'nairobi.mc@judiciary.go.ke'),
                ('court-nairobi-hc', 'Nairobi High Court', 'Nairobi CBD', 'nairobi.hc@judiciary.go.ke'),
                ('court-mombasa-mc', 'Mombasa Law Courts', 'Mombasa', 'mombasa.mc@judiciary.go.ke'),
                ('court-kisumu-mc', 'Kisumu Law Courts', 'Kisumu', 'kisumu.mc@judiciary.go.ke'),
                ('court-nakuru-mc', 'Nakuru Law Courts', 'Nakuru', 'nakuru.mc@judiciary.go.ke'),
                ('court-eldoret-mc', 'Eldoret Law Courts', 'Eldoret', 'eldoret.mc@judiciary.go.ke')
            """);

            // Seed admin user (password: admin123 — SHA-256 placeholder)
            stmt.execute("""
                INSERT INTO app_user (user_id, email, full_name, username, password_hash, salt, court_id, role, status)
                VALUES ('admin-001', 'admin@courttrack.ke', 'System Administrator', 'admin',
                        'placeholder_hash', 'placeholder_salt', 'court-nairobi-mc', 'COURT_ADMIN', 'ACTIVE')
            """);

            // Seed persons
            stmt.execute("""
                INSERT INTO person (person_id, national_id, first_name, last_name, gender, dob, phone_number, email,
                                    nationality, occupation, risk_level, status)
                VALUES
                ('p1', '12345678', 'John', 'Kamau', 'Male', '1990-03-15', '0712345678', 'jkamau@email.com',
                 'Kenyan', 'Trader', 'LOW', 'Active'),
                ('p2', '23456789', 'Jane', 'Wanjiku', 'Female', '1985-07-22', '0723456789', 'jwanjiku@email.com',
                 'Kenyan', 'Driver', 'LOW', 'Active'),
                ('p3', '34567890', 'Peter', 'Odhiambo', 'Male', '1992-11-08', '0734567890', 'podhiambo@email.com',
                 'Kenyan', 'Farmer', 'MEDIUM', 'Active'),
                ('p4', '45678901', 'Mary', 'Akinyi', 'Female', '1988-01-30', '0745678901', 'makinyi@email.com',
                 'Kenyan', 'Teacher', 'LOW', 'Active'),
                ('p5', '56789012', 'Samuel', 'Kipchoge', 'Male', '1995-06-12', '0756789012', 'skipchoge@email.com',
                 'Kenyan', 'Security Guard', 'MEDIUM', 'Active'),
                ('p6', '67890123', 'Grace', 'Muthoni', 'Female', '1991-09-25', '0767890123', 'gmuthoni@email.com',
                 'Kenyan', 'Nurse', 'LOW', 'Active'),
                ('p7', '78901234', 'David', 'Wanyama', 'Male', '1987-04-18', '0778901234', 'dwanyama@email.com',
                 'Kenyan', 'Mechanic', 'LOW', 'Released'),
                ('p8', '89012345', 'Faith', 'Njeri', 'Female', '1993-12-05', '0789012345', 'fnjeri@email.com',
                 'Kenyan', 'Business Owner', 'LOW', 'Active')
            """);

            // Seed cases with enriched fields
            stmt.execute("""
                INSERT INTO court_case (case_id, case_number, case_title, court_id, court_name, filing_date,
                                        case_status, case_category, priority, location_of_offence)
                VALUES
                ('c1', 'CR-001/2024', 'Republic v. John Kamau', 'court-nairobi-mc', 'Nairobi Magistrates Court',
                 '2024-01-15', 'OPEN', 'Criminal', 'MEDIUM', 'Nairobi CBD'),
                ('c2', 'TR-002/2024', 'Republic v. Jane Wanjiku', 'court-nairobi-mc', 'Nairobi Magistrates Court',
                 '2024-02-20', 'OPEN', 'Traffic', 'LOW', 'Thika Road'),
                ('c3', 'CV-003/2024', 'Odhiambo v. Akinyi', 'court-nairobi-hc', 'Nairobi High Court',
                 '2024-03-10', 'CLOSED', 'Civil', 'HIGH', NULL),
                ('c4', 'CR-004/2024', 'Republic v. Samuel Kipchoge', 'court-mombasa-mc', 'Mombasa Law Courts',
                 '2024-04-05', 'OPEN', 'Criminal', 'HIGH', 'Likoni'),
                ('c5', 'TR-005/2024', 'Republic v. David Wanyama', 'court-kisumu-mc', 'Kisumu Law Courts',
                 '2024-05-18', 'CLOSED', 'Traffic', 'LOW', 'Kisumu-Busia Highway'),
                ('c6', 'CV-006/2024', 'Njeri v. Muthoni & Others', 'court-nairobi-hc', 'Nairobi High Court',
                 '2024-06-22', 'OPEN', 'Civil', 'MEDIUM', NULL),
                ('c7', 'CR-007/2024', 'Republic v. Peter Odhiambo', 'court-nakuru-mc', 'Nakuru Law Courts',
                 '2024-07-30', 'OPEN', 'Criminal', 'MEDIUM', 'Nakuru Town'),
                ('c8', 'TR-008/2024', 'Republic v. Unknown Driver', 'court-eldoret-mc', 'Eldoret Law Courts',
                 '2024-08-14', 'OPEN', 'Traffic', 'LOW', 'Eldoret-Iten Road')
            """);

            // Seed case participants
            stmt.execute("""
                INSERT INTO case_participant (participant_id, case_id, person_id, role_type, is_active)
                VALUES
                ('cp1', 'c1', 'p1', 'Accused', 1),
                ('cp2', 'c1', 'p6', 'Complainant', 1),
                ('cp3', 'c2', 'p2', 'Accused', 1),
                ('cp4', 'c3', 'p3', 'Accused', 1),
                ('cp5', 'c3', 'p4', 'Complainant', 1),
                ('cp6', 'c4', 'p5', 'Accused', 1),
                ('cp7', 'c5', 'p7', 'Accused', 1),
                ('cp8', 'c6', 'p8', 'Complainant', 1)
            """);

            // Seed charges with verdicts
            stmt.execute("""
                INSERT INTO charge (charge_id, case_id, accused_person_id, offense_code, particulars, plea, verdict, sentence_notes)
                VALUES
                ('ch1', 'c1', 'p1', 'Penal Code Sec 268', 'Common nuisance contrary to section 268', 'NOT_GUILTY', NULL, NULL),
                ('ch2', 'c2', 'p2', 'Traffic Act Sec 12', 'Exceeding speed limit in a built-up area', 'GUILTY', 'CONVICTED', 'Fine of Ksh 10,000'),
                ('ch3', 'c3', 'p3', 'Civil Suit', 'Breach of contract for sale of land LR No. 1234', NULL, 'JUDGEMENT_FOR_PLAINTIFF', 'Damages of Ksh 500,000 awarded'),
                ('ch4', 'c4', 'p5', 'Penal Code Sec 308', 'Assault causing bodily harm', 'NOT_GUILTY', NULL, NULL),
                ('ch5', 'c5', 'p7', 'Traffic Act Sec 44', 'Driving without a valid license', 'GUILTY', 'CONVICTED', 'Fine of Ksh 5,000 or 1 month imprisonment'),
                ('ch6', 'c7', 'p3', 'Penal Code Sec 275', 'Theft of property valued over Ksh 10,000', 'NOT_GUILTY', NULL, NULL)
            """);
        }
    }
}
