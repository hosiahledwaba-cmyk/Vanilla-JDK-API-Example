package com.robinhood.engine.uno;

import com.robinhood.engine.Game;
import com.robinhood.engine.GameState;
import com.robinhood.engine.core.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UnoGame implements Game {
    private final UnoDeck deck = new UnoDeck();
    private final List<UnoCard> discardPile = new ArrayList<>();
    private final Map<String, List<UnoCard>> hands = new ConcurrentHashMap<>();
    private final List<Player> players = new ArrayList<>();

    private int currentTurnIndex = 0;
    private int direction = 1; // 1 for clockwise, -1 for counter-clockwise
    private UnoCard.Color activeColor = UnoCard.Color.RED;
    private String activeSymbol = "7";
    private boolean gameOver = false;
    private String winnerId = null;
    private String lastActionMessage = "Uno match started";

    public UnoGame() {}

    @Override
    public synchronized void start(List<Player> playersList) {
        if (playersList == null || playersList.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required.");
        }
        this.players.clear();
        this.players.addAll(playersList);
        this.hands.clear();
        this.discardPile.clear();
        this.deck.reset();
        this.currentTurnIndex = 0;
        this.direction = 1;
        this.gameOver = false;
        this.winnerId = null;

        // Deal 7 cards to each player
        for (Player p : players) {
            List<UnoCard> hand = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                UnoCard c = deck.draw();
                if (c != null) hand.add(c);
            }
            hands.put(p.getId(), hand);
        }

        // Flip top card for discard pile (avoid starting with Wild Draw 4)
        UnoCard top = deck.draw();
        while (top != null && top.getType() == UnoCard.Type.WILD_DRAW_FOUR) {
            discardPile.add(top);
            top = deck.draw();
        }
        if (top == null) top = new UnoCard(UnoCard.Color.RED, UnoCard.Type.NUMBER, 7, "INIT_RED_7");
        discardPile.add(top);

        activeColor = (top.getColor() != UnoCard.Color.WILD) ? top.getColor() : UnoCard.Color.RED;
        activeSymbol = top.getSymbol();
        lastActionMessage = "Match started with top card: " + top;
    }

    public synchronized Map<String, Object> playCard(String playerId, String cardId, String declaredColorStr) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver) {
            result.put("success", false);
            result.put("error", "Game has ended");
            return result;
        }

        if (!getCurrentTurnPlayer().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        List<UnoCard> hand = hands.get(playerId);
        if (hand == null) {
            result.put("success", false);
            result.put("error", "Player not in game");
            return result;
        }

        UnoCard cardToPlay = null;
        for (UnoCard c : hand) {
            if (c.getId().equals(cardId)) {
                cardToPlay = c;
                break;
            }
        }

        if (cardToPlay == null) {
            result.put("success", false);
            result.put("error", "Card not in hand");
            return result;
        }

        if (!cardToPlay.canPlayOn(activeColor, activeSymbol)) {
            result.put("success", false);
            result.put("error", "Cannot play " + cardToPlay + " on " + activeColor + " " + activeSymbol);
            return result;
        }

        // Remove from hand, add to discard
        hand.remove(cardToPlay);
        discardPile.add(cardToPlay);

        // Resolve Color and Symbol
        if (cardToPlay.isWild()) {
            UnoCard.Color declared = UnoCard.Color.RED;
            if (declaredColorStr != null && !declaredColorStr.isBlank()) {
                try {
                    declared = UnoCard.Color.valueOf(declaredColorStr.toUpperCase());
                    if (declared == UnoCard.Color.WILD) declared = UnoCard.Color.RED;
                } catch (Exception ignored) {}
            }
            activeColor = declared;
            activeSymbol = cardToPlay.getSymbol();
        } else {
            activeColor = cardToPlay.getColor();
            activeSymbol = cardToPlay.getSymbol();
        }

        // Check Win
        if (hand.isEmpty()) {
            gameOver = true;
            winnerId = playerId;
            lastActionMessage = playerId + " played their last card and WON THE UNO MATCH!";
            result.put("success", true);
            result.put("won", true);
            result.put("nextPlayer", "");
            return result;
        }

        // Apply Action Card Effects
        int step = 1;
        String actionNotice = "";

        if (cardToPlay.getType() == UnoCard.Type.REVERSE) {
            direction = -direction;
            if (players.size() == 2) {
                step = 2; // In 2-player Uno, Reverse acts as a Skip
                actionNotice = " (Reversed turn back to " + playerId + ")";
            } else {
                actionNotice = " (Turn order reversed)";
            }
        } else if (cardToPlay.getType() == UnoCard.Type.SKIP) {
            step = 2; // Skip next player
            String skipped = getPlayerAtStep(1);
            actionNotice = " (Skipped " + skipped + ")";
        } else if (cardToPlay.getType() == UnoCard.Type.DRAW_TWO) {
            step = 2; // Target draws 2 and is skipped
            String target = getPlayerAtStep(1);
            drawCardsForPlayer(target, 2);
            actionNotice = " (" + target + " drew 2 cards and is skipped)";
        } else if (cardToPlay.getType() == UnoCard.Type.WILD_DRAW_FOUR) {
            step = 2; // Target draws 4 and is skipped
            String target = getPlayerAtStep(1);
            drawCardsForPlayer(target, 4);
            actionNotice = " (Called " + activeColor + "! " + target + " drew 4 cards and is skipped)";
        } else if (cardToPlay.getType() == UnoCard.Type.WILD) {
            actionNotice = " (Called " + activeColor + "!)";
        }

        lastActionMessage = playerId + " played " + cardToPlay + actionNotice;

        advanceTurn(step);
        result.put("success", true);
        result.put("won", false);
        result.put("nextPlayer", getCurrentTurnPlayer());
        return result;
    }

    private void drawCardsForPlayer(String playerId, int count) {
        List<UnoCard> hand = hands.get(playerId);
        if (hand == null) return;
        for (int i = 0; i < count; i++) {
            if (deck.isEmpty()) deck.reshuffleFromDiscard(discardPile);
            UnoCard drawn = deck.draw();
            if (drawn != null) hand.add(drawn);
        }
    }

    private String getPlayerAtStep(int step) {
        if (players.isEmpty()) return "";
        int targetIdx = Math.floorMod(currentTurnIndex + (step * direction), players.size());
        return players.get(targetIdx).getId();
    }

    private void advanceTurn(int step) {
        if (players.isEmpty()) return;
        currentTurnIndex = Math.floorMod(currentTurnIndex + (step * direction), players.size());
    }

    public synchronized Map<String, Object> drawCard(String playerId) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver || !getCurrentTurnPlayer().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        List<UnoCard> hand = hands.get(playerId);
        if (hand == null) {
            result.put("success", false);
            result.put("error", "Player not in game");
            return result;
        }

        if (deck.isEmpty()) {
            deck.reshuffleFromDiscard(discardPile);
        }

        UnoCard drawn = deck.draw();
        if (drawn != null) {
            hand.add(drawn);
            lastActionMessage = playerId + " drew a card from the deck";
            result.put("success", true);
            result.put("card", drawn.toJson());
            result.put("canPlay", drawn.canPlayOn(activeColor, activeSymbol));
        } else {
            result.put("success", false);
            result.put("error", "Deck is empty");
        }
        return result;
    }

    public synchronized Map<String, Object> passTurn(String playerId) {
        Map<String, Object> result = new HashMap<>();
        if (gameOver || !getCurrentTurnPlayer().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn");
            return result;
        }

        lastActionMessage = playerId + " passed their turn";
        advanceTurn(1);
        result.put("success", true);
        result.put("nextPlayer", getCurrentTurnPlayer());
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
                String color = map.containsKey("color") ? String.valueOf(map.get("color")) : null;
                playCard(playerId, cardId, color);
            }
        }
        return getGameState();
    }

    public UnoCard getTopCard() {
        if (discardPile.isEmpty()) return null;
        return discardPile.get(discardPile.size() - 1);
    }

    public UnoCard.Color getActiveColor() { return activeColor; }
    public String getActiveSymbol() { return activeSymbol; }

    public List<UnoCard> getHand(String playerId) {
        return hands.getOrDefault(playerId, Collections.emptyList());
    }

    public List<UnoCard> getPlayableCards(String playerId) {
        List<UnoCard> hand = getHand(playerId);
        List<UnoCard> playable = new ArrayList<>();
        for (UnoCard c : hand) {
            if (c.canPlayOn(activeColor, activeSymbol)) {
                playable.add(c);
            }
        }
        return playable;
    }

    public String getCurrentTurnPlayer() {
        if (players.isEmpty() || currentTurnIndex < 0 || currentTurnIndex >= players.size()) return "";
        return players.get(currentTurnIndex).getId();
    }

    public String getWinnerId() { return winnerId; }

    @Override
    public boolean isOver() { return gameOver; }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        UnoCard top = getTopCard();
        sb.append("\"discardTop\":").append(top != null ? top.toJson() : "null").append(",");
        sb.append("\"activeColor\":\"").append(activeColor.name()).append("\",");
        sb.append("\"activeHex\":\"").append(activeColor.getHex()).append("\",");
        sb.append("\"activeSymbol\":\"").append(activeSymbol).append("\",");
        sb.append("\"currentTurn\":\"").append(getCurrentTurnPlayer()).append("\",");
        sb.append("\"drawDeckCount\":").append(deck.size()).append(",");
        sb.append("\"direction\":").append(direction).append(",");
        sb.append("\"gameOver\":").append(gameOver).append(",");
        sb.append("\"winnerId\":").append(winnerId != null ? "\"" + winnerId + "\"" : "null").append(",");
        sb.append("\"lastAction\":\"").append(lastActionMessage.replace("\"", "\\\"")).append("\",");

        // Card counts for all players
        sb.append("\"cardCounts\":{");
        int count = 0;
        for (Map.Entry<String, List<UnoCard>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().size());
        }
        sb.append("},");

        // Hands
        sb.append("\"hands\":{");
        count = 0;
        for (Map.Entry<String, List<UnoCard>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":[");
            int cCount = 0;
            for (UnoCard c : entry.getValue()) {
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
