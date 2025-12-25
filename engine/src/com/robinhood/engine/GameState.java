package com.robinhood.engine;

import java.util.Map;

/**
 * Represents a snapshot of the game at a particular moment.
 */
public class GameState {
    public final boolean gameOver;
    public final String currentPlayerId;
    public final Map<String, Object> state;
    public final String winnerId;
    public final String message;

    public GameState(boolean gameOver, String currentPlayerId, Map<String, Object> state, String winnerId, String message) {
        this.gameOver = gameOver;
        this.currentPlayerId = currentPlayerId;
        this.state = state;
        this.winnerId = winnerId;
        this.message = message;
    }
}
