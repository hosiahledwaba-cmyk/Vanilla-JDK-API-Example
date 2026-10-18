package com.robinhood.db;

import com.robinhood.model.PaymentTransaction;
import com.robinhood.util.JSONParser;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentTransactionManager {
    private static final Map<Integer, PaymentTransaction> memoryStore = new ConcurrentHashMap<>();
    private static final AtomicInteger idSeq = new AtomicInteger(1);

    public static int create(PaymentTransaction tx) {
        int genId = idSeq.getAndIncrement();
        tx.setId(genId);
        memoryStore.put(genId, tx);

        String sql = "INSERT INTO payment_transactions (email,type,provider,amount,currency,metadata) VALUES (?,?,?,?,?,?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tx.getEmail());
            ps.setString(2, tx.getType());
            ps.setString(3, tx.getProvider());
            ps.setInt(4, tx.getAmount());
            ps.setString(5, tx.getCurrency());
            ps.setString(6, JSONParser.stringify(tx.getMetadata()));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int dbId = rs.getInt(1);
                tx.setId(dbId);
                memoryStore.put(dbId, tx);
                return dbId;
            }
        } catch (SQLException ignored) {}
        return genId;
    }

    public static boolean updateStatus(int id, String status, String providerTxId) {
        PaymentTransaction tx = memoryStore.get(id);
        if (tx != null) {
            tx.setStatus(status);
            tx.setProviderTxId(providerTxId);
        }

        String sql = "UPDATE payment_transactions SET status = ?, provider_tx_id = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, providerTxId);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return tx != null;
        }
    }

    public static PaymentTransaction getById(int id) {
        PaymentTransaction tx = memoryStore.get(id);
        if (tx != null) return tx;

        String sql = "SELECT * FROM payment_transactions WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PaymentTransaction pt = new PaymentTransaction();
                pt.setId(rs.getInt("id"));
                pt.setEmail(rs.getString("email"));
                pt.setType(rs.getString("type"));
                pt.setProvider(rs.getString("provider"));
                pt.setProviderTxId(rs.getString("provider_tx_id"));
                pt.setAmount(rs.getInt("amount"));
                pt.setCurrency(rs.getString("currency"));
                pt.setStatus(rs.getString("status"));
                return pt;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public static PaymentTransaction get(int id) {
        return getById(id);
    }
}
