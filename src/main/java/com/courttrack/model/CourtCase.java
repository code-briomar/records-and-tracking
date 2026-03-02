package com.courttrack.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class CourtCase {
    private String caseId;
    private String caseNumber;
    private String caseTitle;
    private String courtId;
    private String courtName;
    private LocalDate filingDate;
    private String caseStatus;
    private String caseCategory;
    private String caseType;
    private String priority;
    private String description;
    private LocalDate dateOfJudgment;
    private String sentence;
    private String mitigationNotes;
    private String prosecutionCounsel;
    private String appealStatus;
    private String locationOfOffence;
    private String evidenceSummary;
    private String hearingDates;
    private String courtAssistant;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Sync fields
    private boolean isNew = true;
    private boolean hasChanges = false;
    private int version = 1;
    private LocalDateTime lastSyncedAt;
    private int syncRetryCount = 0;
    private LocalDateTime nextRetryAt;
    private String lastSyncError;

    // Transient fields populated by joined queries (not persisted directly on court_case)
    private String chargeParticulars;
    private String chargeVerdict;
    private String chargePlea;
    private String sentenceNotes;

    public CourtCase() {
        this.caseId = UUID.randomUUID().toString();
        this.filingDate = LocalDate.now();
        this.caseStatus = "OPEN";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public CourtCase(String caseId) {
        this.caseId = caseId;
    }

    // --- Getters and Setters ---

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }

    public String getCourtId() { return courtId; }
    public void setCourtId(String courtId) { this.courtId = courtId; }

    public String getCourtName() { return courtName; }
    public void setCourtName(String courtName) { this.courtName = courtName; }

    public LocalDate getFilingDate() { return filingDate; }
    public void setFilingDate(LocalDate filingDate) { this.filingDate = filingDate; }

    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String caseStatus) { this.caseStatus = caseStatus; }

    public String getCaseCategory() { return caseCategory; }
    public void setCaseCategory(String caseCategory) { this.caseCategory = caseCategory; }

    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDateOfJudgment() { return dateOfJudgment; }
    public void setDateOfJudgment(LocalDate dateOfJudgment) { this.dateOfJudgment = dateOfJudgment; }

    public String getSentence() { return sentence; }
    public void setSentence(String sentence) { this.sentence = sentence; }

    public String getMitigationNotes() { return mitigationNotes; }
    public void setMitigationNotes(String mitigationNotes) { this.mitigationNotes = mitigationNotes; }

    public String getProsecutionCounsel() { return prosecutionCounsel; }
    public void setProsecutionCounsel(String prosecutionCounsel) { this.prosecutionCounsel = prosecutionCounsel; }

    public String getAppealStatus() { return appealStatus; }
    public void setAppealStatus(String appealStatus) { this.appealStatus = appealStatus; }

    public String getLocationOfOffence() { return locationOfOffence; }
    public void setLocationOfOffence(String locationOfOffence) { this.locationOfOffence = locationOfOffence; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public String getHearingDates() { return hearingDates; }
    public void setHearingDates(String hearingDates) { this.hearingDates = hearingDates; }

    public String getCourtAssistant() { return courtAssistant; }
    public void setCourtAssistant(String courtAssistant) { this.courtAssistant = courtAssistant; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isNew() { return isNew; }
    public void setNew(boolean aNew) { isNew = aNew; }

    public boolean hasChanges() { return hasChanges; }
    public void setHasChanges(boolean hasChanges) { this.hasChanges = hasChanges; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public int getSyncRetryCount() { return syncRetryCount; }
    public void setSyncRetryCount(int syncRetryCount) { this.syncRetryCount = syncRetryCount; }

    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }

    public String getLastSyncError() { return lastSyncError; }
    public void setLastSyncError(String lastSyncError) { this.lastSyncError = lastSyncError; }

    // --- Transient charge fields ---

    public String getChargeParticulars() { return chargeParticulars; }
    public void setChargeParticulars(String chargeParticulars) { this.chargeParticulars = chargeParticulars; }

    public String getChargeVerdict() { return chargeVerdict; }
    public void setChargeVerdict(String chargeVerdict) { this.chargeVerdict = chargeVerdict; }

    public String getChargePlea() { return chargePlea; }
    public void setChargePlea(String chargePlea) { this.chargePlea = chargePlea; }

    public String getSentenceNotes() { return sentenceNotes; }
    public void setSentenceNotes(String sentenceNotes) { this.sentenceNotes = sentenceNotes; }
}
