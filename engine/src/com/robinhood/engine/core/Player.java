package com.robinhood.engine.core;

/**
 * Represents a player in any game.
 */
public class Player {
    private final String id;
    private final String name;
    private int position;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.position = 1; // Start position for most board games
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getPosition() { return position; }

    public void setPosition(int position) {
        if (position < 1) throw new IllegalArgumentException("Position must be >= 1.");
        this.position = position;
    }

    @Override
    public String toString() {
        return name + " (" + id + ") @ " + position;
    }
}
