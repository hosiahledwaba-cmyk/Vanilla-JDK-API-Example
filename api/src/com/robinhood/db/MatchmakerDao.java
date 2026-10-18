package com.robinhood.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MatchmakerDao {

    public static class QueueStatus {
        public String status; // WAITING, MATCHED, CANCELLED, NOT_QUEUED
        public String matchedRoomId;

        public QueueStatus(String status, String matchedRoomId) {
            this.status = status;
            this.matchedRoomId = matchedRoomId;
        }
    }

    public static synchronized void enqueue(String playerId, String gameType, String khasinoMode, int maxPlayers, int wager) {
        String sql = "INSERT INTO matchmaking_queue (player_id, game_type, khasino_mode, max_players, wager_amount, status, heartbeat_at) " +
                     "VALUES (?, ?, ?, ?, ?, 'WAITING', CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (player_id) DO UPDATE SET game_type = EXCLUDED.game_type, khasino_mode = EXCLUDED.khasino_mode, " +
                     "max_players = EXCLUDED.max_players, wager_amount = EXCLUDED.wager_amount, status = 'WAITING', " +
                     "matched_room_id = NULL, heartbeat_at = CURRENT_TIMESTAMP";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            ps.setString(2, gameType);
            ps.setString(3, khasinoMode);
            ps.setInt(4, maxPlayers);
            ps.setInt(5, wager);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchmakerDao] enqueue error: " + e.getMessage());
        }
    }

    public static synchronized void cancel(String playerId) {
        String sql = "DELETE FROM matchmaking_queue WHERE player_id = ? AND status = 'WAITING'";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MatchmakerDao] cancel error: " + e.getMessage());
        }
    }

    public static synchronized QueueStatus checkStatus(String playerId) {
        String sql = "SELECT status, matched_room_id FROM matchmaking_queue WHERE player_id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new QueueStatus(rs.getString("status"), rs.getString("matched_room_id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[MatchmakerDao] checkStatus error: " + e.getMessage());
        }
        return new QueueStatus("NOT_QUEUED", null);
    }

    /**
     * Atomically searches PostgreSQL for compatible waiting players.
     * If enough players are found, generates a matchedRoomId, updates all players to MATCHED,
     * and returns the list of matched player emails.
     */
    public static synchronized List<String> tryMatch(String gameType, String khasinoMode, int maxPlayers, int wager, String matchedRoomId) {
        List<String> matched = new ArrayList<>();
        String selectSql = "SELECT player_id FROM matchmaking_queue " +
                           "WHERE game_type = ? AND (khasino_mode = ? OR (? IS NULL AND khasino_mode IS NULL)) " +
                           "AND max_players = ? AND wager_amount = ? AND status = 'WAITING' " +
                           "ORDER BY created_at ASC LIMIT ? FOR UPDATE";

        String updateSql = "UPDATE matchmaking_queue SET status = 'MATCHED', matched_room_id = ? WHERE player_id = ?";

        try (Connection conn = DBManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                selectPs.setString(1, gameType);
                selectPs.setString(2, khasinoMode);
                selectPs.setString(3, khasinoMode);
                selectPs.setInt(4, maxPlayers);
                selectPs.setInt(5, wager);
                selectPs.setInt(6, maxPlayers);

                try (ResultSet rs = selectPs.executeQuery()) {
                    while (rs.next()) {
                        matched.add(rs.getString("player_id"));
                    }
                }

                if (matched.size() == maxPlayers) {
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        for (String pId : matched) {
                            updatePs.setString(1, matchedRoomId);
                            updatePs.setString(2, pId);
                            updatePs.addBatch();
                        }
                        updatePs.executeBatch();
                    }
                    conn.commit();
                    return matched;
                } else {
                    conn.rollback();
                    return Collections.emptyList();
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[MatchmakerDao] tryMatch error: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public static int getQueueCount() {
        String sql = "SELECT count(*) FROM matchmaking_queue WHERE status = 'WAITING'";
        try (Connection conn = DBManager.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        return 0;
    }
}
