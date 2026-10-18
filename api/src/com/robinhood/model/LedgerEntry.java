package com.robinhood.model;

import java.time.LocalDateTime;

public class LedgerEntry {
    public enum Type { DEPOSIT, WAGER, WIN, RAKE, WITHDRAWAL }

    private final String email;
    private final Type type;
    private final int amount;
    private final String note;
    private final LocalDateTime timestamp;

    public LedgerEntry(String email, Type type, int amount, String note) {
        this.email = email;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.timestamp = LocalDateTime.now();
    }

    public LedgerEntry(String email, Type type, int amount, String note, LocalDateTime timestamp) {
        this.email = email;
        this.type = type;
        this.amount = amount;
        this.note = note;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
    }

    public String getEmail() { return email; }
    public Type getType() { return type; }
    public int getAmount() { return amount; }
    public String getNote() { return note; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
