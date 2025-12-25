package com.robinhood.engine.snakesandladders;

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
		auditLogger.logSessionStart(
			"snakes_and_ladders",
			sessionCode,
			players.stream().map(Player::getId).toList(),
			board.serializeBoard()
		);
		
    }

    @Override
    public GameState move(String playerId, Object moveData) {
        if (gameOver) return buildState("Game has already ended.");
        if (!turnManager.getCurrentPlayer().getId().equals(playerId)) {
            return buildState("Not your turn.");
        }

        try {
            DiceInput input = (DiceInput) moveData;
            int roll = Dice.roll(input);
            Player player = playerMap.get(playerId);

            int newPos = player.getPosition() + roll;
            if (newPos > 100) {
                newPos = player.getPosition(); // no movement
            } else {
                newPos = board.resolveTile(newPos);
            }

            player.setPosition(newPos);

            if (board.isWinningPosition(newPos)) {
                gameOver = true;
                winnerId = playerId;
                return buildState("🎉 Player " + player.getName() + " wins!");
            }

            turnManager.nextTurn();
            return buildState(player.getName() + " rolled a " + roll + ".");
        } catch (Exception e) {
            return buildState("⚠️ Invalid move data: " + e.getMessage());
        }
		
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

        // Players
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : turnManager.getPlayers()) {
            players.add(Map.of(
                "id", p.getId(),
                "name", p.getName(),
                "position", p.getPosition()
            ));
        }

        // Combine state
        state.put("players", players);
        state.put("board", board.serializeBoard());
        state.put("currentTurn", turnManager.getCurrentPlayer().getId());
		
		if (gameOver && winnerId != null) {
			Map<String, Integer> xpMap = calculateXP(); // build from placement logic
			Map<String, Integer> tokenMap = calculateTokenDistribution(); // from bet
			auditLogger.logSessionResult(sessionCode, winnerId, xpMap, tokenMap, houseCut);
		}


        return new GameState(
            gameOver,
            turnManager.getCurrentPlayer().getId(),
            state,
            winnerId,
            message
        );
    }
}
