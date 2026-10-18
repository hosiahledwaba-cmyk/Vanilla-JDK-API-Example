package com.robinhood.game;

import com.robinhood.controller.WalletController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Matchmaker {

    // Key: wager_amount + player_count
    private static final Map<String, List<PlayerEntry>> waitingQueues = new ConcurrentHashMap<>();
    private static final int XP_TOLERANCE = 100; // Allow ±100 XP

    private static final Map<String, String> playerMatchedSession = new ConcurrentHashMap<>();
    private static final Map<String, Long> playerEnqueuedTime = new ConcurrentHashMap<>();
    private static final Map<String, Integer> playerQueuedWager = new ConcurrentHashMap<>();

    private static class PlayerEntry {
        Player player;
        int xp;

        PlayerEntry(Player player, int xp) {
            this.player = player;
            this.xp = xp;
        }
    }

    public static synchronized String joinQueue(Player player, int wager) {
        String email = player.getEmail();
        playerMatchedSession.remove(email);
        playerEnqueuedTime.put(email, System.currentTimeMillis());
        playerQueuedWager.put(email, wager);

        // Check if there's someone else waiting with the same wager
        for (Map.Entry<String, Integer> entry : playerQueuedWager.entrySet()) {
            String waitingEmail = entry.getKey();
            if (!waitingEmail.equals(email) && entry.getValue() == wager) {
                // Match with waiting player!
                Player waitingPlayer = new Player(waitingEmail, "");
                GameSession session = GameManager.createSession(2, wager);
                session.addPlayer(waitingPlayer);
                session.addPlayer(player);
                session.start();

                playerQueuedWager.remove(waitingEmail);
                playerQueuedWager.remove(email);
                playerEnqueuedTime.remove(waitingEmail);
                playerEnqueuedTime.remove(email);

                playerMatchedSession.put(waitingEmail, session.getSessionId());
                playerMatchedSession.put(email, session.getSessionId());

                return session.getSessionId();
            }
        }

        return null;
    }

    public static synchronized String pollQueue(String email) {
        String matched = playerMatchedSession.remove(email);
        if (matched != null) return matched;

        Long enqueuedAt = playerEnqueuedTime.get(email);
        if (enqueuedAt != null && System.currentTimeMillis() - enqueuedAt >= 2500) {
            // Auto match with RobinBot after 2.5s in queue so player can play immediately
            int wager = playerQueuedWager.getOrDefault(email, 50);
            playerQueuedWager.remove(email);
            playerEnqueuedTime.remove(email);

            GameSession session = GameManager.createSession(2, wager);
            session.addPlayer(new Player(email, ""));
            WalletController.credit("RobinBot", wager * 10);
            session.addPlayer(new Player("RobinBot", "bot-token"));
            session.start();

            playerMatchedSession.put(email, session.getSessionId());
            return session.getSessionId();
        }

        return null;
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
