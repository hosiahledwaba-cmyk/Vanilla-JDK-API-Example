package com.robinhood.game;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public static GameSession createSession(int maxPlayers, int wagerAmount) {
        String sessionId = UUID.randomUUID().toString();
        GameSession session = new GameSession(sessionId, maxPlayers, wagerAmount);
        sessions.put(sessionId, session);
        return session;
    }

    public static GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public static void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
}
