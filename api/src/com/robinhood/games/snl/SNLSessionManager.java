package com.robinhood.games.snl;

import com.robinhood.engine.snakesandladders.SnakesAndLaddersGame;
import com.robinhood.db.audit.GameAuditLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SNLSessionManager {

    private static SNLSessionManager instance;

    // sessionId -> session object
    private final Map<String, SNLGameSession> sessions = new ConcurrentHashMap<>();

    private final GameAuditLogger auditLogger;

    private SNLSessionManager(GameAuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public static synchronized SNLSessionManager getInstance(GameAuditLogger auditLogger) {
        if (instance == null) {
            instance = new SNLSessionManager(auditLogger);
        }
        return instance;
    }

    public String createSession(String hostPlayerId, int wagerTokens, int maxPlayers) {
        String sessionId = UUID.randomUUID().toString();
        SNLGameSession session = new SNLGameSession(sessionId, wagerTokens, maxPlayers, auditLogger);
        session.addPlayer(hostPlayerId);
        sessions.put(sessionId, session);
        return sessionId;
    }

    public boolean joinSession(String sessionId, String playerId) {
        SNLGameSession session = sessions.get(sessionId);
        if (session == null) return false;
        return session.addPlayer(playerId);
    }

    public SNLGameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

}
