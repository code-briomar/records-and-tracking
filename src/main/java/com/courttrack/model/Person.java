package com.courttrack.model;

import java.time.LocalDate;
import java.util.UUID;

public class Person {
    private String personId;
    private String nationalId;
    private String firstName;
    private String lastName;
    private String otherNames;
    private String gender;
    private LocalDate dob;
    private String phoneNumber;
    private String email;
    private String photoLocalUri;
    private boolean isDeleted;

    public Person() {
        this.personId = UUID.randomUUID().toString();
    }

    public Person(String personId) {
        this.personId = personId;
    }

    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getOtherNames() { return otherNames; }
    public void setOtherNames(String otherNames) { this.otherNames = otherNames; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public LocalDate getDob() { return dob; }
    public void setDob(LocalDate dob) { this.dob = dob; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhotoLocalUri() { return photoLocalUri; }
    public void setPhotoLocalUri(String photoLocalUri) { this.photoLocalUri = photoLocalUri; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (firstName != null) sb.append(firstName);
        if (lastName != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(lastName);
        }
        return sb.toString();
    }
}
