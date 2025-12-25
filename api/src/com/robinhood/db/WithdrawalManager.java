package com.robinhood.db;

import com.robinhood.model.WithdrawalRequest;

import java.sql.*;

public class WithdrawalManager {

    public static boolean requestWithdrawal(WithdrawalRequest wr) {
        String sql = "INSERT INTO withdrawal_requests (email, amount, method, details) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wr.getEmail());
            ps.setInt(2, wr.getAmount());
            ps.setString(3, wr.getMethod());
            ps.setString(4, wr.getDetails());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getUserRequests(String email) {
        StringBuilder sb = new StringBuilder("[");
        String sql = "SELECT * FROM withdrawal_requests WHERE email = ? ORDER BY requested_at DESC";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                sb.append("{\"id\":").append(rs.getInt("id"))
                  .append(",\"amount\":").append(rs.getInt("amount"))
                  .append(",\"status\":\"").append(rs.getString("status"))
                  .append("\",\"method\":\"").append(rs.getString("method"))
                  .append("\",\"requestedAt\":\"").append(rs.getTimestamp("requested_at")).append("\"},");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
	
	public static boolean requestWithdrawalAtomically(String email, int amount, String method, String details) {
		String deductSql = "UPDATE wallets SET balance = balance - ? WHERE email = ? AND balance >= ?";
		String insertSql = "INSERT INTO withdrawal_requests (email, amount, method, details) VALUES (?, ?, ?, ?)";

		try (Connection conn = DBManager.getConnection()) {
			conn.setAutoCommit(false);

			// 1. Deduct from wallet table
			try (PreparedStatement ps1 = conn.prepareStatement(deductSql)) {
				ps1.setInt(1, amount);
				ps1.setString(2, email);
				ps1.setInt(3, amount);
				int updated = ps1.executeUpdate();
				if (updated != 1) {
					conn.rollback();
					return false; // insufficient funds
				}
			}

			// 2. Insert withdrawal request
			try (PreparedStatement ps2 = conn.prepareStatement(insertSql)) {
				ps2.setString(1, email);
				ps2.setInt(2, amount);
				ps2.setString(3, method);
				ps2.setString(4, details);
				ps2.executeUpdate();
			}

			conn.commit();
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	}

	
}
