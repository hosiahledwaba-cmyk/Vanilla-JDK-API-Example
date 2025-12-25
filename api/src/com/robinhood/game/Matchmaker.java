package com.robinhood.game;

import com.robinhood.controller.WalletController;
import com.robinhood.model.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Matchmaker {

    // Key: wager_amount + player_count
    private static final Map<String, List<PlayerEntry>> waitingQueues = new ConcurrentHashMap<>();
    private static final int XP_TOLERANCE = 100; // Allow ±100 XP

    private static class PlayerEntry {
        Player player;
        int xp;

        PlayerEntry(Player player, int xp) {
            this.player = player;
            this.xp = xp;
        }
    }

    public static synchronized String requestMatch(Player player, int wager, int playerCount, int playerXP) {
        String key = wager + "x" + playerCount;

        List<PlayerEntry> queue = waitingQueues.getOrDefault(key, new ArrayList<>());

        // Filter queue for fair XP proximity
        List<PlayerEntry> compatible = new ArrayList<>();
        for (PlayerEntry entry : queue) {
            if (Math.abs(entry.xp - playerXP) <= XP_TOLERANCE) {
                compatible.add(entry);
            }
        }

        if (compatible.size() >= playerCount - 1) {
            // Match found!
            List<PlayerEntry> finalGroup = new ArrayList<>(compatible.subList(0, playerCount - 1));
            finalGroup.add(new PlayerEntry(player, playerXP));

            GameSession session = GameManager.createSession(playerCount, wager);
            for (PlayerEntry entry : finalGroup) {
                boolean ok = session.addPlayer(entry.player);
                if (!ok) return null; // If any wallet fails, abort match
            }
            session.start();

            // Remove matched players from queue
            queue.removeAll(finalGroup);
            waitingQueues.put(key, queue);

            return session.getSessionId();
        }

        // No match yet — enqueue
        queue.add(new PlayerEntry(player, playerXP));
        waitingQueues.put(key, queue);
        return null; // waiting
    }
}
