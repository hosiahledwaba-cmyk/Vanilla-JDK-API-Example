package com.robinhood.engine.crazyeight;

import java.security.SecureRandom;
import java.util.*;

public class Deck {
    private final List<Card> cards = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

    public Deck() {
        reset();
    }

    public void reset() {
        cards.clear();
        for (Card.Suit suit : Card.Suit.values()) {
            for (Card.Rank rank : Card.Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards, random);
    }

    public Card draw() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }

    public void reshuffleFromDiscard(List<Card> discardPile) {
        if (discardPile == null || discardPile.size() <= 1) return;
        Card top = discardPile.remove(discardPile.size() - 1);
        cards.addAll(discardPile);
        discardPile.clear();
        discardPile.add(top);
        shuffle();
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }
}
