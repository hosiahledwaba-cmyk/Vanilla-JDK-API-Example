package com.robinhood.engine.khasino;

import java.util.Objects;

/**
 * Represents a card in the 40-card South African Khasino deck.
 * Ranks are Ace (1) through 10. There are no face cards (Jacks, Queens, Kings).
 */
public class KhasinoCard implements Comparable<KhasinoCard> {

    public enum Suit {
        SPADES("♠", "black"),
        HEARTS("♥", "red"),
        DIAMONDS("♦", "red"),
        CLUBS("♣", "black");

        private final String symbol;
        private final String color;

        Suit(String symbol, String color) {
            this.symbol = symbol;
            this.color = color;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getColor() {
            return color;
        }
    }

    public enum Rank {
        ACE(1, "A"),
        TWO(2, "2"),
        THREE(3, "3"),
        FOUR(4, "4"),
        FIVE(5, "5"),
        SIX(6, "6"),
        SEVEN(7, "7"),
        EIGHT(8, "8"),
        NINE(9, "9"),
        TEN(10, "10");

        private final int value;
        private final String display;

        Rank(int value, String display) {
            this.value = value;
            this.display = display;
        }

        public int getValue() {
            return value;
        }

        public String getDisplay() {
            return display;
        }
    }

    private final Suit suit;
    private final Rank rank;
    private final String id;

    public KhasinoCard(Suit suit, Rank rank) {
        this.suit = Objects.requireNonNull(suit, "suit cannot be null");
        this.rank = Objects.requireNonNull(rank, "rank cannot be null");
        this.id = rank.name() + "_" + suit.name();
    }

    public Suit getSuit() {
        return suit;
    }

    public Rank getRank() {
        return rank;
    }

    public int getValue() {
        return rank.getValue();
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return rank.getDisplay();
    }

    public String getSymbol() {
        return suit.getSymbol();
    }

    public String getColor() {
        return suit.getColor();
    }

    /**
     * Ten of Diamonds is the famous "Mummy" worth 2 points.
     */
    public boolean isMummy() {
        return rank == Rank.TEN && suit == Suit.DIAMONDS;
    }

    /**
     * Two of Spades is the famous "Spy Two" worth 1 point.
     */
    public boolean isSpyTwo() {
        return rank == Rank.TWO && suit == Suit.SPADES;
    }

    /**
     * Aces are worth 1 point each.
     */
    public boolean isAce() {
        return rank == Rank.ACE;
    }

    public boolean isSpade() {
        return suit == Suit.SPADES;
    }

    @Override
    public int compareTo(KhasinoCard o) {
        if (this.getValue() != o.getValue()) {
            return Integer.compare(this.getValue(), o.getValue());
        }
        return this.suit.compareTo(o.suit);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KhasinoCard that = (KhasinoCard) o;
        return suit == that.suit && rank == that.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }

    @Override
    public String toString() {
        return rank.getDisplay() + suit.getSymbol();
    }

    public String toJson() {
        return "{" +
                "\"id\":\"" + id + "\"," +
                "\"suit\":\"" + suit.name() + "\"," +
                "\"rank\":\"" + rank.name() + "\"," +
                "\"value\":" + getValue() + "," +
                "\"display\":\"" + getDisplay() + "\"," +
                "\"symbol\":\"" + getSymbol() + "\"," +
                "\"color\":\"" + getColor() + "\"," +
                "\"isMummy\":" + isMummy() + "," +
                "\"isSpyTwo\":" + isSpyTwo() + "," +
                "\"isAce\":" + isAce() +
                "}";
    }

    public static KhasinoCard fromId(String cardId) {
        if (cardId == null || !cardId.contains("_")) return null;
        int idx = cardId.indexOf('_');
        String rStr = cardId.substring(0, idx);
        String sStr = cardId.substring(idx + 1);
        try {
            Rank r = Rank.valueOf(rStr.toUpperCase());
            Suit s = Suit.valueOf(sStr.toUpperCase());
            return new KhasinoCard(s, r);
        } catch (Exception e) {
            return null;
        }
    }
}
