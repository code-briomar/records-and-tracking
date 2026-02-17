package com.courttrack.model;

import java.util.UUID;

public class CaseParticipant {
    private String participantId;
    private String caseId;
    private String personId;
    private String roleType;
    private boolean isActive;

    public CaseParticipant() {
        this.participantId = UUID.randomUUID().toString();
        this.isActive = true;
    }

    public String getParticipantId() { return participantId; }
    public void setParticipantId(String participantId) { this.participantId = participantId; }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }

    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
