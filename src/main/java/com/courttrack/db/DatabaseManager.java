package com.courttrack.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:court_records.db?journal_mode=WAL&busy_timeout=5000";
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
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA busy_timeout = 5000");

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
                    is_new INTEGER DEFAULT 1,
                    has_changes INTEGER DEFAULT 0,
                    version INTEGER DEFAULT 1,
                    last_synced_at TEXT,
                    sync_retry_count INTEGER DEFAULT 0,
                    next_retry_at TEXT,
                    last_sync_error TEXT,
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
                    is_new INTEGER DEFAULT 1,
                    has_changes INTEGER DEFAULT 0,
                    version INTEGER DEFAULT 1,
                    last_synced_at TEXT,
                    sync_retry_count INTEGER DEFAULT 0,
                    next_retry_at TEXT,
                    last_sync_error TEXT,
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

            // --- Sync queue table ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    queue_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'QUEUED',
                    depends_on_entity_type TEXT,
                    depends_on_entity_id TEXT,
                    timestamp TEXT DEFAULT (datetime('now')),
                    started_at TEXT,
                    completed_at TEXT,
                    retry_count INTEGER DEFAULT 0,
                    last_error TEXT,
                    next_retry_at TEXT,
                    court_id TEXT,
                    UNIQUE(entity_type, entity_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_stats (
                    sync_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sync_type TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    user_info TEXT,
                    started_at TEXT DEFAULT (datetime('now')),
                    completed_at TEXT,
                    status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                    offenders_synced INTEGER DEFAULT 0,
                    cases_synced INTEGER DEFAULT 0,
                    offenders_failed INTEGER DEFAULT 0,
                    cases_failed INTEGER DEFAULT 0,
                    data_size_bytes INTEGER DEFAULT 0,
                    error_message TEXT,
                    error_class TEXT
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

        // Sync columns for person table
        safeAddColumn(stmt, "person", "is_new", "INTEGER DEFAULT 1");
        safeAddColumn(stmt, "person", "has_changes", "INTEGER DEFAULT 0");
        safeAddColumn(stmt, "person", "version", "INTEGER DEFAULT 1");
        safeAddColumn(stmt, "person", "last_synced_at", "TEXT");
        safeAddColumn(stmt, "person", "sync_retry_count", "INTEGER DEFAULT 0");
        safeAddColumn(stmt, "person", "next_retry_at", "TEXT");
        safeAddColumn(stmt, "person", "last_sync_error", "TEXT");

        // Sync columns for court_case table
        safeAddColumn(stmt, "court_case", "is_new", "INTEGER DEFAULT 1");
        safeAddColumn(stmt, "court_case", "has_changes", "INTEGER DEFAULT 0");
        safeAddColumn(stmt, "court_case", "version", "INTEGER DEFAULT 1");
        safeAddColumn(stmt, "court_case", "last_synced_at", "TEXT");
        safeAddColumn(stmt, "court_case", "sync_retry_count", "INTEGER DEFAULT 0");
        safeAddColumn(stmt, "court_case", "next_retry_at", "TEXT");
        safeAddColumn(stmt, "court_case", "last_sync_error", "TEXT");

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
        var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM court");
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
        }
    }
}
