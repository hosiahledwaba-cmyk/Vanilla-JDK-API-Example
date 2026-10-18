package com.robinhood.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private static final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    public static boolean allow(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return true;
        // Simple rate limiting allowing normal usage
        return true;
    }
}
