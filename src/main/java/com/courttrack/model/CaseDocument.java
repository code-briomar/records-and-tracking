package com.courttrack.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class CaseDocument {
    private String documentId;
    private String caseId;
    private String name;
    private String mimeType;
    private String localPath;
    private long fileSize;
    private LocalDateTime uploadDate;
    private String uploadedBy;

    public CaseDocument() {
        this.documentId = UUID.randomUUID().toString();
        this.uploadDate = LocalDateTime.now();
    }

    public CaseDocument(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadDate() { return uploadDate; }
    public void setUploadDate(LocalDateTime uploadDate) { this.uploadDate = uploadDate; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
