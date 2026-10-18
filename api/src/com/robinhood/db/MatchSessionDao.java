package com.robinhood.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MatchSessionDao {

    public static class MatchRecord {
        public String matchId;
        public String roomId;
        public String gameType;
        public String khasinoMode;
        public int maxPlayers;
        public int wagerAmount;
        public String status;
        public String currentTurn;
        public int stateVersion;
        public String winnerId;
        public Integer winnerTeam;
        public String forfeitBy;
        public String forfeitReason;
        public String outcome;
        public Timestamp startedAt;
        public Timestamp completedAt;
    }

    public static class GameResultRecord {
        public String matchId;
        public String playerId;
        public String gameType;
        public boolean isWinner;
        public boolean isForfeit;
        public int score;
        public String outcome;
        public Timestamp createdAt;
    }

    public static void createMatch(String matchId, String roomId, String gameType, String khasinoMode,
                                   int maxPlayers, int wager, String currentTurn) {
        String sql = "INSERT INTO match_sessions (match_id, room_id, game_type, khasino_mode, max_players, " +
                     "wager_amount, status, current_turn, state_version) VALUES (?, ?, ?, ?, ?, ?, 'IN_PROGRESS', ?, 1) " +
                     "ON CONFLICT (match_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, roomId);
            ps.setString(3, gameType);
            ps.setString(4, khasinoMode);
            ps.setInt(5, maxPlayers);
            ps.setInt(6, wager);
            ps.setString(7, currentTurn);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] createMatch error: " + e.getMessage());
        }
    }

    public static void updateTurn(String matchId, String currentTurn, int stateVersion) {
        String sql = "UPDATE match_sessions SET current_turn = ?, state_version = ?, updated_at = CURRENT_TIMESTAMP WHERE match_id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentTurn);
            ps.setInt(2, stateVersion);
            ps.setString(3, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] updateTurn error: " + e.getMessage());
        }
    }

    public static void finalizeMatch(String matchId, String status, String winnerId, Integer winnerTeam,
                                    String forfeitBy, String forfeitReason, String outcome) {
        String sql = "UPDATE match_sessions SET status = ?, winner_id = ?, winner_team = ?, forfeit_by = ?, " +
                     "forfeit_reason = ?, outcome = ?, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE match_id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, winnerId);
            if (winnerTeam != null) ps.setInt(3, winnerTeam); else ps.setNull(3, Types.INTEGER);
            ps.setString(4, forfeitBy);
            ps.setString(5, forfeitReason);
            ps.setString(6, outcome);
            ps.setString(7, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] finalizeMatch error: " + e.getMessage());
        }
    }

    public static void saveGameState(String matchId, int version, String stateJson) {
        String sql = "INSERT INTO game_states (match_id, version, state_json, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (match_id) DO UPDATE SET version = EXCLUDED.version, state_json = EXCLUDED.state_json, updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setInt(2, version);
            ps.setString(3, stateJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] saveGameState error: " + e.getMessage());
        }
    }

    public static String getGameState(String matchId) {
        String sql = "SELECT state_json FROM game_states WHERE match_id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("state_json");
            }
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] getGameState error: " + e.getMessage());
        }
        return null;
    }

    public static void logAction(String matchId, String playerId, String actionType, String payloadJson, int seq, int stateVersion) {
        String sql = "INSERT INTO game_actions (match_id, player_id, action_type, payload_json, sequence_num, state_version) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, playerId);
            ps.setString(3, actionType);
            ps.setString(4, payloadJson != null ? payloadJson : "{}");
            ps.setInt(5, seq);
            ps.setInt(6, stateVersion);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] logAction error: " + e.getMessage());
        }
    }

    public static void recordResult(String matchId, String playerId, String gameType, boolean isWinner,
                                    boolean isForfeit, int score, String outcome, String detailsJson) {
        String sql = "INSERT INTO match_results (match_id, player_id, game_type, is_winner, is_forfeit, final_score, outcome, details_json) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, playerId);
            ps.setString(3, gameType);
            ps.setBoolean(4, isWinner);
            ps.setBoolean(5, isForfeit);
            ps.setInt(6, score);
            ps.setString(7, outcome != null ? outcome : (isWinner ? "VICTORY" : "DEFEAT"));
            ps.setString(8, detailsJson != null ? detailsJson : "{}");
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] recordResult error: " + e.getMessage());
        }
    }

    public static List<GameResultRecord> getPlayerHistory(String playerId) {
        List<GameResultRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM match_results WHERE player_id = ? ORDER BY created_at DESC LIMIT 25";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GameResultRecord r = new GameResultRecord();
                    r.matchId = rs.getString("match_id");
                    r.playerId = rs.getString("player_id");
                    r.gameType = rs.getString("game_type");
                    r.isWinner = rs.getBoolean("is_winner");
                    r.isForfeit = rs.getBoolean("is_forfeit");
                    r.score = rs.getInt("final_score");
                    r.outcome = rs.getString("outcome");
                    r.createdAt = rs.getTimestamp("created_at");
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            System.err.println("[MatchSessionDao] getPlayerHistory error: " + e.getMessage());
        }
        return list;
    }
}
