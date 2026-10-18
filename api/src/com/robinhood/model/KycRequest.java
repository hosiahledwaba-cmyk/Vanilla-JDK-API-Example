package com.robinhood.model;

import java.time.LocalDateTime;

public class KycRequest {
    private int id;
    private String email;
    private String fullName;
    private String documentType;
    private String documentUrl;
    private String providerRequestId;
    private String status = "pending";
    private LocalDateTime submittedAt = LocalDateTime.now();
    private LocalDateTime processedAt;
    private String adminNote;

    public KycRequest() {}

    public KycRequest(int id, String email, String fullName, String documentType, String documentUrl,
                      String providerRequestId, String status, LocalDateTime submittedAt, LocalDateTime processedAt) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.documentType = documentType;
        this.documentUrl = documentUrl;
        this.providerRequestId = providerRequestId;
        this.status = status != null ? status : "pending";
        this.submittedAt = submittedAt != null ? submittedAt : LocalDateTime.now();
        this.processedAt = processedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getProviderRequestId() { return providerRequestId; }
    public void setProviderRequestId(String providerRequestId) { this.providerRequestId = providerRequestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
}
