package com.robinhood.security;

import com.robinhood.core.ConfigLoader;
import java.util.Set;

public class AdminAuthManager {
    // roles loaded from config: viewerToken, operatorToken, superAdminToken
    private static final String VIEWER = ConfigLoader.get("admin.viewer.token");
    private static final String OPERATOR = ConfigLoader.get("admin.operator.token");
    private static final String SUPER = ConfigLoader.get("admin.super.token");

    public enum Role { VIEWER, OPERATOR, SUPER }

    public static Role getRole(String token) {
        if (SUPER.equals(token))    return Role.SUPER;
        if (OPERATOR.equals(token)) return Role.OPERATOR;
        if (VIEWER.equals(token))   return Role.VIEWER;
        return null;
    }

    public static boolean require(String token, Role... allowed) {
        Role role = getRole(token);
        if (role == null) return false;
        for (Role r : allowed) if (r == role) return true;
        return false;
    }
}
