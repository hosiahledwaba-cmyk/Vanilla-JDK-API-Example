package com.robinhood.engine.uno;

import java.security.SecureRandom;
import java.util.*;

public class UnoDeck {
    private final List<UnoCard> cards = new ArrayList<>();
    private final SecureRandom random = new SecureRandom();

    public UnoDeck() {
        reset();
    }

    public void reset() {
        cards.clear();
        int idCounter = 1;

        // 4 Colors
        UnoCard.Color[] standardColors = new UnoCard.Color[]{
                UnoCard.Color.RED, UnoCard.Color.BLUE, UnoCard.Color.GREEN, UnoCard.Color.YELLOW
        };

        for (UnoCard.Color color : standardColors) {
            // One '0' per color
            cards.add(new UnoCard(color, UnoCard.Type.NUMBER, 0, color.name() + "_0_" + (idCounter++)));

            // Two of '1' through '9' per color
            for (int num = 1; num <= 9; num++) {
                cards.add(new UnoCard(color, UnoCard.Type.NUMBER, num, color.name() + "_" + num + "_A_" + (idCounter++)));
                cards.add(new UnoCard(color, UnoCard.Type.NUMBER, num, color.name() + "_" + num + "_B_" + (idCounter++)));
            }

            // Two of each action card per color
            for (int i = 1; i <= 2; i++) {
                cards.add(new UnoCard(color, UnoCard.Type.SKIP, -1, color.name() + "_SKIP_" + i + "_" + (idCounter++)));
                cards.add(new UnoCard(color, UnoCard.Type.REVERSE, -1, color.name() + "_REVERSE_" + i + "_" + (idCounter++)));
                cards.add(new UnoCard(color, UnoCard.Type.DRAW_TWO, -1, color.name() + "_DRAW2_" + i + "_" + (idCounter++)));
            }
        }

        // 4 Wild and 4 Wild Draw Four
        for (int i = 1; i <= 4; i++) {
            cards.add(new UnoCard(UnoCard.Color.WILD, UnoCard.Type.WILD, -1, "WILD_" + i + "_" + (idCounter++)));
            cards.add(new UnoCard(UnoCard.Color.WILD, UnoCard.Type.WILD_DRAW_FOUR, -1, "WILD_DRAW4_" + i + "_" + (idCounter++)));
        }

        shuffle();
    }

    public void shuffle() {
        Collections.shuffle(cards, random);
    }

    public UnoCard draw() {
        if (cards.isEmpty()) return null;
        return cards.remove(cards.size() - 1);
    }

    public void reshuffleFromDiscard(List<UnoCard> discardPile) {
        if (discardPile == null || discardPile.size() <= 1) return;
        UnoCard top = discardPile.remove(discardPile.size() - 1);
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
