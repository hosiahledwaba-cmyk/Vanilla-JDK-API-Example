package com.robinhood.db.audit;

import com.robinhood.util.JSONParser;

import java.sql.*;
import java.util.*;

public class GameAuditLoggerImpl implements GameAuditLogger {
    private final Connection conn;

    public GameAuditLoggerImpl(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void logSessionStart(String gameType, String sessionCode, List<String> playerIds, Map<String, Object> boardConfig) {
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO game_sessions (game_type, session_code, player_ids, board_config) VALUES (?, ?, ?, ?)"
        )) {
            stmt.setString(1, gameType);
            stmt.setString(2, sessionCode);
            stmt.setString(3, playerIds != null ? String.join(",", playerIds) : "");
            stmt.setString(4, JSONParser.stringify(boardConfig));
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void logPlayerAction(String sessionCode, String playerId, String moveType, int roll, int fromTile, int toTile, double angle, double intensity) {
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO game_actions (session_code, player_id, move_type, dice_roll, from_tile, to_tile, angle, intensity) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            stmt.setString(1, sessionCode);
            stmt.setString(2, playerId);
            stmt.setString(3, moveType);
            stmt.setInt(4, roll);
            stmt.setInt(5, fromTile);
            stmt.setInt(6, toTile);
            stmt.setDouble(7, angle);
            stmt.setDouble(8, intensity);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @Override
    public void logSessionResult(String sessionCode, String winnerId, Map<String, Integer> xpMap, Map<String, Integer> tokenMap, int houseCut) {
        if (conn == null) return;
        try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO game_results (session_code, winner_id, xp_gain, token_distribution, house_cut) VALUES (?, ?, ?, ?, ?)"
        )) {
            stmt.setString(1, sessionCode);
            stmt.setString(2, winnerId);
            stmt.setString(3, JSONParser.stringify(xpMap));
            stmt.setString(4, JSONParser.stringify(tokenMap));
            stmt.setInt(5, houseCut);
            stmt.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
