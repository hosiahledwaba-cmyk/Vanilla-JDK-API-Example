package com.robinhood.security;

import com.robinhood.model.User;

import java.util.Base64;
import java.util.UUID;

public class AuthManager {
    public static String generateToken(User user) {
        String raw = user.getEmail() + ":" + UUID.randomUUID();
        String token = Base64.getEncoder().encodeToString(raw.getBytes());
        SessionManager.addSession(token, user.getEmail());
        return token;
    }

    public static boolean validateToken(String token) {
        return SessionManager.isValid(token);
    }

    public static String getEmailFromToken(String token) {
        return SessionManager.getUserEmail(token);
    }
}
