package com.robinhood.db;

import com.robinhood.model.GameResult;
import com.robinhood.util.TimeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Map;

public class GameResultLogger {
    public static void log(GameResult result) {
        if (result == null) return;

        // If this result represents a multi-player result without single email
        if (result.getEmail() == null && result.getPlayerRanks() != null) {
            for (Map.Entry<String, Integer> entry : result.getPlayerRanks().entrySet()) {
                String playerEmail = entry.getKey();
                int rank = entry.getValue();
                int xp = (result.getPlayerXP() != null) ? result.getPlayerXP().getOrDefault(playerEmail, 0) : 0;
                int tokens = 0;
                GameResult individual = new GameResult(result.getSessionId(), playerEmail, rank, xp, tokens);
                log(individual);
            }
            return;
        }

        String sql = "INSERT INTO game_results (session_id, email, rank, xp_awarded, tokens_won, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, result.getSessionId());
            ps.setString(2, result.getEmail());
            ps.setInt(3, result.getRank());
            ps.setInt(4, result.getXpAwarded());
            ps.setInt(5, result.getTokensWon());
            ps.setTimestamp(6, Timestamp.valueOf(result.getTimestamp() != null ? result.getTimestamp() : java.time.LocalDateTime.now()));
            ps.executeUpdate();
        } catch (Exception e) {
            // Silently log in demo mode
        }
    }

    public static void save(GameResult result) {
        log(result);
    }
}
