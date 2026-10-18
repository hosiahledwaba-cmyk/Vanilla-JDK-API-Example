package com.robinhood.game;

import com.robinhood.realtime.RealTimeDispatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private static final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public static GameSession createSession(int maxPlayers) {
        return createSession(maxPlayers, 50);
    }

    public static GameSession createSession(int maxPlayers, int wagerAmount) {
        String sessionId = UUID.randomUUID().toString();
        GameSession session = new GameSession(sessionId, maxPlayers, wagerAmount);
        sessions.put(sessionId, session);
        return session;
    }

    public static GameSession getSession(String sessionId) {
        if (sessionId == null) return null;
        return sessions.get(sessionId);
    }

    public static void removeSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public static Collection<GameSession> getAllSessions() {
        return sessions.values();
    }

    public static int getActiveSessionCount() {
        return sessions.size();
    }

    public static int getActiveConnectionCount() {
        return RealTimeDispatcher.getActiveConnectionCount();
    }

    public static List<Map<String, Object>> listActiveSessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GameSession s : sessions.values()) {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", s.getSessionId());
            m.put("started", s.isStarted());
            m.put("players", s.getPlayers().stream().map(Player::getEmail).toList());
            list.add(m);
        }
        return list;
    }
}
