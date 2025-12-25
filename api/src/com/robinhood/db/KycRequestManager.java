// KycRequestManager.java
package com.robinhood.db;

import com.robinhood.model.KycRequest;
import java.sql.*;
import java.util.*;

public class KycRequestManager {
    public static int create(KycRequest r) {
        String sql = "INSERT INTO kyc_requests (email, full_name, document_type, document_url) VALUES (?,?,?,?)";
        try (var conn = DBManager.getConnection();
             var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getEmail());
            ps.setString(2, r.getFullName());
            ps.setString(3, r.getDocumentType());
            ps.setString(4, r.getDocumentUrl());
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }

    public static KycRequest getById(int id) { /* load from DB */ }
    public static List<KycRequest> listPending() { /* select WHERE status='pending' */ }
    public static boolean updateStatus(int id, String status, String note) { /* update + processed_at */ }
}
