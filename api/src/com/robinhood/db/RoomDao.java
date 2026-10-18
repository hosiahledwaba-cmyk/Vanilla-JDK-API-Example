package com.robinhood.db;

import java.sql.*;
import java.util.*;

public class RoomDao {

    public static class Room {
        public String id;
        public String roomCode;
        public String hostId;
        public String gameType;
        public String khasinoMode;
        public String aiDifficulty;
        public int maxPlayers;
        public int wagerAmount;
        public String status;
        public boolean isBotMatch;
        public Timestamp createdAt;
        public List<RoomMember> members = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"id\":\"").append(id).append("\",");
            sb.append("\"roomCode\":\"").append(roomCode).append("\",");
            sb.append("\"code\":\"").append(roomCode).append("\",");
            sb.append("\"hostId\":\"").append(hostId).append("\",");
            sb.append("\"gameType\":\"").append(gameType).append("\",");
            sb.append("\"khasinoMode\":").append(khasinoMode != null ? "\"" + khasinoMode + "\"" : "null").append(",");
            sb.append("\"aiDifficulty\":").append(aiDifficulty != null ? "\"" + aiDifficulty + "\"" : "null").append(",");
            sb.append("\"maxPlayers\":").append(maxPlayers).append(",");
            sb.append("\"wagerAmount\":").append(wagerAmount).append(",");
            sb.append("\"status\":\"").append(status).append("\",");
            sb.append("\"isBotMatch\":").append(isBotMatch).append(",");
            sb.append("\"members\":[");
            for (int i = 0; i < members.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(members.get(i).toJson());
            }
            sb.append("]");
            sb.append("}");
            return sb.toString();
        }
    }

    public static class RoomMember {
        public String roomId;
        public String playerId;
        public int seatIndex;
        public int teamId;
        public boolean isHost;
        public boolean isReady;
        public String status;
        public Timestamp joinedAt;

        public String toJson() {
            return "{" +
                "\"playerId\":\"" + playerId + "\"," +
                "\"seatIndex\":" + seatIndex + "," +
                "\"teamId\":" + teamId + "," +
                "\"isHost\":" + isHost + "," +
                "\"isReady\":" + isReady + "," +
                "\"status\":\"" + status + "\"" +
                "}";
        }
    }

    public static void createRoom(Room room) throws SQLException {
        String sql = "INSERT INTO rooms (id, room_code, host_id, game_type, khasino_mode, ai_difficulty, " +
                     "max_players, wager_amount, status, is_bot_match) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room.id);
            ps.setString(2, room.roomCode);
            ps.setString(3, room.hostId);
            ps.setString(4, room.gameType);
            ps.setString(5, room.khasinoMode);
            ps.setString(6, room.aiDifficulty);
            ps.setInt(7, room.maxPlayers);
            ps.setInt(8, room.wagerAmount);
            ps.setString(9, room.status != null ? room.status : "WAITING");
            ps.setBoolean(10, room.isBotMatch);
            ps.executeUpdate();
        }
    }

    public static Room findById(String id) {
        String sql = "SELECT * FROM rooms WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Room r = mapRoom(rs);
                    r.members = getMembers(conn, r.id);
                    return r;
                }
            }
        } catch (SQLException e) {
            System.err.println("[RoomDao] findById error: " + e.getMessage());
        }
        return null;
    }

    public static Room findByCode(String code) {
        String sql = "SELECT * FROM rooms WHERE UPPER(room_code) = UPPER(?)";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Room r = mapRoom(rs);
                    r.members = getMembers(conn, r.id);
                    return r;
                }
            }
        } catch (SQLException e) {
            System.err.println("[RoomDao] findByCode error: " + e.getMessage());
        }
        return null;
    }

    public static synchronized boolean addMember(String roomId, String playerId, int seatIndex, int teamId, boolean isHost) {
        String sql = "INSERT INTO room_memberships (room_id, player_id, seat_index, team_id, is_host, is_ready, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 'JOINED') " +
                     "ON CONFLICT (room_id, player_id) DO UPDATE SET status = 'JOINED', left_at = NULL";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, playerId);
            ps.setInt(3, seatIndex);
            ps.setInt(4, teamId);
            ps.setBoolean(5, isHost);
            ps.setBoolean(6, isHost); // Host is automatically ready
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[RoomDao] addMember error: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void removeMember(String roomId, String playerId) {
        String sql = "UPDATE room_memberships SET status = 'LEFT', left_at = CURRENT_TIMESTAMP WHERE room_id = ? AND player_id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            ps.setString(2, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RoomDao] removeMember error: " + e.getMessage());
        }
    }

    public static synchronized void setMemberReady(String roomId, String playerId, boolean ready) {
        String sql = "UPDATE room_memberships SET is_ready = ? WHERE room_id = ? AND player_id = ? AND status = 'JOINED'";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, ready);
            ps.setString(2, roomId);
            ps.setString(3, playerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RoomDao] setMemberReady error: " + e.getMessage());
        }
    }

    public static synchronized void updateRoomStatus(String roomId, String status) {
        String sql = "UPDATE rooms SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[RoomDao] updateRoomStatus error: " + e.getMessage());
        }
    }

    public static List<RoomMember> getMembers(Connection conn, String roomId) {
        List<RoomMember> list = new ArrayList<>();
        String sql = "SELECT * FROM room_memberships WHERE room_id = ? AND status = 'JOINED' ORDER BY seat_index ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RoomMember m = new RoomMember();
                    m.roomId = rs.getString("room_id");
                    m.playerId = rs.getString("player_id");
                    m.seatIndex = rs.getInt("seat_index");
                    m.teamId = rs.getInt("team_id");
                    m.isHost = rs.getBoolean("is_host");
                    m.isReady = rs.getBoolean("is_ready");
                    m.status = rs.getString("status");
                    m.joinedAt = rs.getTimestamp("joined_at");
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RoomDao] getMembers error: " + e.getMessage());
        }
        return list;
    }

    private static Room mapRoom(ResultSet rs) throws SQLException {
        Room r = new Room();
        r.id = rs.getString("id");
        r.roomCode = rs.getString("room_code");
        r.hostId = rs.getString("host_id");
        r.gameType = rs.getString("game_type");
        r.khasinoMode = rs.getString("khasino_mode");
        r.aiDifficulty = rs.getString("ai_difficulty");
        r.maxPlayers = rs.getInt("max_players");
        r.wagerAmount = rs.getInt("wager_amount");
        r.status = rs.getString("status");
        r.isBotMatch = rs.getBoolean("is_bot_match");
        r.createdAt = rs.getTimestamp("created_at");
        return r;
    }
}
