package com.robinhood.game;

import com.robinhood.controller.WalletController;
import com.robinhood.db.MatchmakerDao;
import com.robinhood.db.RoomDao;
import com.robinhood.realtime.RealTimeDispatcher;

import java.util.*;

public class Matchmaker {

    public static synchronized String joinQueue(Player player, int wager, com.robinhood.engine.GameType gameType) {
        return joinQueue(player, wager, gameType, null, 2);
    }

    public static synchronized String requestMatch(Player player, int wager, int maxPlayers, int xp) {
        return joinQueue(player, wager, com.robinhood.engine.GameType.SNAKES_AND_LADDERS, null, maxPlayers);
    }

    public static synchronized String joinQueue(Player player, int wager, com.robinhood.engine.GameType gameType,
                                                String khasinoMode, int maxPlayers) {
        String email = player.getEmail();
        if (maxPlayers < 2) maxPlayers = 2;
        if (wager > 0 && WalletController.getBalance(email) < wager) {
            throw new IllegalArgumentException("Insufficient balance for wager of " + wager);
        }

        String gtStr = (gameType != null) ? gameType.name() : "SNAKES_AND_LADDERS";

        // Enqueue into PostgreSQL
        MatchmakerDao.enqueue(email, gtStr, khasinoMode, maxPlayers, wager);

        // Try atomic matchmaking in PostgreSQL
        String targetSessionId = "mm-" + UUID.randomUUID().toString();
        List<String> matchedPlayers = MatchmakerDao.tryMatch(gtStr, khasinoMode, maxPlayers, wager, targetSessionId);

        if (!matchedPlayers.isEmpty()) {
            // Group of compatible human players matched!
            try {
                String hostEmail = matchedPlayers.get(0);
                RoomDao.Room room = RoomManager.createRoom(hostEmail, gtStr, khasinoMode, "MEDIUM", maxPlayers, wager, false);

                // Add remaining players
                for (int i = 1; i < matchedPlayers.size(); i++) {
                    RoomManager.joinRoom(room.id, matchedPlayers.get(i));
                }

                // Start match automatically for matched players
                GameSession session = RoomManager.startMatch(room.id, hostEmail, false, targetSessionId);

                // Notify all matched players via their personal channels and room channel
                for (String pId : matchedPlayers) {
                    RealTimeDispatcher.publish(pId, "{\"type\":\"match_found\",\"sessionId\":\"" + session.getSessionId() + "\",\"gameType\":\"" + gtStr + "\"}");
                }

                return session.getSessionId();
            } catch (Exception e) {
                System.err.println("[Matchmaker] Error launching matched game: " + e.getMessage());
            }
        }

        return null; // Waiting for more human players
    }

    public static synchronized String pollQueue(String email) {
        MatchmakerDao.QueueStatus status = MatchmakerDao.checkStatus(email);
        if ("MATCHED".equalsIgnoreCase(status.status) && status.matchedRoomId != null) {
            // Check if room or session is created
            RoomDao.Room r = RoomManager.getRoom(status.matchedRoomId);
            if (r != null) {
                return status.matchedRoomId;
            }
            return status.matchedRoomId;
        }
        return null;
    }

    public static synchronized void cancelQueue(String email) {
        MatchmakerDao.cancel(email);
    }
}
