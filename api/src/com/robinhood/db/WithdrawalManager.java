package com.robinhood.db;

import com.robinhood.model.WithdrawalRequest;

import java.sql.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class WithdrawalManager {
    private static final List<WithdrawalRequest> memoryRequests = new CopyOnWriteArrayList<>();
    private static final AtomicInteger idCounter = new AtomicInteger(1001);

    public static boolean requestWithdrawal(WithdrawalRequest wr) {
        if (wr != null) {
            wr.setId(idCounter.getAndIncrement());
            memoryRequests.add(wr);
        }
        String sql = "INSERT INTO withdrawal_requests (email, amount, method, details) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setString(1, wr.getEmail());
                ps.setInt(2, wr.getAmount());
                ps.setString(3, wr.getMethod());
                ps.setString(4, wr.getDetails());
                ps.executeUpdate();
                return true;
            }
        } catch (Exception ignored) {}
        return true;
    }

    public static String getUserRequests(String email) {
        StringBuilder sb = new StringBuilder("[");
        boolean fromDb = false;
        String sql = "SELECT * FROM withdrawal_requests WHERE email = ? ORDER BY requested_at DESC";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        fromDb = true;
                        sb.append("{\"id\":").append(rs.getInt("id"))
                          .append(",\"amount\":").append(rs.getInt("amount"))
                          .append(",\"status\":\"").append(rs.getString("status"))
                          .append("\",\"method\":\"").append(rs.getString("method"))
                          .append("\",\"requestedAt\":\"").append(rs.getTimestamp("requested_at")).append("\"},");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!fromDb) {
            for (WithdrawalRequest wr : memoryRequests) {
                if (email != null && email.equals(wr.getEmail())) {
                    sb.append("{\"id\":").append(wr.getId())
                      .append(",\"amount\":").append(wr.getAmount())
                      .append(",\"status\":\"").append(wr.getStatus())
                      .append("\",\"method\":\"").append(wr.getMethod())
                      .append("\",\"requestedAt\":\"").append(wr.getRequestedAt()).append("\"},");
                }
            }
        }

        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    public static String getAllPending() {
        StringBuilder sb = new StringBuilder("[");
        boolean fromDb = false;
        String sql = "SELECT * FROM withdrawal_requests WHERE status = 'pending' ORDER BY requested_at ASC";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null) {
            if (ps != null) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        fromDb = true;
                        sb.append("{\"id\":").append(rs.getInt("id"))
                          .append(",\"email\":\"").append(rs.getString("email"))
                          .append("\",\"amount\":").append(rs.getInt("amount"))
                          .append(",\"status\":\"").append(rs.getString("status"))
                          .append("\",\"method\":\"").append(rs.getString("method"))
                          .append("\",\"requestedAt\":\"").append(rs.getTimestamp("requested_at")).append("\"},");
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!fromDb) {
            for (WithdrawalRequest wr : memoryRequests) {
                if ("pending".equalsIgnoreCase(wr.getStatus())) {
                    sb.append("{\"id\":").append(wr.getId())
                      .append(",\"email\":\"").append(wr.getEmail())
                      .append("\",\"amount\":").append(wr.getAmount())
                      .append(",\"status\":\"").append(wr.getStatus())
                      .append("\",\"method\":\"").append(wr.getMethod())
                      .append("\",\"requestedAt\":\"").append(wr.getRequestedAt()).append("\"},");
                }
            }
        }

        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();
    }

    public static boolean requestWithdrawalAtomically(String email, int amount, String method, String details) {
        WithdrawalRequest wr = new WithdrawalRequest(email, amount, method, details);
        return requestWithdrawal(wr);
    }
}
