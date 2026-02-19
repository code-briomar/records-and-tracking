package com.courttrack.db;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_DIR = System.getProperty("user.home") + "/.courttrack";
    private static final String DB_NAME = "court_records";
    private static final String DB_URL = "jdbc:h2:file:" + DB_DIR + "/" + DB_NAME + ";MODE=MySQL;AUTO_SERVER=TRUE";
    private static DatabaseManager instance;

    static {
        try {
            Files.createDirectories(Path.of(DB_DIR));
            Class.forName("org.h2.Driver");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize H2 database", e);
        }
    }

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
            // --- Core tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS court (
                    court_id VARCHAR(255) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    location VARCHAR(255),
                    email VARCHAR(255),
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    created_by VARCHAR(255)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS person (
                    person_id VARCHAR(255) PRIMARY KEY,
                    national_id VARCHAR(255) UNIQUE,
                    first_name VARCHAR(255) NOT NULL,
                    last_name VARCHAR(255) NOT NULL,
                    other_names VARCHAR(255),
                    gender VARCHAR(50),
                    dob DATE,
                    phone_number VARCHAR(50),
                    email VARCHAR(255),
                    photo_local_uri VARCHAR(500),
                    is_deleted BOOLEAN DEFAULT FALSE,
                    alias VARCHAR(255),
                    nationality VARCHAR(100),
                    marital_status VARCHAR(50),
                    occupation VARCHAR(200),
                    address VARCHAR(500),
                    first_offender BOOLEAN DEFAULT TRUE,
                    criminal_history TEXT,
                    known_associates TEXT,
                    arrest_date DATE,
                    arresting_officer VARCHAR(255),
                    place_of_arrest VARCHAR(255),
                    penalty TEXT,
                    notes TEXT,
                    eye_color VARCHAR(50),
                    hair_color VARCHAR(50),
                    emergency_contact_name VARCHAR(255),
                    emergency_contact_phone VARCHAR(50),
                    emergency_contact_relationship VARCHAR(100),
                    legal_representation VARCHAR(255),
                    medical_conditions TEXT,
                    risk_level VARCHAR(50),
                    distinguishing_marks TEXT,
                    type VARCHAR(50),
                    status VARCHAR(50),
                    facility VARCHAR(255),
                    offense_type VARCHAR(100),
                    is_new BOOLEAN DEFAULT TRUE,
                    has_changes BOOLEAN DEFAULT FALSE,
                    version INT DEFAULT 1,
                    last_synced_at TIMESTAMP,
                    sync_retry_count INT DEFAULT 0,
                    next_retry_at TIMESTAMP,
                    last_sync_error TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS court_case (
                    case_id VARCHAR(255) PRIMARY KEY,
                    case_number VARCHAR(100) UNIQUE NOT NULL,
                    case_title VARCHAR(500),
                    court_id VARCHAR(255),
                    court_name VARCHAR(255),
                    filing_date DATE NOT NULL,
                    case_status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
                    case_category VARCHAR(100) NOT NULL,
                    case_type VARCHAR(100),
                    priority VARCHAR(50),
                    description TEXT,
                    date_of_judgment DATE,
                    sentence TEXT,
                    mitigation_notes TEXT,
                    prosecution_counsel VARCHAR(255),
                    appeal_status VARCHAR(50),
                    location_of_offence VARCHAR(255),
                    evidence_summary TEXT,
                    hearing_dates TEXT,
                    court_assistant VARCHAR(255),
                    is_deleted BOOLEAN DEFAULT FALSE,
                    is_new BOOLEAN DEFAULT TRUE,
                    has_changes BOOLEAN DEFAULT FALSE,
                    version INT DEFAULT 1,
                    last_synced_at TIMESTAMP,
                    sync_retry_count INT DEFAULT 0,
                    next_retry_at TIMESTAMP,
                    last_sync_error TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS case_participant (
                    participant_id VARCHAR(255) PRIMARY KEY,
                    case_id VARCHAR(255) NOT NULL,
                    person_id VARCHAR(255) NOT NULL,
                    role_type VARCHAR(100) NOT NULL,
                    is_active BOOLEAN DEFAULT TRUE,
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id),
                    FOREIGN KEY (person_id) REFERENCES person(person_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS charge (
                    charge_id VARCHAR(255) PRIMARY KEY,
                    case_id VARCHAR(255) NOT NULL,
                    accused_person_id VARCHAR(255),
                    offense_code VARCHAR(100),
                    particulars TEXT,
                    plea VARCHAR(100),
                    verdict VARCHAR(100),
                    sentence_notes TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id),
                    FOREIGN KEY (accused_person_id) REFERENCES person(person_id)
                )
            """);

            // --- Auth & security tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    user_id VARCHAR(255) PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    full_name VARCHAR(255),
                    username VARCHAR(100),
                    password_hash VARCHAR(255),
                    salt VARCHAR(100),
                    court_id VARCHAR(255),
                    title VARCHAR(100),
                    professional_title VARCHAR(100),
                    role VARCHAR(50) NOT NULL DEFAULT 'CLERK',
                    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                    failed_login_attempts INT DEFAULT 0,
                    password_expiry_date DATE,
                    account_lock_timestamp TIMESTAMP,
                    require_password_change BOOLEAN DEFAULT FALSE,
                    last_login_date TIMESTAMP,
                    language VARCHAR(10) DEFAULT 'en',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (court_id) REFERENCES court(court_id)
                )
            """);

            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_court_email
                ON app_user(court_id, email)
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auth_token (
                    token_id VARCHAR(255) PRIMARY KEY,
                    token TEXT NOT NULL,
                    user_id VARCHAR(255) NOT NULL,
                    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP,
                    is_revoked BOOLEAN DEFAULT FALSE,
                    revoked_at TIMESTAMP,
                    last_used_at TIMESTAMP,
                    refresh_token TEXT,
                    refresh_expires_at TIMESTAMP,
                    device_info VARCHAR(500),
                    ip_address VARCHAR(50),
                    FOREIGN KEY (user_id) REFERENCES app_user(user_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS login_attempt (
                    attempt_id VARCHAR(255) PRIMARY KEY,
                    username VARCHAR(100),
                    user_id VARCHAR(255),
                    email VARCHAR(255),
                    status VARCHAR(50) NOT NULL,
                    failure_reason VARCHAR(255),
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    ip_address VARCHAR(50),
                    device_info VARCHAR(500)
                )
            """);

            // --- Document & audit tables ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS case_document (
                    document_id VARCHAR(255) PRIMARY KEY,
                    case_id VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    mime_type VARCHAR(100),
                    local_path VARCHAR(500),
                    file_size BIGINT DEFAULT 0,
                    upload_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    uploaded_by VARCHAR(255),
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    log_id VARCHAR(255) PRIMARY KEY,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    user_id VARCHAR(255),
                    username VARCHAR(100),
                    action VARCHAR(100) NOT NULL,
                    entity_type VARCHAR(100),
                    entity_id VARCHAR(255),
                    status VARCHAR(50),
                    details TEXT,
                    court_id VARCHAR(255),
                    ip_address VARCHAR(50),
                    device_info VARCHAR(500)
                )
            """);

            // --- Sync queue table ---

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    queue_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    entity_type VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(255) NOT NULL,
                    operation VARCHAR(50) NOT NULL,
                    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
                    depends_on_entity_type VARCHAR(100),
                    depends_on_entity_id VARCHAR(255),
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    started_at TIMESTAMP,
                    completed_at TIMESTAMP,
                    retry_count INT DEFAULT 0,
                    last_error TEXT,
                    next_retry_at TIMESTAMP,
                    court_id VARCHAR(255),
                    UNIQUE(entity_type, entity_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sync_stats (
                    sync_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    sync_type VARCHAR(50) NOT NULL,
                    direction VARCHAR(50) NOT NULL,
                    user_info TEXT,
                    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    completed_at TIMESTAMP,
                    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
                    offenders_synced INT DEFAULT 0,
                    cases_synced INT DEFAULT 0,
                    offenders_failed INT DEFAULT 0,
                    cases_failed INT DEFAULT 0,
                    data_size_bytes BIGINT DEFAULT 0,
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
        safeAddColumn(stmt, "court_case", "case_title", "VARCHAR(500)");
        safeAddColumn(stmt, "court_case", "court_name", "VARCHAR(255)");
        safeAddColumn(stmt, "court_case", "case_type", "VARCHAR(100)");
        safeAddColumn(stmt, "court_case", "priority", "VARCHAR(50)");
        safeAddColumn(stmt, "court_case", "description", "TEXT");
        safeAddColumn(stmt, "court_case", "date_of_judgment", "DATE");
        safeAddColumn(stmt, "court_case", "sentence", "TEXT");
        safeAddColumn(stmt, "court_case", "mitigation_notes", "TEXT");
        safeAddColumn(stmt, "court_case", "prosecution_counsel", "VARCHAR(255)");
        safeAddColumn(stmt, "court_case", "appeal_status", "VARCHAR(50)");
        safeAddColumn(stmt, "court_case", "location_of_offence", "VARCHAR(255)");
        safeAddColumn(stmt, "court_case", "evidence_summary", "TEXT");
        safeAddColumn(stmt, "court_case", "hearing_dates", "TEXT");
        safeAddColumn(stmt, "court_case", "court_assistant", "VARCHAR(255)");

        // person new columns
        safeAddColumn(stmt, "person", "alias", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "nationality", "VARCHAR(100)");
        safeAddColumn(stmt, "person", "marital_status", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "occupation", "VARCHAR(200)");
        safeAddColumn(stmt, "person", "address", "VARCHAR(500)");
        safeAddColumn(stmt, "person", "first_offender", "BOOLEAN DEFAULT TRUE");
        safeAddColumn(stmt, "person", "criminal_history", "TEXT");
        safeAddColumn(stmt, "person", "known_associates", "TEXT");
        safeAddColumn(stmt, "person", "arrest_date", "DATE");
        safeAddColumn(stmt, "person", "arresting_officer", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "place_of_arrest", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "penalty", "TEXT");
        safeAddColumn(stmt, "person", "notes", "TEXT");
        safeAddColumn(stmt, "person", "eye_color", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "hair_color", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "emergency_contact_name", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "emergency_contact_phone", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "emergency_contact_relationship", "VARCHAR(100)");
        safeAddColumn(stmt, "person", "legal_representation", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "medical_conditions", "TEXT");
        safeAddColumn(stmt, "person", "risk_level", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "distinguishing_marks", "TEXT");
        safeAddColumn(stmt, "person", "type", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "status", "VARCHAR(50)");
        safeAddColumn(stmt, "person", "facility", "VARCHAR(255)");
        safeAddColumn(stmt, "person", "offense_type", "VARCHAR(100)");

        // Sync columns for person table
        safeAddColumn(stmt, "person", "is_new", "BOOLEAN DEFAULT TRUE");
        safeAddColumn(stmt, "person", "has_changes", "BOOLEAN DEFAULT FALSE");
        safeAddColumn(stmt, "person", "version", "INT DEFAULT 1");
        safeAddColumn(stmt, "person", "last_synced_at", "TIMESTAMP");
        safeAddColumn(stmt, "person", "sync_retry_count", "INT DEFAULT 0");
        safeAddColumn(stmt, "person", "next_retry_at", "TIMESTAMP");
        safeAddColumn(stmt, "person", "last_sync_error", "TEXT");

        // Sync columns for court_case table
        safeAddColumn(stmt, "court_case", "is_new", "BOOLEAN DEFAULT TRUE");
        safeAddColumn(stmt, "court_case", "has_changes", "BOOLEAN DEFAULT FALSE");
        safeAddColumn(stmt, "court_case", "version", "INT DEFAULT 1");
        safeAddColumn(stmt, "court_case", "last_synced_at", "TIMESTAMP");
        safeAddColumn(stmt, "court_case", "sync_retry_count", "INT DEFAULT 0");
        safeAddColumn(stmt, "court_case", "next_retry_at", "TIMESTAMP");
        safeAddColumn(stmt, "court_case", "last_sync_error", "TEXT");

        // charge new columns
        safeAddColumn(stmt, "charge", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        safeAddColumn(stmt, "charge", "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
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
