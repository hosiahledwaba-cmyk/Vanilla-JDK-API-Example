package com.robinhood.engine.snakesandladders;

import com.robinhood.db.audit.GameAuditLogger;
import com.robinhood.engine.Game;
import com.robinhood.engine.GameState;
import com.robinhood.engine.core.Dice;
import com.robinhood.engine.core.DiceInput;
import com.robinhood.engine.core.Player;
import com.robinhood.engine.core.TurnManager;

import java.util.*;

public class SnakesAndLaddersGame implements Game {
    private final Board board = new Board();
    private final Map<String, Player> playerMap = new HashMap<>();
    private TurnManager turnManager;
    private boolean gameOver = false;
    private String winnerId;
    private final GameAuditLogger auditLogger;
    private final String sessionCode;

    public SnakesAndLaddersGame() {
        this("default", null);
    }

    public SnakesAndLaddersGame(String sessionCode, GameAuditLogger auditLogger) {
        this.sessionCode = sessionCode;
        this.auditLogger = auditLogger;
    }

    @Override
    public void start(List<Player> players) {
        for (Player p : players) {
            p.setPosition(1);
            playerMap.put(p.getId(), p);
        }
        turnManager = new TurnManager(players);
        if (auditLogger != null) {
            auditLogger.logSessionStart(
                "snakes_and_ladders",
                sessionCode,
                players.stream().map(Player::getId).toList(),
                board.serializeBoard()
            );
        }
    }

    @Override
    public GameState move(String playerId, Object moveData) {
        if (gameOver) return buildState("Game has already ended.");
        if (turnManager == null || !turnManager.getCurrentPlayer().getId().equals(playerId)) {
            return buildState("Not your turn.");
        }

        try {
            DiceInput input = (DiceInput) moveData;
            int roll = Dice.roll(input);
            Player player = playerMap.get(playerId);
            if (player == null) return buildState("Player not found: " + playerId);

            int oldPos = player.getPosition();
            int newPos = oldPos + roll;
            if (newPos > 100) {
                newPos = oldPos; // no movement
            } else {
                newPos = board.resolveTile(newPos);
            }

            player.setPosition(newPos);

            if (auditLogger != null) {
                auditLogger.logPlayerAction(
                    sessionCode,
                    playerId,
                    "roll",
                    roll,
                    oldPos,
                    newPos,
                    input.angle(),
                    input.intensity()
                );
            }

            if (board.isWinningPosition(newPos)) {
                gameOver = true;
                winnerId = playerId;
                return buildState("Player " + player.getName() + " wins!");
            }

            turnManager.nextTurn();
            return buildState(player.getName() + " rolled a " + roll + ".");
        } catch (Exception e) {
            return buildState("Invalid move data: " + e.getMessage());
        }
    }

    /**
     * Helper move method used by session managers with discrete roll and physics parameters.
     */
    public int move(String playerId, int diceRoll, double angle, double intensity) {
        if (gameOver) return -1;
        if (turnManager == null || !turnManager.getCurrentPlayer().getId().equals(playerId)) {
            return -1;
        }
        Player player = playerMap.get(playerId);
        if (player == null) return -1;

        int oldPos = player.getPosition();
        int newPos = oldPos + diceRoll;
        if (newPos > 100) {
            newPos = oldPos;
        } else {
            newPos = board.resolveTile(newPos);
        }

        player.setPosition(newPos);

        if (auditLogger != null) {
            auditLogger.logPlayerAction(sessionCode, playerId, "roll", diceRoll, oldPos, newPos, angle, intensity);
        }

        if (board.isWinningPosition(newPos)) {
            gameOver = true;
            winnerId = playerId;
        } else {
            turnManager.nextTurn();
        }

        return newPos;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public Map<String, Object> getBoardConfig() {
        return board.serializeBoard();
    }

    @Override
    public GameState getGameState() {
        return buildState("Game state requested.");
    }

    @Override
    public boolean isOver() {
        return gameOver;
    }

    private GameState buildState(String message) {
        Map<String, Object> state = new HashMap<>();

        List<Map<String, Object>> players = new ArrayList<>();
        if (turnManager != null) {
            for (Player p : turnManager.getPlayers()) {
                players.add(Map.of(
                    "id", p.getId(),
                    "name", p.getName(),
                    "position", p.getPosition()
                ));
            }
        }

        state.put("players", players);
        state.put("board", board.serializeBoard());
        String current = (turnManager != null && turnManager.getCurrentPlayer() != null) ?
                turnManager.getCurrentPlayer().getId() : "";
        state.put("currentTurn", current);

        return new GameState(
            gameOver,
            current,
            state,
            winnerId,
            message
        );
    }
}
