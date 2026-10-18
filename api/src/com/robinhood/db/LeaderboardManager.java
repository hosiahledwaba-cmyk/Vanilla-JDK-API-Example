package com.robinhood.db;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardManager {
    private static final Map<String, Integer> memoryXP = new ConcurrentHashMap<>();

    static {
        // Seed some sample champions so leaderboard is never completely blank
        memoryXP.put("robin_legend", 480);
        memoryXP.put("snake_charmer", 320);
        memoryXP.put("ladder_king", 195);
    }

    public static void addXP(String email, int xp) {
        if (email != null) {
            memoryXP.merge(email, xp, Integer::sum);
        }
        String sql = "INSERT INTO leaderboard (email, weekly_xp, total_xp, games_played, wins, updated_at) " +
                     "VALUES (?, ?, ?, 1, ?, NOW()) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "weekly_xp = weekly_xp + VALUES(weekly_xp), " +
                     "total_xp = total_xp + VALUES(total_xp), " +
                     "games_played = games_played + 1, " +
                     "wins = wins + VALUES(wins), " +
                     "updated_at = NOW()";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setString(1, email);
                ps.setInt(2, xp);
                ps.setInt(3, xp);
                ps.setInt(4, xp > 0 ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static String getTop100Json() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean fromDb = false;
        String sql = "SELECT email, weekly_xp FROM leaderboard ORDER BY weekly_xp DESC LIMIT 100";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null;
             ResultSet rs = (ps != null) ? ps.executeQuery() : null) {
            if (rs != null) {
                while (rs.next()) {
                    fromDb = true;
                    sb.append("{\"email\":\"").append(rs.getString("email"))
                            .append("\",\"xp\":").append(rs.getInt("weekly_xp")).append("},");
                }
            }
        } catch (Exception ignored) {}

        if (!fromDb) {
            memoryXP.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(100)
                    .forEach(entry -> {
                        sb.append("{\"email\":\"").append(entry.getKey())
                          .append("\",\"xp\":").append(entry.getValue()).append("},");
                    });
        }

        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    public static int getXP(String email) {
        if (email == null) return 0;
        String sql = "SELECT total_xp FROM leaderboard WHERE email = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("total_xp");
                }
            }
        } catch (Exception ignored) {}
        return memoryXP.getOrDefault(email, 0);
    }
}
