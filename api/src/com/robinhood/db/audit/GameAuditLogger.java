package com.robinhood.db.audit;

import java.util.List;
import java.util.Map;

public interface GameAuditLogger {
    void logSessionStart(String gameType, String sessionCode, List<String> playerIds, Map<String, Object> boardConfig);
    void logPlayerAction(String sessionCode, String playerId, String moveType, int roll, int fromTile, int toTile, double angle, double intensity);
    void logSessionResult(String sessionCode, String winnerId, Map<String, Integer> xpMap, Map<String, Integer> tokenMap, int houseCut);
}
