package com.robinhood.db;

import com.robinhood.model.LedgerEntry;

import java.sql.*;

public class LedgerManager {
    public static void log(LedgerEntry entry) {
        String query = "INSERT INTO ledger (email, type, amount, note, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, entry.getEmail());
            ps.setString(2, entry.getType().name());
            ps.setInt(3, entry.getAmount());
            ps.setString(4, entry.getNote());
            ps.setTimestamp(5, Timestamp.valueOf(entry.getTimestamp()));
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
	
	public static int getHouseEarnings() {
		String sql = "SELECT SUM(amount) FROM ledger WHERE email = 'HOUSE' AND type = 'RAKE'";
		try (Connection conn = DBManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(sql);
			 ResultSet rs = ps.executeQuery()) {
			if (rs.next()) return rs.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
}
