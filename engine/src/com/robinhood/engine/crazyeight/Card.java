package com.robinhood.engine.crazyeight;

public class Card {
    public enum Suit {
        HEARTS("♥", "red"),
        DIAMONDS("♦", "red"),
        CLUBS("♣", "black"),
        SPADES("♠", "black");

        private final String symbol;
        private final String color;

        Suit(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }

        public String getSymbol() { return symbol; }
        public String getColor() { return color; }
    }

    public enum Rank {
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 50), // 50 points in Crazy Eights
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 10),
        QUEEN("Q", 10),
        KING("K", 10),
        ACE("A", 1);

        private final String display;
        private final int value;

        Rank(String display, int value) {
            this.display = display;
            this.value = value;
        }

        public String getDisplay() { return display; }
        public int getValue() { return value; }
    }

    private final Suit suit;
    private final Rank rank;
    private final String id;

    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
        this.id = rank.name() + "_" + suit.name();
    }

    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }
    public String getId() { return id; }

    public boolean isEight() {
        return rank == Rank.EIGHT;
    }

    public boolean canPlayOn(Suit activeSuit, Rank activeRank) {
        if (isEight()) return true; // 8 is always wild
        return this.suit == activeSuit || this.rank == activeRank;
    }

    public String toJson() {
        return "{" +
                "\"id\":\"" + id + "\"," +
                "\"suit\":\"" + suit.name() + "\"," +
                "\"rank\":\"" + rank.name() + "\"," +
                "\"display\":\"" + rank.getDisplay() + "\"," +
                "\"symbol\":\"" + suit.getSymbol() + "\"," +
                "\"color\":\"" + suit.getColor() + "\"," +
                "\"isEight\":" + isEight() +
                "}";
    }

    public static Card fromId(String id) {
        if (id == null || !id.contains("_")) return null;
        int idx = id.indexOf('_');
        String rStr = id.substring(0, idx);
        String sStr = id.substring(idx + 1);
        try {
            Rank r = Rank.valueOf(rStr);
            Suit s = Suit.valueOf(sStr);
            return new Card(s, r);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit == card.suit && rank == card.rank;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return rank.getDisplay() + suit.getSymbol();
    }
}
