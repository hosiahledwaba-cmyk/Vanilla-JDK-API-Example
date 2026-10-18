package com.robinhood.model;

import java.time.LocalDateTime;

public class WithdrawalRequest {
    private int id;
    private String email;
    private int amount;
    private String method; // e.g., 'bank', 'paypal'
    private String details; // JSON or raw text
    private String status = "pending";
    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime processedAt;
    private String adminNote;

    public WithdrawalRequest() {}

    public WithdrawalRequest(String email, int amount, String method, String details) {
        this.email = email;
        this.amount = amount;
        this.method = method;
        this.details = details;
        this.status = "pending";
        this.requestedAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
}
