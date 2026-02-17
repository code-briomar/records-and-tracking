package com.courttrack.model;

import java.util.UUID;

public class Charge {
    private String chargeId;
    private String caseId;
    private String accusedPersonId;
    private String offenseCode;
    private String particulars;
    private String plea;
    private String verdict;
    private String sentenceNotes;

    public Charge() {
        this.chargeId = UUID.randomUUID().toString();
    }

    public String getChargeId() { return chargeId; }
    public void setChargeId(String chargeId) { this.chargeId = chargeId; }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getAccusedPersonId() { return accusedPersonId; }
    public void setAccusedPersonId(String accusedPersonId) { this.accusedPersonId = accusedPersonId; }

    public String getOffenseCode() { return offenseCode; }
    public void setOffenseCode(String offenseCode) { this.offenseCode = offenseCode; }

    public String getParticulars() { return particulars; }
    public void setParticulars(String particulars) { this.particulars = particulars; }

    public String getPlea() { return plea; }
    public void setPlea(String plea) { this.plea = plea; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getSentenceNotes() { return sentenceNotes; }
    public void setSentenceNotes(String sentenceNotes) { this.sentenceNotes = sentenceNotes; }
}
