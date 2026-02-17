package com.courttrack.model;

import java.time.LocalDate;
import java.util.UUID;

public class CourtCase {
    private String caseId;
    private String caseNumber;
    private String caseTitle;
    private String courtId;
    private LocalDate filingDate;
    private String caseStatus;
    private String caseCategory;
    private boolean isDeleted;

    // Transient fields populated by joined queries (not persisted directly on court_case)
    private String chargeParticulars;
    private String chargeVerdict;
    private String chargePlea;

    public CourtCase() {
        this.caseId = UUID.randomUUID().toString();
        this.filingDate = LocalDate.now();
        this.caseStatus = "OPEN";
    }

    public CourtCase(String caseId) {
        this.caseId = caseId;
    }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

    public String getCaseTitle() { return caseTitle; }
    public void setCaseTitle(String caseTitle) { this.caseTitle = caseTitle; }

    public String getCourtId() { return courtId; }
    public void setCourtId(String courtId) { this.courtId = courtId; }

    public LocalDate getFilingDate() { return filingDate; }
    public void setFilingDate(LocalDate filingDate) { this.filingDate = filingDate; }

    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String caseStatus) { this.caseStatus = caseStatus; }

    public String getCaseCategory() { return caseCategory; }
    public void setCaseCategory(String caseCategory) { this.caseCategory = caseCategory; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getChargeParticulars() { return chargeParticulars; }
    public void setChargeParticulars(String chargeParticulars) { this.chargeParticulars = chargeParticulars; }

    public String getChargeVerdict() { return chargeVerdict; }
    public void setChargeVerdict(String chargeVerdict) { this.chargeVerdict = chargeVerdict; }

    public String getChargePlea() { return chargePlea; }
    public void setChargePlea(String chargePlea) { this.chargePlea = chargePlea; }
}
