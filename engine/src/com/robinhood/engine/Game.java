package com.robinhood.engine;

import com.robinhood.engine.core.Player;
import java.util.List;

/**
 * Represents a generic game that can be started, played, and queried for its state.
 */
public interface Game {

    /**
     * Initializes the game with a list of players.
     */
    void start(List<Player> players);

    /**
     * Processes a move from a player.
     * 
     * @param playerId The ID of the player making the move.
     * @param moveData Game-specific input or action.
     * @return A snapshot of the current game state.
     */
    GameState move(String playerId, Object moveData);

    /**
     * Returns the current game state for broadcasting to the UI.
     */
    GameState getGameState();

    /**
     * Returns true if the game has ended.
     */
    boolean isOver();
}
