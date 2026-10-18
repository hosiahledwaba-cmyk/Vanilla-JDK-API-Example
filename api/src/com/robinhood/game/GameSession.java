package com.robinhood.game;

import com.robinhood.controller.WalletController;
import com.robinhood.db.GameResultLogger;
import com.robinhood.db.LedgerManager;
import com.robinhood.model.GameResult;
import com.robinhood.model.LedgerEntry;
import com.robinhood.realtime.RealTimeDispatcher;

import com.robinhood.engine.snakesandladders.Board;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GameSession {
    private final String sessionId;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final int maxPlayers;
    private final int wagerAmount;
    private final Board board = new Board();
    private final Map<String, Integer> playerRanks = new HashMap<>();
    private final Map<String, Integer> playerXP = new HashMap<>();
    private final Map<String, Integer> playerPositions = new ConcurrentHashMap<>();
    private final List<String> turnOrder = new ArrayList<>();
    private final AtomicInteger currentTurnIndex = new AtomicInteger(0);
    private final Map<Integer, String> movesLog = new LinkedHashMap<>();
    private final Map<String, String> playerTurnKeys = new HashMap<>();
    private final Map<String, Long> lastMoveTimestamps = new HashMap<>();
    private volatile boolean started = false;
    private final String boardSeed = UUID.randomUUID().toString();
    private final long TURN_TIMEOUT_MS = 30_000; // 30 seconds

    public GameSession(String sessionId, int maxPlayers, int wagerAmount) {
        this.sessionId = sessionId;
        this.maxPlayers = maxPlayers;
        this.wagerAmount = wagerAmount;
    }

    public Board getBoard() {
        return board;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getWagerAmount() {
        return wagerAmount;
    }

    public boolean isStarted() {
        return started;
    }

    public String getBoardSeed() {
        return boardSeed;
    }

    public synchronized boolean addPlayer(Player player) {
        if (players.size() >= maxPlayers || players.containsKey(player.getEmail()) || started) {
            return false;
        }

        boolean deducted = WalletController.deduct(player.getEmail(), wagerAmount);
        if (!deducted) return false;

        players.put(player.getEmail(), player);
        playerPositions.put(player.getEmail(), 1);
        turnOrder.add(player.getEmail());

        LedgerManager.log(new LedgerEntry(
                player.getEmail(),
                LedgerEntry.Type.WAGER,
                wagerAmount,
                "Wager for game " + sessionId
        ));

        return true;
    }

    public synchronized boolean start() {
        if (players.size() < 1) return false;
        started = true;
        currentTurnIndex.set(0);

        RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"players\":" + playerListJson() + "}");
        return true;
    }

    public synchronized boolean isPlayerTurn(String email) {
        return started && !turnOrder.isEmpty() && turnOrder.get(currentTurnIndex.get() % turnOrder.size()).equals(email);
    }

    public synchronized String getCurrentTurnPlayer() {
        return (started && !turnOrder.isEmpty()) ? turnOrder.get(currentTurnIndex.get() % turnOrder.size()) : null;
    }

    public synchronized String getCurrentTurnKey(String email) {
        if (!isPlayerTurn(email)) return null;
        String turnKey = UUID.randomUUID().toString();
        playerTurnKeys.put(email, turnKey);
        return turnKey;
    }

    public synchronized boolean validateTurnKey(String email, String turnKey) {
        String expected = playerTurnKeys.get(email);
        return expected == null || expected.equals(turnKey);
    }

    public synchronized boolean submitMove(String email, String moveData, String turnKey) {
        if (!started || !isPlayerTurn(email) || !validateTurnKey(email, turnKey)) return false;

        int turnNum = currentTurnIndex.get();
        movesLog.put(turnNum, moveData);
        playerTurnKeys.remove(email);
        updateLastMove(email);

        int roll = 1;
        try {
            roll = Integer.parseInt(moveData.replaceAll("[^0-9]", ""));
            if (roll < 1 || roll > 6) roll = (int) (Math.random() * 6) + 1;
        } catch (Exception ignored) {
            roll = (int) (Math.random() * 6) + 1;
        }
        int currentPos = playerPositions.getOrDefault(email, 1);
        int stepPos = currentPos + roll;
        if (stepPos > 100) stepPos = currentPos;
        int newPos = board.resolveTile(stepPos);
        playerPositions.put(email, newPos);

        boolean won = (newPos >= 100);
        String nextPlayer = null;
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            ranks.put(email, 1);
            int r = 2;
            for (String p : turnOrder) {
                if (!p.equals(email)) ranks.put(p, r++);
            }
            endGame(ranks);
        } else {
            int nextTurn = (turnNum + 1) % turnOrder.size();
            currentTurnIndex.set(nextTurn);
            nextPlayer = getCurrentTurnPlayer();
        }

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"move\","
                + "\"turn\":" + turnNum + ","
                + "\"player\":\"" + email + "\","
                + "\"roll\":" + roll + ","
                + "\"from\":" + currentPos + ","
                + "\"to\":" + newPos + ","
                + "\"won\":" + won + ","
                + "\"move\":\"" + moveData + "\","
                + "\"nextPlayer\":\"" + (nextPlayer != null ? nextPlayer : "") + "\""
                + "}"
        );

        if (!won && "RobinBot".equals(nextPlayer)) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1200);
                    if (started && "RobinBot".equals(getCurrentTurnPlayer())) {
                        int botRoll = (int) (Math.random() * 6) + 1;
                        submitMove("RobinBot", String.valueOf(botRoll));
                    }
                } catch (Exception ignored) {}
            });
        }

        return true;
    }

    public synchronized boolean submitMove(String email, String moveData) {
        String key = playerTurnKeys.get(email);
        return submitMove(email, moveData, key != null ? key : "");
    }

    public synchronized boolean removePlayer(String email) {
        if (email == null) return false;
        players.remove(email);
        playerPositions.remove(email);
        turnOrder.remove(email);
        playerTurnKeys.remove(email);
        lastMoveTimestamps.remove(email);
        return true;
    }

    public Map<String, Integer> getPlayerPositions() {
        return Collections.unmodifiableMap(playerPositions);
    }

    public void setPlayerPosition(String email, int pos) {
        playerPositions.put(email, pos);
    }

    public synchronized void updateLastMove(String email) {
        if (isPlayerTurn(email)) {
            lastMoveTimestamps.put(email, System.currentTimeMillis());
        }
    }

    public synchronized boolean hasTimedOut(String email) {
        Long last = lastMoveTimestamps.get(email);
        return last != null && System.currentTimeMillis() - last > TURN_TIMEOUT_MS;
    }

    public synchronized boolean currentPlayerTimedOut() {
        String current = getCurrentTurnPlayer();
        Long last = lastMoveTimestamps.get(current);
        return last != null && System.currentTimeMillis() - last > TURN_TIMEOUT_MS;
    }

    public synchronized void endGame(Map<String, Integer> finalRanks) {
        this.playerRanks.putAll(finalRanks);
        assignXPandPayouts();
        this.started = false;
        persistGameResult();
        RealTimeDispatcher.publish(sessionId,
                "{\"type\":\"end\",\"results\":" + resultsJson() + "}");
    }

    private void assignXPandPayouts() {
        int playersCount = players.size();
        int[] xpDistribution;
        int[] payoutDistribution;
        int houseRake;

        if (playersCount == 1) {
            houseRake = 0;
            xpDistribution = new int[]{20};
            payoutDistribution = new int[]{wagerAmount};
        } else if (playersCount == 2) {
            houseRake = Math.max(5, wagerAmount / 10);
            xpDistribution = new int[]{25, 10};
            payoutDistribution = new int[]{(wagerAmount * 2) - houseRake, 0};
        } else if (playersCount == 3) {
            houseRake = 35;
            xpDistribution = new int[]{30, 15, 5};
            payoutDistribution = new int[]{100, 15, 0};
        } else if (playersCount == 4) {
            houseRake = 50;
            xpDistribution = new int[]{40, 20, 10, 5};
            payoutDistribution = new int[]{115, 20, 5, 0};
        } else {
            return;
        }

        playerRanks.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEachOrdered(entry -> {
                    String email = entry.getKey();
                    int rank = entry.getValue();
                    int idx = rank - 1;

                    int xp = (idx < xpDistribution.length) ? xpDistribution[idx] : 0;
                    int payout = (idx < payoutDistribution.length) ? payoutDistribution[idx] : 0;

                    playerXP.put(email, xp);
                    WalletController.credit(email, payout);

                    LedgerManager.log(new LedgerEntry(
                            email,
                            LedgerEntry.Type.WIN,
                            payout,
                            "Winnings for game " + sessionId + ", Rank: " + rank
                    ));
                });

        LedgerManager.log(new LedgerEntry(
                "HOUSE",
                LedgerEntry.Type.RAKE,
                houseRake,
                "House rake for game " + sessionId
        ));
    }

    private void persistGameResult() {
        GameResult result = new GameResult(
                sessionId,
                boardSeed,
                LocalDateTime.now(),
                new HashMap<>(playerRanks),
                new HashMap<>(playerXP),
                new LinkedHashMap<>(movesLog),
                players.keySet()
        );
        GameResultLogger.save(result);
    }

    public Collection<Player> getPlayers() {
        return players.values();
    }

    public Map<String, Integer> getPlayerXP() {
        return Collections.unmodifiableMap(playerXP);
    }

    public Map<String, Integer> getFinalRanks() {
        return Collections.unmodifiableMap(playerRanks);
    }

    public Map<Integer, String> getMovesLog() {
        return Collections.unmodifiableMap(movesLog);
    }

    private String playerListJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < turnOrder.size(); i++) {
            sb.append("\"").append(turnOrder.get(i)).append("\"");
            if (i < turnOrder.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String resultsJson() {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (String email : playerRanks.keySet()) {
            sb.append("\"").append(email).append("\":{")
              .append("\"rank\":").append(playerRanks.get(email)).append(",")
              .append("\"xp\":").append(playerXP.getOrDefault(email, 0))
              .append("}");
            if (++i < playerRanks.size()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    public String getCurrentPlayer() {
        return getCurrentTurnPlayer();
    }
}
