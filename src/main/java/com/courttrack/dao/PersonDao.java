package com.courttrack.dao;

import com.courttrack.db.DatabaseManager;
import com.courttrack.model.Person;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PersonDao {
    private final DatabaseManager db = DatabaseManager.getInstance();

    public List<Person> findAll() {
        List<Person> list = new ArrayList<>();
        String sql = "SELECT * FROM person WHERE is_deleted = 0 ORDER BY last_name, first_name";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Person> findAllPaginated(int offset, int limit) {
        List<Person> list = new ArrayList<>();
        String sql = "SELECT * FROM person WHERE is_deleted = 0 ORDER BY last_name, first_name LIMIT ? OFFSET ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Person findById(String personId) {
        String sql = "SELECT * FROM person WHERE person_id = ? AND is_deleted = 0";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, personId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Person> search(String query) {
        List<Person> list = new ArrayList<>();
        String sql = """
            SELECT * FROM person WHERE is_deleted = 0
            AND (first_name LIKE ? OR last_name LIKE ? OR national_id LIKE ? OR phone_number LIKE ?)
            ORDER BY last_name, first_name
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void insert(Person p) {
        String sql = """
            INSERT INTO person (person_id, national_id, first_name, last_name, other_names, gender, dob, phone_number, email, photo_local_uri)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getPersonId());
            ps.setString(2, p.getNationalId());
            ps.setString(3, p.getFirstName());
            ps.setString(4, p.getLastName());
            ps.setString(5, p.getOtherNames());
            ps.setString(6, p.getGender());
            ps.setString(7, p.getDob() != null ? p.getDob().toString() : null);
            ps.setString(8, p.getPhoneNumber());
            ps.setString(9, p.getEmail());
            ps.setString(10, p.getPhotoLocalUri());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(Person p) {
        String sql = """
            UPDATE person SET national_id = ?, first_name = ?, last_name = ?, other_names = ?,
            gender = ?, dob = ?, phone_number = ?, email = ?, photo_local_uri = ?,
            updated_at = CURRENT_TIMESTAMP
            WHERE person_id = ?
        """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getNationalId());
            ps.setString(2, p.getFirstName());
            ps.setString(3, p.getLastName());
            ps.setString(4, p.getOtherNames());
            ps.setString(5, p.getGender());
            ps.setString(6, p.getDob() != null ? p.getDob().toString() : null);
            ps.setString(7, p.getPhoneNumber());
            ps.setString(8, p.getEmail());
            ps.setString(9, p.getPhotoLocalUri());
            ps.setString(10, p.getPersonId());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void softDelete(String personId) {
        String sql = "UPDATE person SET is_deleted = TRUE, updated_at = CURRENT_TIMESTAMP WHERE person_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, personId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM person WHERE is_deleted = 0";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Person mapRow(ResultSet rs) throws SQLException {
        Person p = new Person(rs.getString("person_id"));
        p.setNationalId(rs.getString("national_id"));
        p.setFirstName(rs.getString("first_name"));
        p.setLastName(rs.getString("last_name"));
        p.setOtherNames(rs.getString("other_names"));
        p.setGender(rs.getString("gender"));
        String dob = rs.getString("dob");
        if (dob != null && !dob.isEmpty()) {
            p.setDob(LocalDate.parse(dob));
        }
        p.setPhoneNumber(rs.getString("phone_number"));
        p.setEmail(rs.getString("email"));
        p.setPhotoLocalUri(rs.getString("photo_local_uri"));
        p.setDeleted(rs.getInt("is_deleted") == 1);
        return p;
    }
}
