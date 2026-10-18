package com.robinhood.engine.uno;

public class UnoCard {
    public enum Color {
        RED("#ef4444"),
        BLUE("#3b82f6"),
        GREEN("#10b981"),
        YELLOW("#f59e0b"),
        WILD("#8b5cf6");

        private final String hex;
        Color(String hex) { this.hex = hex; }
        public String getHex() { return hex; }
    }

    public enum Type {
        NUMBER,
        SKIP,
        REVERSE,
        DRAW_TWO,
        WILD,
        WILD_DRAW_FOUR
    }

    private final Color color;
    private final Type type;
    private final int number; // 0-9 or -1
    private final String id;

    public UnoCard(Color color, Type type, int number, String id) {
        this.color = color;
        this.type = type;
        this.number = number;
        this.id = id;
    }

    public Color getColor() { return color; }
    public Type getType() { return type; }
    public int getNumber() { return number; }
    public String getId() { return id; }

    public boolean isWild() {
        return type == Type.WILD || type == Type.WILD_DRAW_FOUR;
    }

    public String getSymbol() {
        return switch (type) {
            case NUMBER -> String.valueOf(number);
            case SKIP -> "⊘";
            case REVERSE -> "⇄";
            case DRAW_TWO -> "+2";
            case WILD -> "★";
            case WILD_DRAW_FOUR -> "+4";
        };
    }

    public boolean canPlayOn(Color activeColor, String activeSymbol) {
        if (isWild()) return true;
        if (this.color == activeColor) return true;
        return this.getSymbol().equals(activeSymbol);
    }

    public String toJson() {
        return "{" +
                "\"id\":\"" + id + "\"," +
                "\"color\":\"" + color.name() + "\"," +
                "\"hex\":\"" + color.getHex() + "\"," +
                "\"type\":\"" + type.name() + "\"," +
                "\"number\":" + number + "," +
                "\"symbol\":\"" + getSymbol() + "\"," +
                "\"isWild\":" + isWild() +
                "}";
    }

    @Override
    public String toString() {
        return (color == Color.WILD ? "" : color.name() + " ") + getSymbol();
    }
}
