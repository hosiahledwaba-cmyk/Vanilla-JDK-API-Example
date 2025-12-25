package com.robinhood.db;

import com.robinhood.game.GameManager;

import java.sql.*;
import java.util.*;

public class AdminStatsManager {

    // --- Core Metrics ---

    public static int getActiveSessions() {
        return GameManager.getActiveSessionCount();
    }

    public static int getActiveConnections() {
        return GameManager.getActiveConnectionCount(); // This must be implemented in GameManager
    }

    public static int getHouseEarnings() {
        String sql = "SELECT SUM(amount) AS total FROM ledger WHERE type = 'RAKE'";
        try (Connection c = DBManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("total");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static int getPendingWithdrawals() {
        String sql = "SELECT COUNT(*) AS count FROM withdrawal_requests WHERE status = 'pending'";
        try (Connection c = DBManager.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("count");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // --- User & Wallet Management ---

    public static List<Map<String, Object>> listAllUsers() throws SQLException {
        String sql = "SELECT email, created_at FROM users";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                out.add(Map.of(
                        "email", rs.getString("email"),
                        "joined", rs.getTimestamp("created_at").toString()
                ));
            }
        }
        return out;
    }

    public static Map<String, Object> getUserDetails(String email) throws SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("balance", getWalletBalance(email));
        m.put("totalXP", getTotalXP(email));
        m.put("gamesPlayed", getGamesPlayed(email));
        m.put("wins", getWins(email));
        m.put("pendingWithdrawals", countWithdrawals(email, "pending"));
        m.put("totalWithdrawals", countWithdrawals(email, null));
        return m;
    }

    // --- Withdrawals ---

    public static List<Map<String, Object>> listWithdrawals(String status) throws SQLException {
        String sql = "SELECT * FROM withdrawal_requests" +
                (status != null ? " WHERE status=?" : "");
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            if (status != null) p.setString(1, status);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(Map.of(
                        "id", rs.getInt("id"),
                        "email", rs.getString("email"),
                        "amount", rs.getInt("amount"),
                        "method", rs.getString("method"),
                        "status", rs.getString("status"),
                        "requestedAt", rs.getTimestamp("requested_at").toString()
                ));
            }
        }
        return out;
    }

    public static boolean approveWithdrawal(int id) throws SQLException {
        return updateWithdrawalStatus(id, "approved");
    }

    public static boolean rejectWithdrawal(int id) throws SQLException {
        return updateWithdrawalStatus(id, "rejected");
    }

    private static boolean updateWithdrawalStatus(int id, String status) throws SQLException {
        String sql = "UPDATE withdrawal_requests SET status=?, processed_at=NOW() WHERE id=?";
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, status);
            p.setInt(2, id);
            return p.executeUpdate() == 1;
        }
    }

    // --- Game Sessions & History ---

    public static List<Map<String, Object>> listActiveSessions() {
        return GameManager.listActiveSessions(); // You must expose this from GameManager
    }

    public static List<Map<String, Object>> getSessionHistory(String sessionId) throws SQLException {
        String sql = "SELECT * FROM game_results WHERE session_id=? ORDER BY rank";
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, sessionId);
            ResultSet rs = p.executeQuery();
            while (rs.next()) {
                out.add(Map.of(
                        "email", rs.getString("email"),
                        "rank", rs.getInt("rank"),
                        "xp", rs.getInt("xp_awarded"),
                        "tokens", rs.getInt("tokens_won")
                ));
            }
        }
        return out;
    }

    // --- Helpers ---

    public static int getWalletBalance(String email) throws SQLException {
        String sql = "SELECT balance FROM wallets WHERE email=?";
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, email);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt("balance");
        }
        return 0;
    }

    public static int getTotalXP(String email) throws SQLException {
        String sql = "SELECT xp FROM leaderboard WHERE email=?";
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, email);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt("xp");
        }
        return 0;
    }

    public static int getGamesPlayed(String email) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT session_id) AS total FROM game_results WHERE email=?";
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, email);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt("total");
        }
        return 0;
    }

    public static int getWins(String email) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM game_results WHERE email=? AND rank=1";
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, email);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt("total");
        }
        return 0;
    }

    public static int countWithdrawals(String email, String status) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM withdrawal_requests WHERE email=?" +
                (status != null ? " AND status=?" : "");
        try (Connection c = DBManager.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, email);
            if (status != null) p.setString(2, status);
            ResultSet rs = p.executeQuery();
            if (rs.next()) return rs.getInt("total");
        }
        return 0;
    }
	
	// Page helper
	private static <T> List<T> paginate(List<T> list, int page, int size) {
		if (page < 1 || size < 1) return Collections.emptyList();
		int from = (page - 1) * size;
		if (from >= list.size()) return Collections.emptyList();
		return list.subList(from, Math.min(from + size, list.size()));
	}

	// Example: Top users by total XP
	public static List<Map<String, Object>> topUsersByXP(int limit) throws SQLException {
		String sql = "SELECT email, xp FROM leaderboard ORDER BY xp DESC LIMIT ?";
		List<Map<String, Object>> out = new ArrayList<>();
		try (Connection c = DBManager.getConnection();
			 PreparedStatement p = c.prepareStatement(sql)) {
			p.setInt(1, limit);
			try (ResultSet rs = p.executeQuery()) {
				while (rs.next()) {
					Map<String, Object> record = new HashMap<>();
					record.put("email", rs.getString("email"));
					record.put("xp", rs.getInt("xp"));
					out.add(record);
				}
			}
		}
		return out;
	}
	
}
