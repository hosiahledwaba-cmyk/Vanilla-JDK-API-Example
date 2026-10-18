package com.robinhood.engine.khasino;

import java.util.*;

/**
 * Represents the 40-card South African Khasino deck.
 * Derived from standard 52-card deck by stripping all Jacks, Queens, and Kings.
 */
public class KhasinoDeck {

    private final List<KhasinoCard> cards = new ArrayList<>();
    private final Random random;

    public KhasinoDeck() {
        this(new Random());
    }

    public KhasinoDeck(Random random) {
        this.random = random != null ? random : new Random();
        reset();
    }

    public final void reset() {
        cards.clear();
        for (KhasinoCard.Suit suit : KhasinoCard.Suit.values()) {
            for (KhasinoCard.Rank rank : KhasinoCard.Rank.values()) {
                cards.add(new KhasinoCard(suit, rank));
            }
        }
    }

    public void shuffle() {
        // Fair Fisher-Yates shuffle
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            KhasinoCard temp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, temp);
        }
    }

    public KhasinoCard draw() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }

    public List<KhasinoCard> draw(int count) {
        List<KhasinoCard> drawn = new ArrayList<>();
        for (int i = 0; i < count && !cards.isEmpty(); i++) {
            drawn.add(draw());
        }
        return drawn;
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public List<KhasinoCard> getRemainingCards() {
        return Collections.unmodifiableList(cards);
    }
}
