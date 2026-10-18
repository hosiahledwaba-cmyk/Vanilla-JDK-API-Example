package com.robinhood.engine.uno;

import java.util.*;

public class UnoAi {

    public static class MoveDecision {
        public final String action; // "play", "draw", "pass"
        public final String cardId;
        public final String declaredColor;
        public final String message;

        public MoveDecision(String action, String cardId, String declaredColor, String message) {
            this.action = action;
            this.cardId = cardId;
            this.declaredColor = declaredColor;
            this.message = message;
        }
    }

    public static MoveDecision decideMove(UnoGame game, String botId) {
        List<UnoCard> hand = game.getHand(botId);
        if (hand.isEmpty()) {
            return new MoveDecision("pass", null, null, "No cards");
        }

        UnoCard.Color activeColor = game.getActiveColor();
        String activeSymbol = game.getActiveSymbol();

        List<UnoCard> playableRegular = new ArrayList<>();
        List<UnoCard> playableAction = new ArrayList<>();
        List<UnoCard> playableWild = new ArrayList<>();

        for (UnoCard c : hand) {
            if (c.canPlayOn(activeColor, activeSymbol)) {
                if (c.isWild()) {
                    playableWild.add(c);
                } else if (c.getType() == UnoCard.Type.NUMBER) {
                    playableRegular.add(c);
                } else {
                    playableAction.add(c);
                }
            }
        }

        // 1. Prefer action cards (Draw Two, Skip, Reverse) to gain advantage
        if (!playableAction.isEmpty()) {
            UnoCard chosen = playableAction.get(0);
            return new MoveDecision("play", chosen.getId(), null, botId + " played " + chosen);
        }

        // 2. Play matching regular number card
        if (!playableRegular.isEmpty()) {
            // Sort to play highest number first
            playableRegular.sort((a, b) -> Integer.compare(b.getNumber(), a.getNumber()));
            UnoCard chosen = playableRegular.get(0);
            return new MoveDecision("play", chosen.getId(), null, botId + " played " + chosen);
        }

        // 3. Play Wild card if available
        if (!playableWild.isEmpty()) {
            // Prefer regular Wild over Wild Draw Four unless necessary
            playableWild.sort((a, b) -> {
                if (a.getType() == UnoCard.Type.WILD) return -1;
                return 1;
            });
            UnoCard chosen = playableWild.get(0);
            UnoCard.Color bestColor = findBestColorInHand(hand, chosen);
            return new MoveDecision("play", chosen.getId(), bestColor.name(), botId + " played " + chosen + " and called " + bestColor);
        }

        // 4. Must draw
        return new MoveDecision("draw", null, null, botId + " draws a card from deck");
    }

    private static UnoCard.Color findBestColorInHand(List<UnoCard> hand, UnoCard excludeCard) {
        Map<UnoCard.Color, Integer> counts = new HashMap<>();
        counts.put(UnoCard.Color.RED, 0);
        counts.put(UnoCard.Color.BLUE, 0);
        counts.put(UnoCard.Color.GREEN, 0);
        counts.put(UnoCard.Color.YELLOW, 0);

        for (UnoCard c : hand) {
            if (c != excludeCard && c.getColor() != UnoCard.Color.WILD) {
                counts.put(c.getColor(), counts.getOrDefault(c.getColor(), 0) + 1);
            }
        }

        UnoCard.Color best = UnoCard.Color.RED;
        int max = -1;
        for (Map.Entry<UnoCard.Color, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }
}
