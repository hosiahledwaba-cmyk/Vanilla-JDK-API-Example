package com.robinhood.db;

import com.robinhood.model.KycRequest;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KycRequestManager {
    private static final Map<Integer, KycRequest> memoryStore = new ConcurrentHashMap<>();
    private static final AtomicInteger idSeq = new AtomicInteger(1);

    public static int create(KycRequest r) {
        int genId = idSeq.getAndIncrement();
        r.setId(genId);
        memoryStore.put(genId, r);

        String sql = "INSERT INTO kyc_requests (email, full_name, document_type, document_url) VALUES (?,?,?,?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.getEmail());
            ps.setString(2, r.getFullName());
            ps.setString(3, r.getDocumentType());
            ps.setString(4, r.getDocumentUrl());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int dbId = rs.getInt(1);
                r.setId(dbId);
                memoryStore.put(dbId, r);
                return dbId;
            }
        } catch (SQLException ignored) {}
        return genId;
    }

    public static KycRequest getById(int id) {
        KycRequest r = memoryStore.get(id);
        if (r != null) return r;

        String sql = "SELECT * FROM kyc_requests WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                KycRequest req = new KycRequest(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("document_type"),
                    rs.getString("document_url"),
                    null,
                    rs.getString("status"),
                    rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toLocalDateTime() : LocalDateTime.now(),
                    rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toLocalDateTime() : null
                );
                req.setAdminNote(rs.getString("admin_note"));
                return req;
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public static List<KycRequest> listPending() {
        List<KycRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM kyc_requests WHERE status='pending'";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                KycRequest req = new KycRequest(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("document_type"),
                    rs.getString("document_url"),
                    null,
                    rs.getString("status"),
                    rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toLocalDateTime() : LocalDateTime.now(),
                    rs.getTimestamp("processed_at") != null ? rs.getTimestamp("processed_at").toLocalDateTime() : null
                );
                list.add(req);
            }
        } catch (SQLException e) {
            for (KycRequest req : memoryStore.values()) {
                if ("pending".equalsIgnoreCase(req.getStatus())) {
                    list.add(req);
                }
            }
        }
        return list;
    }

    public static boolean updateStatus(int id, String status, String note) {
        return updateStatus(id, status, null, note);
    }

    public static boolean updateStatus(int id, String status, String providerRequestId, String note) {
        KycRequest r = memoryStore.get(id);
        if (r != null) {
            r.setStatus(status);
            if (providerRequestId != null) r.setProviderRequestId(providerRequestId);
            if (note != null) r.setAdminNote(note);
            r.setProcessedAt(LocalDateTime.now());
        }

        String sql = "UPDATE kyc_requests SET status = ?, admin_note = ?, processed_at = NOW() WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, note);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            return r != null;
        }
    }
}
