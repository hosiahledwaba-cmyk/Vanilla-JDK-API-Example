package com.robinhood.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<String, String> tokenToEmail = new ConcurrentHashMap<>();

    public static void addSession(String token, String email) {
        tokenToEmail.put(token, email);
    }

    public static boolean isValid(String token) {
        return token != null && tokenToEmail.containsKey(token);
    }

    public static String getUserEmail(String token) {
        return tokenToEmail.get(token);
    }

    public static void remove(String token) {
        tokenToEmail.remove(token);
    }
}
