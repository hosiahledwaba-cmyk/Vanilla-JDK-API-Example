package com.robinhood.db;

import com.robinhood.model.LedgerEntry;

import java.sql.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LedgerManager {
    private static final List<LedgerEntry> memoryLedger = new CopyOnWriteArrayList<>();

    public static void log(LedgerEntry entry) {
        if (entry != null) memoryLedger.add(entry);
        String query = "INSERT INTO ledger (email, type, amount, note, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(query) : null) {
            if (ps != null) {
                ps.setString(1, entry.getEmail());
                ps.setString(2, entry.getType().name());
                ps.setInt(3, entry.getAmount());
                ps.setString(4, entry.getNote());
                ps.setTimestamp(5, Timestamp.valueOf(entry.getTimestamp()));
                ps.executeUpdate();
            }
        } catch (Exception ignored) {}
    }

    public static int getHouseEarnings() {
        String sql = "SELECT SUM(amount) FROM ledger WHERE email = 'HOUSE' AND type = 'RAKE'";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = (conn != null) ? conn.prepareStatement(sql) : null;
             ResultSet rs = (ps != null) ? ps.executeQuery() : null) {
            if (rs != null && rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        return memoryLedger.stream()
                .filter(e -> "HOUSE".equals(e.getEmail()) && e.getType() == LedgerEntry.Type.RAKE)
                .mapToInt(LedgerEntry::getAmount)
                .sum();
    }

    public static List<LedgerEntry> getEntriesForUser(String email) {
        return memoryLedger.stream()
                .filter(e -> email != null && email.equals(e.getEmail()))
                .toList();
    }
}
