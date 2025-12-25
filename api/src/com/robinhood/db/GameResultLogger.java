package com.robinhood.db;

import com.robinhood.model.GameResult;
import com.robinhood.util.TimeUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

public class GameResultLogger {
    public static void log(GameResult result) {
        String sql = "INSERT INTO game_results (session_id, email, rank, xp_awarded, tokens_won, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, result.getSessionId());
            ps.setString(2, result.getEmail());
            ps.setInt(3, result.getRank());
            ps.setInt(4, result.getXpAwarded());
            ps.setInt(5, result.getTokensWon());
            ps.setTimestamp(6, Timestamp.valueOf(result.getTimestamp()));
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
