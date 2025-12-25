// PaymentTransactionManager.java
package com.robinhood.db;

import com.robinhood.model.PaymentTransaction;
import java.sql.*;
import java.util.*;

public class PaymentTransactionManager {
    public static int create(PaymentTransaction tx) {
        String sql = "INSERT INTO payment_transactions (email,type,provider,amount,currency,metadata) VALUES (?,?,?,?,?,?)";
        try (var conn = DBManager.getConnection();
             var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tx.getEmail());
            ps.setString(2, tx.getType());
            ps.setString(3, tx.getProvider());
            ps.setInt(4, tx.getAmount());
            ps.setString(5, tx.getCurrency());
            ps.setString(6, JSONParser.stringify(tx.getMetadata()));
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public static boolean updateStatus(int id, String status, String providerTxId) { /* update */ }
    public static PaymentTransaction getById(int id) { /* load */ }
}
