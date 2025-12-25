package com.robinhood.model;

public class Wallet {
    private final String email;
    private int balance;

    public Wallet(String email, int initialTokens) {
        this.email = email;
        this.balance = initialTokens;
    }

    public String getEmail() {
        return email;
    }

    public int getBalance() {
        return balance;
    }

    public boolean deduct(int tokens) {
        if (balance >= tokens) {
            balance -= tokens;
            return true;
        }
        return false;
    }

    public void deposit(int tokens) {
        balance += tokens;
    }
}
