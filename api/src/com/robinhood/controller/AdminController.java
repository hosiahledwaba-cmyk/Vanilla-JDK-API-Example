package com.robinhood.controller;

import com.robinhood.db.AdminStatsManager;
import com.robinhood.security.AdminAuthManager;
import com.robinhood.util.JSONParser;
import com.robinhood.util.Logger;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;

public class AdminController {

    private static boolean auth(String token) {
        return AdminAuthManager.isAdminToken(token);
    }

    private static String role(String token) {
        return AdminAuthManager.getRole(token).name();
    }

    // GET /admin/stats
    public static String stats(String token) {
        if (!auth(token)) return forbidden();
        try {
            Map<String, Object> m = Map.of(
                    "activeSessions", AdminStatsManager.getActiveSessions(),
                    "activeConnections", AdminStatsManager.getActiveConnections(),
                    "houseEarnings", AdminStatsManager.getHouseEarnings(),
                    "pendingWithdrawals", AdminStatsManager.getPendingWithdrawals()
            );
            Logger.admin(role(token), "viewStats", null);
            return ResponseBuilder.json(200, JSONParser.stringify(m));
        } catch (Exception e) {
            Logger.admin(role(token), "viewStats", "error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error fetching stats");
        }
    }

    // GET /admin/users
    public static String listUsers(String token) {
        if (!auth(token)) return forbidden();
        try {
            var users = AdminStatsManager.listAllUsers();
            Logger.admin(role(token), "listUsers", null);
            return ResponseBuilder.json(200, JSONParser.stringify(users));
        } catch (Exception e) {
            Logger.admin(role(token), "listUsers", "error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error listing users");
        }
    }

    // GET /admin/user?email=...
    public static String userDetails(String token, Map<String, String> query) {
        if (!auth(token)) return forbidden();
        String email = query.get("email");
        if (email == null) return badRequest("Missing email");
        try {
            var details = AdminStatsManager.getUserDetails(email);
            Logger.admin(role(token), "userDetails", "email=" + email);
            return ResponseBuilder.json(200, JSONParser.stringify(details));
        } catch (Exception e) {
            Logger.admin(role(token), "userDetails", "email=" + email + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error fetching user details");
        }
    }

    // GET /admin/withdrawals?status=pending|approved|rejected
    public static String withdrawals(String token, Map<String, String> query) {
        if (!auth(token)) return forbidden();
        String status = query.get("status");
        try {
            var list = AdminStatsManager.listWithdrawals(status);
            Logger.admin(role(token), "listWithdrawals", "status=" + status);
            return ResponseBuilder.json(200, JSONParser.stringify(list));
        } catch (Exception e) {
            Logger.admin(role(token), "listWithdrawals", "status=" + status + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error listing withdrawals");
        }
    }

    // POST /admin/withdrawals/approve
    public static String approveWithdrawal(String token, String body) {
        if (!auth(token)) return forbidden();
        int id = Integer.parseInt(JSONParser.parse(body).get("id"));
        try {
            boolean ok = AdminStatsManager.approveWithdrawal(id);
            Logger.admin(role(token), "approveWithdrawal", "id=" + id);
            return ok ? success("approved") : notFound();
        } catch (Exception e) {
            Logger.admin(role(token), "approveWithdrawal", "id=" + id + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error approving withdrawal");
        }
    }

    // POST /admin/withdrawals/reject
    public static String rejectWithdrawal(String token, String body) {
        if (!auth(token)) return forbidden();
        int id = Integer.parseInt(JSONParser.parse(body).get("id"));
        try {
            boolean ok = AdminStatsManager.rejectWithdrawal(id);
            Logger.admin(role(token), "rejectWithdrawal", "id=" + id);
            return ok ? success("rejected") : notFound();
        } catch (Exception e) {
            Logger.admin(role(token), "rejectWithdrawal", "id=" + id + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error rejecting withdrawal");
        }
    }

    // GET /admin/session/history?sessionId=...
    public static String sessionHistory(String token, Map<String, String> query) {
        if (!auth(token)) return forbidden();
        String sid = query.get("sessionId");
        if (sid == null) return badRequest("Missing sessionId");
        try {
            var hist = AdminStatsManager.getSessionHistory(sid);
            Logger.admin(role(token), "sessionHistory", "sessionId=" + sid);
            return ResponseBuilder.json(200, JSONParser.stringify(hist));
        } catch (Exception e) {
            Logger.admin(role(token), "sessionHistory", "sessionId=" + sid + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error fetching session history");
        }
    }

    // GET /admin/sessions
    public static String listSessions(String token) {
        if (!auth(token)) return forbidden();
        try {
            var sessions = AdminStatsManager.listAllSessions();
            Logger.admin(role(token), "listSessions", null);
            return ResponseBuilder.json(200, JSONParser.stringify(sessions));
        } catch (Exception e) {
            Logger.admin(role(token), "listSessions", "error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error listing sessions");
        }
    }

    // POST /admin/kill  {sessionId: "..."}
    public static String killSession(String token, String body) {
        if (!auth(token)) return forbidden();
        String sid = JSONParser.parse(body).get("sessionId");
        if (sid == null) return badRequest("Missing sessionId");
        try {
            boolean ok = AdminStatsManager.killSession(sid);
            Logger.admin(role(token), "killSession", "sessionId=" + sid);
            return ok ? success("killed") : notFound();
        } catch (Exception e) {
            Logger.admin(role(token), "killSession", "sessionId=" + sid + ",error=" + e.getMessage());
            return ResponseBuilder.json(500, "Error killing session");
        }
    }

    // POST /admin/shutdown
    public static String shutdown(String token) {
        if (!auth(token)) return forbidden();
        Logger.admin(role(token), "shutdown", null);
        new Thread(() -> System.exit(0)).start();
        return ResponseBuilder.json(200, "{\"shutdown\":true}");
    }

    // Standard JSON helpers
    private static String forbidden() {
        return ResponseBuilder.json(403, "Forbidden");
    }

    private static String notFound() {
        return ResponseBuilder.json(404, "Not found");
    }

    private static String success(String key) {
        return ResponseBuilder.json(200, "{\"" + key + "\":true}");
    }

    private static String badRequest(String msg) {
        return ResponseBuilder.json(400, msg);
    }
}
