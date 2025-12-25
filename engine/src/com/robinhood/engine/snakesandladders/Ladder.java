package com.robinhood.engine.snakesandladders;

public class Ladder {
    private final int base;
    private final int top;

    public Ladder(int base, int top) {
        if (top <= base)
            throw new IllegalArgumentException("Ladder must go up.");
        this.base = base;
        this.top = top;
    }

    public int getBase() {
        return base;
    }

    public int getTop() {
        return top;
    }
}
