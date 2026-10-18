package com.robinhood.games.snl;

import com.robinhood.engine.snakesandladders.SnakesAndLaddersGame;
import com.robinhood.db.audit.GameAuditLogger;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SNLGameSession {

    private final String sessionId;
    private final int wagerTokens;
    private final int maxPlayers;
    private final GameAuditLogger auditLogger;

    private final List<String> players = new ArrayList<>();
    private final Map<String, Integer> playerPositions = new HashMap<>();

    private SnakesAndLaddersGame gameEngine;
    private boolean gameStarted = false;
    private String currentPlayerId;
    private String winnerId;
    private int currentPlayerIndex = 0;

    private final ReentrantLock moveLock = new ReentrantLock();

    public SNLGameSession(String sessionId, int wagerTokens, int maxPlayers, GameAuditLogger auditLogger) {
        this.sessionId = sessionId;
        this.wagerTokens = wagerTokens;
        this.maxPlayers = maxPlayers;
        this.auditLogger = auditLogger;
    }

    public boolean addPlayer(String playerId) {
        if (players.size() >= maxPlayers || gameStarted || players.contains(playerId)) return false;

        players.add(playerId);
        playerPositions.put(playerId, 0);

        if (players.size() == maxPlayers) {
            startGame();
        }
        return true;
    }

    private void startGame() {
        this.gameStarted = true;
        this.currentPlayerId = players.get(0);
        this.gameEngine = new SnakesAndLaddersGame(sessionId, auditLogger);
        List<com.robinhood.engine.core.Player> enginePlayers = players.stream()
                .map(id -> new com.robinhood.engine.core.Player(id, id))
                .toList();
        this.gameEngine.start(enginePlayers);
    }

    public boolean makeMove(String playerId, int diceRoll, double angle, double intensity) {
        if (!gameStarted) return false;
        if (!playerId.equals(currentPlayerId)) return false;

        moveLock.lock();
        try {
            int oldPos = playerPositions.getOrDefault(playerId, 0);
            int newPos = gameEngine.move(playerId, diceRoll, angle, intensity);

            if (newPos == -1) return false; // invalid move (e.g., cheating)

            playerPositions.put(playerId, newPos);

            // Rotate turn
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            currentPlayerId = players.get(currentPlayerIndex);

            if (gameEngine.isGameOver()) {
                winnerId = gameEngine.getWinnerId();
                distributeWinnings();
            }

            return true;
        } finally {
            moveLock.unlock();
        }
    }

    private void distributeWinnings() {
        int totalPot = wagerTokens * players.size();
        Map<String, Integer> tokenDistribution = new HashMap<>();
        Map<String, Integer> xpDistribution = new HashMap<>();

        // Sort players by position descending (closest to 100 wins)
        List<String> sortedPlayers = new ArrayList<>(players);
        sortedPlayers.sort(Comparator.comparingInt((String p) -> playerPositions.getOrDefault(p, 0)).reversed());

        int houseCut = 0;

        if (players.size() == 3) {
            tokenDistribution.put(sortedPlayers.get(0), 100);
            tokenDistribution.put(sortedPlayers.get(1), 15);
            tokenDistribution.put(sortedPlayers.get(2), 0);
            houseCut = Math.max(0, totalPot - 115);

            xpDistribution.put(sortedPlayers.get(0), 30);
            xpDistribution.put(sortedPlayers.get(1), 15);
            xpDistribution.put(sortedPlayers.get(2), 5);
        } else if (players.size() == 4) {
            tokenDistribution.put(sortedPlayers.get(0), 115);
            tokenDistribution.put(sortedPlayers.get(1), 20);
            tokenDistribution.put(sortedPlayers.get(2), 5);
            tokenDistribution.put(sortedPlayers.get(3), 0);
            houseCut = Math.max(0, totalPot - 140);

            xpDistribution.put(sortedPlayers.get(0), 40);
            xpDistribution.put(sortedPlayers.get(1), 20);
            xpDistribution.put(sortedPlayers.get(2), 10);
            xpDistribution.put(sortedPlayers.get(3), 5);
        } else {
            int houseTake = totalPot / 5;
            houseCut = houseTake;
            int remainder = totalPot - houseTake;
            int share = remainder / Math.max(1, players.size());
            for (String p : players) {
                tokenDistribution.put(p, share);
                xpDistribution.put(p, 10);
            }
        }

        if (auditLogger != null) {
            auditLogger.logSessionResult(sessionId, winnerId, xpDistribution, tokenDistribution, houseCut);
        }
    }

    public Map<String, Object> getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", sessionId);
        state.put("players", players);
        state.put("positions", playerPositions);
        state.put("currentPlayer", currentPlayerId);
        state.put("winner", winnerId);
        state.put("gameStarted", gameStarted);
        state.put("wagerTokens", wagerTokens);
        state.put("board", gameEngine != null ? gameEngine.getBoardConfig() : null);
        return state;
    }
}
