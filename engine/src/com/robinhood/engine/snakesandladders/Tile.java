package com.robinhood.engine.snakesandladders;

public class Tile {
    private final int position;

    public Tile(int position) {
        if (position < 1 || position > 100)
            throw new IllegalArgumentException("Tile position must be 1-100.");
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
