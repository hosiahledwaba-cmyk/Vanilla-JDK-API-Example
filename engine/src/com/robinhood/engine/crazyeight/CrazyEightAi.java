package com.robinhood.engine.crazyeight;

import java.util.*;

public class CrazyEightAi {

    public static class MoveDecision {
        public final String action; // "play", "draw", "pass"
        public final String cardId;
        public final String declaredSuit;
        public final String message;

        public MoveDecision(String action, String cardId, String declaredSuit, String message) {
            this.action = action;
            this.cardId = cardId;
            this.declaredSuit = declaredSuit;
            this.message = message;
        }
    }

    public static MoveDecision decideMove(CrazyEightGame game, String botId) {
        List<Card> hand = game.getHand(botId);
        if (hand.isEmpty()) {
            return new MoveDecision("pass", null, null, "No cards");
        }

        Card.Suit activeSuit = game.getActiveSuit();
        Card.Rank activeRank = game.getActiveRank();

        List<Card> regularPlayable = new ArrayList<>();
        Card eightCard = null;

        for (Card c : hand) {
            if (c.isEight()) {
                if (eightCard == null) eightCard = c;
            } else if (c.canPlayOn(activeSuit, activeRank)) {
                regularPlayable.add(c);
            }
        }

        // 1. Play regular matching card if available (save 8s)
        if (!regularPlayable.isEmpty()) {
            // Pick card with highest value
            regularPlayable.sort((a, b) -> Integer.compare(b.getRank().getValue(), a.getRank().getValue()));
            Card chosen = regularPlayable.get(0);
            return new MoveDecision("play", chosen.getId(), null, botId + " played " + chosen);
        }

        // 2. If no regular card, play an 8 and declare best suit
        if (eightCard != null) {
            Card.Suit bestSuit = findBestSuitInHand(hand, eightCard);
            return new MoveDecision("play", eightCard.getId(), bestSuit.name(), botId + " played 8 and called " + bestSuit);
        }

        // 3. Otherwise must draw
        return new MoveDecision("draw", null, null, botId + " draws a card");
    }

    private static Card.Suit findBestSuitInHand(List<Card> hand, Card excludeEight) {
        Map<Card.Suit, Integer> counts = new HashMap<>();
        for (Card.Suit s : Card.Suit.values()) counts.put(s, 0);

        for (Card c : hand) {
            if (c != excludeEight && !c.isEight()) {
                counts.put(c.getSuit(), counts.get(c.getSuit()) + 1);
            }
        }

        Card.Suit best = Card.Suit.HEARTS;
        int max = -1;
        for (Map.Entry<Card.Suit, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
