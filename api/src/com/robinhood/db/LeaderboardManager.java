package com.robinhood.db;

import java.sql.*;

public class LeaderboardManager {

    public static void addXP(String email, int xp) {
        String sql = "INSERT INTO leaderboard (email, weekly_xp, total_xp, games_played, wins, updated_at) " +
                     "VALUES (?, ?, ?, 1, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "weekly_xp = weekly_xp + VALUES(weekly_xp), " +
                     "total_xp = total_xp + VALUES(total_xp), " +
                     "games_played = games_played + 1, " +
                     "wins = wins + VALUES(wins), " +
                     "updated_at = NOW()";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, xp);
            ps.setInt(3, xp);
            ps.setInt(4, xp > 0 ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getTop100Json() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        String sql = "SELECT email, weekly_xp FROM leaderboard ORDER BY weekly_xp DESC LIMIT 100";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sb.append("{\"email\":\"").append(rs.getString("email"))
                        .append("\",\"weeklyXP\":").append(rs.getInt("weekly_xp")).append("},");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }

	public static int getXP(String email) {
		String sql = "SELECT total_xp FROM leaderboard WHERE email = ?";
		try (Connection conn = DBManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, email);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getInt("total_xp");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

}

