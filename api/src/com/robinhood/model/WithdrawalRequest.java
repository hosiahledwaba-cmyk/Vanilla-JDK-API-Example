package com.robinhood.model;

import java.time.LocalDateTime;

public class WithdrawalRequest {
    private String email;
    private int amount;
    private String method; // e.g., 'bank', 'paypal'
    private String details; // JSON or raw text
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;

    // Constructor + Getters + Setters
}
