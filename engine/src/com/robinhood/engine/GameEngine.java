package com.robinhood.engine;

import com.robinhood.engine.core.Player;
import com.robinhood.engine.snakesandladders.SnakesAndLaddersGame;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main manager for loading, running, and tracking active games.
 */
public class GameEngine {
    private static final Map<String, Game> activeGames = new ConcurrentHashMap<>();

    /**
     * Creates a new game instance and stores it by session ID.
     */
    public static Game create(GameType type, String sessionId, List<Player> players) {
        Game game = switch (type) {
            case SNAKES_AND_LADDERS -> new SnakesAndLaddersGame();
            default -> throw new UnsupportedOperationException("Game type not implemented.");
        };
        game.start(players);
        activeGames.put(sessionId, game);
        return game;
    }

    /**
     * Retrieves a game instance by its session ID.
     */
    public static Game get(String sessionId) {
        return activeGames.get(sessionId);
    }

    /**
     * Removes a completed or terminated game.
     */
    public static void remove(String sessionId) {
        activeGames.remove(sessionId);
    }
}
