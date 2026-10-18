package com.robinhood.engine.crazyeight;

import com.robinhood.engine.Game;
import com.robinhood.engine.GameState;
import com.robinhood.engine.core.Player;
import com.robinhood.engine.core.TurnManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CrazyEightGame implements Game {
    private final Deck deck = new Deck();
    private final List<Card> discardPile = new ArrayList<>();
    private final Map<String, List<Card>> hands = new ConcurrentHashMap<>();
    private final List<Player> players = new ArrayList<>();
    private TurnManager turnManager;

    private Card.Suit activeSuit;
    private Card.Rank activeRank;
    private boolean gameOver = false;
    private String winnerId = null;
    private String lastActionMessage = "Game started";

    public CrazyEightGame() {}

    @Override
    public void start(List<Player> playersList) {
        if (playersList == null || playersList.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required.");
        }
        this.players.clear();
        this.players.addAll(playersList);
        this.hands.clear();
        this.discardPile.clear();
        this.deck.reset();
        this.gameOver = false;
        this.winnerId = null;

        // Deal 5 cards each (7 if 2 players)
        int dealCount = (players.size() == 2) ? 7 : 5;
        for (Player p : players) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < dealCount; i++) {
                Card c = deck.draw();
                if (c != null) hand.add(c);
            }
            hands.put(p.getId(), hand);
        }

        // Flip top card for discard pile
        Card top = deck.draw();
        while (top != null && top.isEight()) {
            // Re-deal top if it is an 8
            discardPile.add(top);
            top = deck.draw();
        }
        if (top == null) top = new Card(Card.Suit.HEARTS, Card.Rank.SEVEN);
        discardPile.add(top);

        activeSuit = top.getSuit();
        activeRank = top.getRank();
        turnManager = new TurnManager(players);
        lastActionMessage = "Game initialized with top card: " + top;
    }

    public synchronized Map<String, Object> playCard(String playerId, String cardId, String declaredSuitStr) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver) {
            result.put("success", false);
            result.put("error", "Game has already ended");
            return result;
        }

        if (turnManager == null || !turnManager.getCurrentPlayer().getId().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        List<Card> hand = hands.get(playerId);
        if (hand == null) {
            result.put("success", false);
            result.put("error", "Player not in game");
            return result;
        }

        Card cardToPlay = null;
        for (Card c : hand) {
            if (c.getId().equals(cardId)) {
                cardToPlay = c;
                break;
            }
        }

        if (cardToPlay == null) {
            result.put("success", false);
            result.put("error", "Card not in hand: " + cardId);
            return result;
        }

        if (!cardToPlay.canPlayOn(activeSuit, activeRank)) {
            result.put("success", false);
            result.put("error", "Card cannot be played on " + activeRank + " of " + activeSuit);
            return result;
        }

        // Play card
        hand.remove(cardToPlay);
        discardPile.add(cardToPlay);

        if (cardToPlay.isEight()) {
            Card.Suit declared = Card.Suit.HEARTS;
            if (declaredSuitStr != null && !declaredSuitStr.isBlank()) {
                try {
                    declared = Card.Suit.valueOf(declaredSuitStr.toUpperCase());
                } catch (Exception ignored) {}
            } else {
                declared = cardToPlay.getSuit();
            }
            activeSuit = declared;
            activeRank = Card.Rank.EIGHT;
            lastActionMessage = playerId + " played 8 of " + cardToPlay.getSuit() + " and called " + declared;
        } else {
            activeSuit = cardToPlay.getSuit();
            activeRank = cardToPlay.getRank();
            lastActionMessage = playerId + " played " + cardToPlay;
        }

        // Check win condition
        if (hand.isEmpty()) {
            gameOver = true;
            winnerId = playerId;
            lastActionMessage = playerId + " discarded all cards and WON THE GAME!";
            result.put("success", true);
            result.put("won", true);
            result.put("nextPlayer", "");
            return result;
        }

        turnManager.nextTurn();
        result.put("success", true);
        result.put("won", false);
        result.put("nextPlayer", turnManager.getCurrentPlayer().getId());
        return result;
    }

    public synchronized Map<String, Object> drawCard(String playerId) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver) {
            result.put("success", false);
            result.put("error", "Game has already ended");
            return result;
        }

        if (turnManager == null || !turnManager.getCurrentPlayer().getId().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        List<Card> hand = hands.get(playerId);
        if (hand == null) {
            result.put("success", false);
            result.put("error", "Player not in game");
            return result;
        }

        if (deck.isEmpty()) {
            deck.reshuffleFromDiscard(discardPile);
        }

        Card drawn = deck.draw();
        if (drawn != null) {
            hand.add(drawn);
            lastActionMessage = playerId + " drew a card";
            result.put("success", true);
            result.put("card", drawn.toJson());
            result.put("canPlay", drawn.canPlayOn(activeSuit, activeRank));
        } else {
            result.put("success", false);
            result.put("error", "Deck is empty");
        }
        return result;
    }

    public synchronized Map<String, Object> passTurn(String playerId) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver || turnManager == null || !turnManager.getCurrentPlayer().getId().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        lastActionMessage = playerId + " passed their turn";
        turnManager.nextTurn();
        result.put("success", true);
        result.put("nextPlayer", turnManager.getCurrentPlayer().getId());
        return result;
    }

    @Override
    public GameState move(String playerId, Object moveData) {
        if (moveData instanceof Map<?, ?> map) {
            String action = map.containsKey("action") ? String.valueOf(map.get("action")) : "play";
            if ("draw".equalsIgnoreCase(action)) {
                drawCard(playerId);
            } else if ("pass".equalsIgnoreCase(action)) {
                passTurn(playerId);
            } else {
                String cardId = String.valueOf(map.get("cardId"));
                String suit = map.containsKey("suit") ? String.valueOf(map.get("suit")) : null;
                playCard(playerId, cardId, suit);
            }
        }
        return getGameState();
    }

    public Card getTopCard() {
        if (discardPile.isEmpty()) return null;
        return discardPile.get(discardPile.size() - 1);
    }

    public Card.Suit getActiveSuit() {
        return activeSuit;
    }

    public Card.Rank getActiveRank() {
        return activeRank;
    }

    public List<Card> getHand(String playerId) {
        return hands.getOrDefault(playerId, Collections.emptyList());
    }

    public List<Card> getPlayableCards(String playerId) {
        List<Card> hand = getHand(playerId);
        List<Card> playable = new ArrayList<>();
        for (Card c : hand) {
            if (c.canPlayOn(activeSuit, activeRank)) {
                playable.add(c);
            }
        }
        return playable;
    }

    public String getCurrentTurnPlayer() {
        return (turnManager != null && turnManager.getCurrentPlayer() != null) ?
                turnManager.getCurrentPlayer().getId() : "";
    }

    public String getWinnerId() {
        return winnerId;
    }

    @Override
    public boolean isOver() {
        return gameOver;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        Card top = getTopCard();
        sb.append("\"discardTop\":").append(top != null ? top.toJson() : "null").append(",");
        sb.append("\"activeSuit\":\"").append(activeSuit != null ? activeSuit.name() : "").append("\",");
        sb.append("\"activeRank\":\"").append(activeRank != null ? activeRank.name() : "").append("\",");
        sb.append("\"currentTurn\":\"").append(getCurrentTurnPlayer()).append("\",");
        sb.append("\"drawDeckCount\":").append(deck.size()).append(",");
        sb.append("\"gameOver\":").append(gameOver).append(",");
        sb.append("\"winnerId\":").append(winnerId != null ? "\"" + winnerId + "\"" : "null").append(",");
        sb.append("\"lastAction\":\"").append(lastActionMessage.replace("\"", "\\\"")).append("\",");

        // Player card counts
        sb.append("\"cardCounts\":{");
        int count = 0;
        for (Map.Entry<String, List<Card>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().size());
        }
        sb.append("},");

        // All hands (for local player/bot view)
        sb.append("\"hands\":{");
        count = 0;
        for (Map.Entry<String, List<Card>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":[");
            int cCount = 0;
            for (Card c : entry.getValue()) {
                if (cCount++ > 0) sb.append(",");
                sb.append(c.toJson());
            }
            sb.append("]");
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    @Override
    public GameState getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("currentTurn", getCurrentTurnPlayer());
        state.put("gameOver", gameOver);
        state.put("winnerId", winnerId);
        return new GameState(gameOver, getCurrentTurnPlayer(), state, winnerId, lastActionMessage);
    }
}
