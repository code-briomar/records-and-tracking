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
                    filing_date TEXT NOT NULL,
                    case_status TEXT NOT NULL DEFAULT 'OPEN',
                    case_category TEXT NOT NULL,
                    is_deleted INTEGER DEFAULT 0,
                    created_at TEXT DEFAULT (datetime('now')),
                    updated_at TEXT DEFAULT (datetime('now'))
                )
            """);

            // Migrate: add case_title column if missing (for existing databases)
            try {
                stmt.execute("ALTER TABLE court_case ADD COLUMN case_title TEXT");
            } catch (SQLException ignored) {
                // Column already exists
            }

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
                    FOREIGN KEY (case_id) REFERENCES court_case(case_id),
                    FOREIGN KEY (accused_person_id) REFERENCES person(person_id)
                )
            """);

            seedDataIfEmpty(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void seedDataIfEmpty(Connection conn) throws SQLException {
        var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM person");
        rs.next();
        if (rs.getInt(1) > 0) return;

        try (Statement stmt = conn.createStatement()) {
            // Seed persons
            stmt.execute("""
                INSERT INTO person (person_id, national_id, first_name, last_name, gender, dob, phone_number, email)
                VALUES
                ('p1', '12345678', 'John', 'Kamau', 'Male', '1990-03-15', '0712345678', 'jkamau@email.com'),
                ('p2', '23456789', 'Jane', 'Wanjiku', 'Female', '1985-07-22', '0723456789', 'jwanjiku@email.com'),
                ('p3', '34567890', 'Peter', 'Odhiambo', 'Male', '1992-11-08', '0734567890', 'podhiambo@email.com'),
                ('p4', '45678901', 'Mary', 'Akinyi', 'Female', '1988-01-30', '0745678901', 'makinyi@email.com'),
                ('p5', '56789012', 'Samuel', 'Kipchoge', 'Male', '1995-06-12', '0756789012', 'skipchoge@email.com'),
                ('p6', '67890123', 'Grace', 'Muthoni', 'Female', '1991-09-25', '0767890123', 'gmuthoni@email.com'),
                ('p7', '78901234', 'David', 'Wanyama', 'Male', '1987-04-18', '0778901234', 'dwanyama@email.com'),
                ('p8', '89012345', 'Faith', 'Njeri', 'Female', '1993-12-05', '0789012345', 'fnjeri@email.com')
            """);

            // Seed cases with titles
            stmt.execute("""
                INSERT INTO court_case (case_id, case_number, case_title, court_id, filing_date, case_status, case_category)
                VALUES
                ('c1', 'CR-001/2024', 'Republic v. John Kamau', 'NAIROBI-MC', '2024-01-15', 'OPEN', 'Criminal'),
                ('c2', 'TR-002/2024', 'Republic v. Jane Wanjiku', 'NAIROBI-MC', '2024-02-20', 'OPEN', 'Traffic'),
                ('c3', 'CV-003/2024', 'Odhiambo v. Akinyi', 'NAIROBI-HC', '2024-03-10', 'CLOSED', 'Civil'),
                ('c4', 'CR-004/2024', 'Republic v. Samuel Kipchoge', 'MOMBASA-MC', '2024-04-05', 'OPEN', 'Criminal'),
                ('c5', 'TR-005/2024', 'Republic v. David Wanyama', 'KISUMU-MC', '2024-05-18', 'CLOSED', 'Traffic'),
                ('c6', 'CV-006/2024', 'Njeri v. Muthoni & Others', 'NAIROBI-HC', '2024-06-22', 'OPEN', 'Civil'),
                ('c7', 'CR-007/2024', 'Republic v. Peter Odhiambo', 'NAKURU-MC', '2024-07-30', 'OPEN', 'Criminal'),
                ('c8', 'TR-008/2024', 'Republic v. Unknown Driver', 'ELDORET-MC', '2024-08-14', 'OPEN', 'Traffic')
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
