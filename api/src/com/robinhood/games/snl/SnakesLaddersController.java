package com.robinhood.games.snl;

import com.robinhood.db.audit.GameAuditLogger;
import com.robinhood.db.audit.GameAuditLoggerImpl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

public class SnakesLaddersController {

    private final SNLSessionManager sessionManager;

    public SnakesLaddersController() throws Exception {
        // Setup DB connection + audit logger (better to use a pool in prod)
        Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/robinhood", "user", "password");
        GameAuditLogger auditLogger = new GameAuditLoggerImpl(conn);
        sessionManager = SNLSessionManager.getInstance(auditLogger);
    }

    // Example method signature for request handler
    public Map<String, Object> createGame(Map<String, Object> requestBody) {
        String playerId = (String) requestBody.get("playerId");
        int wager = (int) requestBody.get("wagerTokens");
        int maxPlayers = (int) requestBody.getOrDefault("maxPlayers", 3);
        String sessionId = sessionManager.createSession(playerId, wager, maxPlayers);
        return Map.of("sessionId", sessionId);
    }

    public Map<String, Object> joinGame(Map<String, Object> requestBody) {
        String playerId = (String) requestBody.get("playerId");
        String sessionId = (String) requestBody.get("sessionId");
        boolean joined = sessionManager.joinSession(sessionId, playerId);
        return Map.of("joined", joined);
    }

    public Map<String, Object> makeMove(Map<String, Object> requestBody) {
        String playerId = (String) requestBody.get("playerId");
        String sessionId = (String) requestBody.get("sessionId");
        int diceRoll = (int) requestBody.get("diceRoll");
        double angle = (double) requestBody.get("angle");
        double intensity = (double) requestBody.get("intensity");

        SNLGameSession session = sessionManager.getSession(sessionId);
        if (session == null) return Map.of("error", "Session not found");

        boolean valid = session.makeMove(playerId, diceRoll, angle, intensity);
        return Map.of("moveAccepted", valid);
    }

    public Map<String, Object> getState(Map<String, Object> requestBody) {
        String sessionId = (String) requestBody.get("sessionId");
        SNLGameSession session = sessionManager.getSession(sessionId);
        if (session == null) return Map.of("error", "Session not found");
        return session.getGameState();
    }
}
