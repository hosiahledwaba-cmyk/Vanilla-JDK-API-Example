package com.robinhood.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class User {
    private String email;
    private String passwordHash;

    public User(String email, String password) {
        this.email = email;
        this.passwordHash = hashPassword(password);
    }

    public User(String email, String passwordHash, boolean isHashed) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean checkPassword(String inputPassword) {
        return passwordHash.equals(hashPassword(inputPassword));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing error", e);
        }
    }
}
