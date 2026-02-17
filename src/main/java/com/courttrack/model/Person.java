package com.courttrack.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // New fields from mobile Offender model
    private String alias;
    private String nationality;
    private String maritalStatus;
    private String occupation;
    private String address;
    private boolean firstOffender;
    private String criminalHistory;
    private String knownAssociates;
    private LocalDate arrestDate;
    private String arrestingOfficer;
    private String placeOfArrest;
    private String penalty;
    private String notes;
    private String eyeColor;
    private String hairColor;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelationship;
    private String legalRepresentation;
    private String medicalConditions;
    private String riskLevel;
    private String distinguishingMarks;
    private String type;
    private String status;
    private String facility;
    private String offenseType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Person() {
        this.personId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Person(String personId) {
        this.personId = personId;
    }

    // --- Getters and Setters ---

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

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isFirstOffender() { return firstOffender; }
    public void setFirstOffender(boolean firstOffender) { this.firstOffender = firstOffender; }

    public String getCriminalHistory() { return criminalHistory; }
    public void setCriminalHistory(String criminalHistory) { this.criminalHistory = criminalHistory; }

    public String getKnownAssociates() { return knownAssociates; }
    public void setKnownAssociates(String knownAssociates) { this.knownAssociates = knownAssociates; }

    public LocalDate getArrestDate() { return arrestDate; }
    public void setArrestDate(LocalDate arrestDate) { this.arrestDate = arrestDate; }

    public String getArrestingOfficer() { return arrestingOfficer; }
    public void setArrestingOfficer(String arrestingOfficer) { this.arrestingOfficer = arrestingOfficer; }

    public String getPlaceOfArrest() { return placeOfArrest; }
    public void setPlaceOfArrest(String placeOfArrest) { this.placeOfArrest = placeOfArrest; }

    public String getPenalty() { return penalty; }
    public void setPenalty(String penalty) { this.penalty = penalty; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getEyeColor() { return eyeColor; }
    public void setEyeColor(String eyeColor) { this.eyeColor = eyeColor; }

    public String getHairColor() { return hairColor; }
    public void setHairColor(String hairColor) { this.hairColor = hairColor; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

    public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
    public void setEmergencyContactRelationship(String emergencyContactRelationship) { this.emergencyContactRelationship = emergencyContactRelationship; }

    public String getLegalRepresentation() { return legalRepresentation; }
    public void setLegalRepresentation(String legalRepresentation) { this.legalRepresentation = legalRepresentation; }

    public String getMedicalConditions() { return medicalConditions; }
    public void setMedicalConditions(String medicalConditions) { this.medicalConditions = medicalConditions; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public String getDistinguishingMarks() { return distinguishingMarks; }
    public void setDistinguishingMarks(String distinguishingMarks) { this.distinguishingMarks = distinguishingMarks; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFacility() { return facility; }
    public void setFacility(String facility) { this.facility = facility; }

    public String getOffenseType() { return offenseType; }
    public void setOffenseType(String offenseType) { this.offenseType = offenseType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

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
