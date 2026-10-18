package com.robinhood.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, String> tokenToEmail = new ConcurrentHashMap<>();

    public static void addSession(String token, String email) {
        tokenToEmail.put(token, email);
    }

    public static boolean isValid(String token) {
        if (token == null || token.isBlank()) return false;
        if (tokenToEmail.containsKey(token)) return true;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx > 0) {
                String email = decoded.substring(0, idx);
                tokenToEmail.put(token, email);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static String getUserEmail(String token) {
        String email = tokenToEmail.get(token);
        if (email != null) return email;
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(token), java.nio.charset.StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx > 0) {
                email = decoded.substring(0, idx);
                tokenToEmail.put(token, email);
                return email;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void remove(String token) {
        tokenToEmail.remove(token);
    }
}
