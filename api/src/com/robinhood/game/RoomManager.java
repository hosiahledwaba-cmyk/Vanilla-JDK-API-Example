package com.robinhood.game;

import com.robinhood.controller.WalletController;
import com.robinhood.db.MatchSessionDao;
import com.robinhood.db.RoomDao;
import com.robinhood.realtime.RealTimeDispatcher;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, RoomDao.Room> activeRooms = new ConcurrentHashMap<>();

    public static String generateRoomCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public static synchronized RoomDao.Room createRoom(String hostEmail, String gameTypeStr, String khasinoModeStr,
                                                       String aiDifficultyStr, int maxPlayers, int wagerAmount, boolean isBotMatch) throws Exception {
        if (wagerAmount < 0) wagerAmount = 0;
        if (maxPlayers < 2) maxPlayers = 2;
        if (maxPlayers > 4) maxPlayers = 4;

        if (wagerAmount > 0 && WalletController.getBalance(hostEmail) < wagerAmount) {
            throw new IllegalArgumentException("Insufficient token balance for wager of " + wagerAmount);
        }

        String roomId = UUID.randomUUID().toString();
        String roomCode = generateRoomCode();

        RoomDao.Room room = new RoomDao.Room();
        room.id = roomId;
        room.roomCode = roomCode;
        room.hostId = hostEmail;
        room.gameType = (gameTypeStr != null && !gameTypeStr.isBlank()) ? gameTypeStr.toUpperCase() : "SNAKES_AND_LADDERS";
        room.khasinoMode = khasinoModeStr;
        room.aiDifficulty = aiDifficultyStr;
        room.maxPlayers = maxPlayers;
        room.wagerAmount = wagerAmount;
        room.status = "WAITING";
        room.isBotMatch = isBotMatch;

        // Persist to PostgreSQL
        try {
            RoomDao.createRoom(room);
            RoomDao.addMember(roomId, hostEmail, 0, 1, true);
        } catch (Exception e) {
            System.err.println("[RoomManager] DB error creating room: " + e.getMessage());
        }

        RoomDao.RoomMember hostMember = new RoomDao.RoomMember();
        hostMember.roomId = roomId;
        hostMember.playerId = hostEmail;
        hostMember.seatIndex = 0;
        hostMember.teamId = 1;
        hostMember.isHost = true;
        hostMember.isReady = true;
        hostMember.status = "JOINED";
        room.members.add(hostMember);

        activeRooms.put(roomId, room);
        return room;
    }

    public static synchronized RoomDao.Room joinRoom(String codeOrId, String playerEmail) throws Exception {
        RoomDao.Room room = getRoom(codeOrId);
        if (room == null) {
            throw new IllegalArgumentException("Room not found: " + codeOrId);
        }

        if (!"WAITING".equalsIgnoreCase(room.status)) {
            throw new IllegalStateException("Room is not accepting new players (status: " + room.status + ")");
        }

        // Check if already in room
        for (RoomDao.RoomMember m : room.members) {
            if (m.playerId.equalsIgnoreCase(playerEmail)) {
                return room; // Already in room, return state
            }
        }

        if (room.members.size() >= room.maxPlayers) {
            throw new IllegalStateException("Room is full (max " + room.maxPlayers + " players)");
        }

        if (room.wagerAmount > 0 && WalletController.getBalance(playerEmail) < room.wagerAmount) {
            throw new IllegalArgumentException("Insufficient balance for wager of " + room.wagerAmount + " tokens");
        }

        int seatIndex = room.members.size();
        // 4-player partnerships: Seat 0 & 2 Team 1; Seat 1 & 3 Team 2
        int teamId = 0;
        if ("FOUR_PLAYER_PARTNERSHIPS".equalsIgnoreCase(room.khasinoMode)) {
            teamId = (seatIndex % 2 == 0) ? 1 : 2;
        }

        RoomDao.RoomMember newMember = new RoomDao.RoomMember();
        newMember.roomId = room.id;
        newMember.playerId = playerEmail;
        newMember.seatIndex = seatIndex;
        newMember.teamId = teamId;
        newMember.isHost = false;
        newMember.isReady = false;
        newMember.status = "JOINED";

        room.members.add(newMember);

        // Persist to PostgreSQL
        try {
            RoomDao.addMember(room.id, playerEmail, seatIndex, teamId, false);
        } catch (Exception e) {
            System.err.println("[RoomManager] DB error joining room: " + e.getMessage());
        }

        // Notify all participants in this room
        RealTimeDispatcher.publish(room.id, "{\"type\":\"room_update\",\"room\":" + room.toJson() + "}");
        return room;
    }

    public static synchronized RoomDao.Room setReady(String roomId, String playerEmail, boolean ready) throws Exception {
        RoomDao.Room room = getRoom(roomId);
        if (room == null) throw new IllegalArgumentException("Room not found");

        for (RoomDao.RoomMember m : room.members) {
            if (m.playerId.equalsIgnoreCase(playerEmail)) {
                m.isReady = ready;
                break;
            }
        }

        try {
            RoomDao.setMemberReady(room.id, playerEmail, ready);
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(room.id, "{\"type\":\"room_update\",\"room\":" + room.toJson() + "}");
        return room;
    }

    public static synchronized void leaveRoom(String roomId, String playerEmail) {
        RoomDao.Room room = getRoom(roomId);
        if (room == null) return;

        room.members.removeIf(m -> m.playerId.equalsIgnoreCase(playerEmail));

        try {
            RoomDao.removeMember(room.id, playerEmail);
        } catch (Exception ignored) {}

        if (room.members.isEmpty()) {
            room.status = "ABANDONED";
            try { RoomDao.updateRoomStatus(room.id, "ABANDONED"); } catch (Exception ignored) {}
            activeRooms.remove(room.id);
        } else {
            // If host left, transfer host role
            if (room.hostId.equalsIgnoreCase(playerEmail)) {
                RoomDao.RoomMember nextHost = room.members.get(0);
                nextHost.isHost = true;
                nextHost.isReady = true;
                room.hostId = nextHost.playerId;
            }
            RealTimeDispatcher.publish(room.id, "{\"type\":\"room_update\",\"room\":" + room.toJson() + "}");
        }
    }

    public static synchronized GameSession startMatch(String roomId, String hostEmail, boolean allowBotFill) throws Exception {
        return startMatch(roomId, hostEmail, allowBotFill, null);
    }

    public static synchronized GameSession startMatch(String roomId, String hostEmail, boolean allowBotFill, String forcedSessionId) throws Exception {
        RoomDao.Room room = getRoom(roomId);
        if (room == null) throw new IllegalArgumentException("Room not found");
        if (!room.hostId.equalsIgnoreCase(hostEmail)) {
            throw new IllegalStateException("Only the host can start the match");
        }
        if (!"WAITING".equalsIgnoreCase(room.status)) {
            throw new IllegalStateException("Room is already started or finished");
        }

        int humanCount = room.members.size();
        if (humanCount < 2 && !allowBotFill && !room.isBotMatch) {
            throw new IllegalStateException("Waiting for at least 2 players to join before starting");
        }

        room.status = "IN_PROGRESS";
        try { RoomDao.updateRoomStatus(room.id, "IN_PROGRESS"); } catch (Exception ignored) {}

        // Resolve GameType
        com.robinhood.engine.GameType gt = com.robinhood.engine.GameType.SNAKES_AND_LADDERS;
        try {
            gt = com.robinhood.engine.GameType.valueOf(room.gameType);
        } catch (Exception ignored) {}

        String sId = (forcedSessionId != null && !forcedSessionId.isBlank()) ? forcedSessionId : UUID.randomUUID().toString();
        GameSession session = GameManager.createSession(sId, room.maxPlayers, room.wagerAmount, gt);
        session.setAutoFillBots(allowBotFill || room.isBotMatch);

        if (gt == com.robinhood.engine.GameType.KHASINO && room.khasinoMode != null) {
            try {
                session.setKhasinoMode(com.robinhood.engine.khasino.KhasinoGame.Mode.valueOf(room.khasinoMode));
            } catch (Exception ignored) {}
            if (room.aiDifficulty != null) {
                try {
                    session.setKhasinoDifficulty(com.robinhood.engine.khasino.KhasinoAi.Difficulty.valueOf(room.aiDifficulty));
                } catch (Exception ignored) {}
            }
        }

        // Add human players in seat order
        for (RoomDao.RoomMember m : room.members) {
            Player p = new Player(m.playerId, "token-" + m.playerId);
            session.addPlayer(p);
        }

        // Fill remaining slots with bots if allowed / needed
        if (allowBotFill || room.isBotMatch) {
            int botIdx = 1;
            while (session.getPlayers().size() < room.maxPlayers) {
                String botName = (botIdx == 1) ? "RobinBot" : "CPU_" + botIdx;
                WalletController.credit(botName, room.wagerAmount * 10);
                session.addPlayer(new Player(botName, "bot-token"));
                botIdx++;
            }
        }

        // Start session
        session.start();

        // Persist initial match state in PostgreSQL
        try {
            MatchSessionDao.createMatch(session.getSessionId(), room.id, gt.name(), room.khasinoMode,
                                       room.maxPlayers, room.wagerAmount, session.getCurrentTurnPlayer());
            String stateJson = getSessionStateJson(session);
            if (stateJson != null) {
                MatchSessionDao.saveGameState(session.getSessionId(), 1, stateJson);
            }
        } catch (Exception e) {
            System.err.println("[RoomManager] DB error saving initial match session: " + e.getMessage());
        }

        // Broadcast match start to room channel so all room players launch into the game!
        String matchStartPayload = "{" +
            "\"type\":\"match_start\"," +
            "\"roomId\":\"" + room.id + "\"," +
            "\"sessionId\":\"" + session.getSessionId() + "\"," +
            "\"gameType\":\"" + gt.name() + "\"" +
            "}";
        RealTimeDispatcher.publish(room.id, matchStartPayload);

        return session;
    }

    public static RoomDao.Room getRoom(String codeOrId) {
        if (codeOrId == null || codeOrId.isBlank()) return null;
        RoomDao.Room r = activeRooms.get(codeOrId);
        if (r != null) return r;

        // Search activeRooms by roomCode
        for (RoomDao.Room room : activeRooms.values()) {
            if (codeOrId.equalsIgnoreCase(room.roomCode)) {
                return room;
            }
        }

        // Check PostgreSQL
        r = RoomDao.findById(codeOrId);
        if (r == null) r = RoomDao.findByCode(codeOrId);
        if (r != null) {
            activeRooms.put(r.id, r);
        }
        return r;
    }

    public static String getSessionStateJson(GameSession session) {
        if (session == null) return "{}";
        com.robinhood.engine.GameType gt = session.getGameType();
        if (gt == com.robinhood.engine.GameType.KHASINO && session.getKhasinoGame() != null) {
            return session.getKhasinoGame().toJson();
        } else if (gt == com.robinhood.engine.GameType.UNO && session.getUnoGame() != null) {
            return session.getUnoGame().toJson();
        } else if (gt == com.robinhood.engine.GameType.CRAZY_EIGHT && session.getCrazyEightGame() != null) {
            return session.getCrazyEightGame().toJson();
        } else if (session.getBoard() != null) {
            return session.getBoard().toJson();
        }
        return "{}";
    }

    public static int getActiveRoomCount() {
        return activeRooms.size();
    }
}
