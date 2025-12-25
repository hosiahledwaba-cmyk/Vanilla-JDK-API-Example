package com.robinhood.engine.core;

import java.util.List;

/**
 * Handles rotation of turns between multiple players.
 */
public class TurnManager {
    private final List<Player> players;
    private int currentIndex = 0;

    public TurnManager(List<Player> players) {
        if (players == null || players.isEmpty())
            throw new IllegalArgumentException("At least one player is required.");
        this.players = players;
    }

    public Player getCurrentPlayer() {
        return players.get(currentIndex);
    }

    public void nextTurn() {
        currentIndex = (currentIndex + 1) % players.size();
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
