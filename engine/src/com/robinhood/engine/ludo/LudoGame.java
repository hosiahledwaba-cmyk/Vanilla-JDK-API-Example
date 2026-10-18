package com.robinhood.engine.ludo;

import com.robinhood.engine.Game;
import com.robinhood.engine.GameState;
import com.robinhood.engine.core.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete, authoritative backend rules engine for standard four-colour Ludo.
 * Supports 2, 3, or 4 players with exact roll finishing, bonus sixes, consecutive six penalties,
 * safe squares, captures, and turn auto-advancement.
 */
public class LudoGame implements Game {

    public enum TurnPhase {
        WAITING_FOR_ROLL,
        WAITING_FOR_MOVE
    }

    private final List<Player> players = new ArrayList<>();
    private final Map<String, LudoColor> playerColors = new LinkedHashMap<>();
    private final Map<String, List<LudoToken>> playerTokens = new ConcurrentHashMap<>();
    private final List<String> eventLog = new ArrayList<>();

    private int currentTurnIndex = 0;
    private TurnPhase phase = TurnPhase.WAITING_FOR_ROLL;
    private int currentRoll = 0;
    private int consecutiveSixes = 0;
    private boolean gameOver = false;
    private String winnerId = null;
    private String lastActionMessage = "Ludo match initialized.";

    public LudoGame() {}

    @Override
    public synchronized void start(List<Player> playersList) {
        if (playersList == null || playersList.isEmpty()) {
            throw new IllegalArgumentException("At least one player is required to start Ludo.");
        }
        if (playersList.size() > 4) {
            throw new IllegalArgumentException("Ludo supports at most 4 players.");
        }

        this.players.clear();
        this.players.addAll(playersList);
        this.playerColors.clear();
        this.playerTokens.clear();
        this.eventLog.clear();
        this.currentTurnIndex = 0;
        this.phase = TurnPhase.WAITING_FOR_ROLL;
        this.currentRoll = 0;
        this.consecutiveSixes = 0;
        this.gameOver = false;
        this.winnerId = null;

        // Assign active colors according to player count
        int count = players.size();
        LudoColor[] assignedColors;
        if (count == 2) {
            // Opposite colors for balanced 2-player board play
            assignedColors = new LudoColor[] { LudoColor.RED, LudoColor.YELLOW };
        } else if (count == 3) {
            assignedColors = new LudoColor[] { LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW };
        } else {
            assignedColors = new LudoColor[] { LudoColor.RED, LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE };
        }

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            LudoColor col = assignedColors[i % assignedColors.length];
            playerColors.put(p.getId(), col);

            List<LudoToken> tokens = new ArrayList<>(4);
            for (int t = 0; t < 4; t++) {
                tokens.add(new LudoToken(t, col, p.getId()));
            }
            playerTokens.put(p.getId(), tokens);
        }

        this.lastActionMessage = "Ludo match started with " + count + " players. First turn: " + getCurrentTurnPlayer();
        this.eventLog.add(this.lastActionMessage);
    }

    public synchronized String getCurrentTurnPlayer() {
        if (players.isEmpty()) return null;
        return players.get(currentTurnIndex % players.size()).getId();
    }

    public synchronized LudoColor getPlayerColor(String playerId) {
        return playerColors.get(playerId);
    }

    public synchronized TurnPhase getPhase() {
        return phase;
    }

    public synchronized int getCurrentRoll() {
        return currentRoll;
    }

    public synchronized int getConsecutiveSixes() {
        return consecutiveSixes;
    }

    @Override
    public synchronized boolean isOver() {
        return gameOver;
    }

    public synchronized String getWinnerId() {
        return winnerId;
    }

    public synchronized List<LudoToken> getTokens(String playerId) {
        return playerTokens.getOrDefault(playerId, Collections.emptyList());
    }

    public synchronized Map<String, List<LudoToken>> getAllPlayerTokens() {
        return Collections.unmodifiableMap(playerTokens);
    }

    public synchronized List<String> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public synchronized String getLastActionMessage() {
        return lastActionMessage;
    }

    /**
     * Authoritative dice roll for active player.
     */
    public synchronized Map<String, Object> rollDice(String playerId) {
        return rollDice(playerId, -1);
    }

    /**
     * Authoritative dice roll with test injection support (if forcedRoll > 0).
     */
    public synchronized Map<String, Object> rollDice(String playerId, int forcedRoll) {
        Map<String, Object> res = new HashMap<>();

        if (gameOver) {
            res.put("success", false);
            res.put("error", "Game has already ended.");
            return res;
        }

        String active = getCurrentTurnPlayer();
        if (active == null || !active.equals(playerId)) {
            res.put("success", false);
            res.put("error", "Not your turn.");
            return res;
        }

        if (phase != TurnPhase.WAITING_FOR_ROLL) {
            res.put("success", false);
            res.put("error", "Already rolled dice. Must move a legal token.");
            return res;
        }

        int roll = (forcedRoll >= 1 && forcedRoll <= 6) ? forcedRoll : (int) (Math.random() * 6) + 1;
        this.currentRoll = roll;

        // Check consecutive sixes
        if (roll == 6) {
            consecutiveSixes++;
            if (consecutiveSixes == 3) {
                // Third consecutive six penalizes and forfeits the turn
                this.consecutiveSixes = 0;
                this.currentRoll = 0;
                this.phase = TurnPhase.WAITING_FOR_ROLL;
                String forfeitedPlayer = playerId;
                advanceTurn();

                this.lastActionMessage = forfeitedPlayer + " rolled 3 consecutive sixes! Turn ended without a move.";
                this.eventLog.add(this.lastActionMessage);

                res.put("success", true);
                res.put("roll", roll);
                res.put("consecutiveSixPenalty", true);
                res.put("message", this.lastActionMessage);
                res.put("nextPlayer", getCurrentTurnPlayer());
                return res;
            }
        } else {
            this.consecutiveSixes = 0;
        }

        // Determine legal moves for active player with this roll
        List<Integer> legalMoves = calculateLegalMoves(playerId, roll);

        if (legalMoves.isEmpty()) {
            // No legal moves possible (e.g. all in yard and roll < 6, or overshoot)
            this.currentRoll = 0;
            this.phase = TurnPhase.WAITING_FOR_ROLL;
            this.consecutiveSixes = 0;
            advanceTurn();

            this.lastActionMessage = playerId + " rolled a " + roll + " with no legal moves available.";
            this.eventLog.add(this.lastActionMessage);

            res.put("success", true);
            res.put("roll", roll);
            res.put("legalMoves", Collections.emptyList());
            res.put("noLegalMoves", true);
            res.put("message", this.lastActionMessage);
            res.put("nextPlayer", getCurrentTurnPlayer());
            return res;
        }

        // Legal moves available: transition to WAITING_FOR_MOVE
        this.phase = TurnPhase.WAITING_FOR_MOVE;
        this.lastActionMessage = playerId + " rolled a " + roll + ". Select a token to move.";
        this.eventLog.add(playerId + " rolled a " + roll);

        res.put("success", true);
        res.put("roll", roll);
        res.put("legalMoves", legalMoves);
        res.put("message", this.lastActionMessage);
        return res;
    }

    /**
     * Returns legal token indices (0..3) that can be moved with the current roll.
     */
    public synchronized List<Integer> getLegalMoves(String playerId) {
        if (phase != TurnPhase.WAITING_FOR_MOVE || !playerId.equals(getCurrentTurnPlayer())) {
            return Collections.emptyList();
        }
        return calculateLegalMoves(playerId, currentRoll);
    }

    private List<Integer> calculateLegalMoves(String playerId, int roll) {
        List<Integer> legal = new ArrayList<>();
        List<LudoToken> tokens = playerTokens.get(playerId);
        if (tokens == null || roll < 1 || roll > 6) return legal;

        for (int i = 0; i < tokens.size(); i++) {
            LudoToken t = tokens.get(i);
            if (t.isFinished()) continue;

            if (t.isInYard()) {
                // Must roll a 6 to exit yard onto starting square
                if (roll == 6) legal.add(i);
            } else {
                // On track or in home column: must not overshoot finish step (56)
                int targetStep = t.getStep() + roll;
                if (targetStep <= LudoBoard.FINISH_STEP) {
                    legal.add(i);
                }
            }
        }
        return legal;
    }

    /**
     * Moves the chosen token for the active player using the current roll.
     */
    public synchronized Map<String, Object> moveToken(String playerId, int tokenId) {
        Map<String, Object> res = new HashMap<>();

        if (gameOver) {
            res.put("success", false);
            res.put("error", "Game has already ended.");
            return res;
        }

        String active = getCurrentTurnPlayer();
        if (active == null || !active.equals(playerId)) {
            res.put("success", false);
            res.put("error", "Not your turn.");
            return res;
        }

        if (phase != TurnPhase.WAITING_FOR_MOVE) {
            res.put("success", false);
            res.put("error", "Must roll dice before selecting a token.");
            return res;
        }

        List<Integer> legal = calculateLegalMoves(playerId, currentRoll);
        if (!legal.contains(tokenId)) {
            res.put("success", false);
            res.put("error", "Illegal token selection: Token " + tokenId + " cannot move with roll " + currentRoll);
            return res;
        }

        List<LudoToken> tokens = playerTokens.get(playerId);
        LudoToken token = tokens.get(tokenId);
        int oldStep = token.getStep();
        int newStep;

        if (token.isInYard()) {
            newStep = 0; // Move to starting square
        } else {
            newStep = oldStep + currentRoll;
        }

        token.setStep(newStep);

        // Check for captures on shared track
        List<Map<String, Object>> capturedList = new ArrayList<>();
        if (token.isOnTrack()) {
            int trackSquare = token.getTrackSquare();
            if (!LudoBoard.isSafeTrackSquare(trackSquare)) {
                // Safe square check passed: capture any opponent token on this square
                for (Map.Entry<String, List<LudoToken>> entry : playerTokens.entrySet()) {
                    if (entry.getKey().equals(playerId)) continue; // Cannot capture own tokens

                    for (LudoToken oppToken : entry.getValue()) {
                        if (oppToken.isOnTrack() && oppToken.getTrackSquare() == trackSquare) {
                            oppToken.setStep(-1); // Return to yard!
                            Map<String, Object> capInfo = new HashMap<>();
                            capInfo.put("capturedPlayer", entry.getKey());
                            capInfo.put("capturedTokenId", oppToken.getId());
                            capInfo.put("capturedColor", oppToken.getColor().name());
                            capInfo.put("trackSquare", trackSquare);
                            capturedList.add(capInfo);

                            String capMsg = playerId + " captured " + entry.getKey() + "'s " + oppToken.getColor().getDisplayName() + " token at track square " + trackSquare + "!";
                            this.eventLog.add(capMsg);
                        }
                    }
                }
            }
        }

        // Check win condition (all 4 tokens finished at step 56)
        boolean hasWon = tokens.stream().allMatch(LudoToken::isFinished);
        if (hasWon) {
            this.gameOver = true;
            this.winnerId = playerId;
            this.phase = TurnPhase.WAITING_FOR_ROLL;
            this.lastActionMessage = "🎉 " + playerId + " brought all 4 tokens home and won the game!";
            this.eventLog.add(this.lastActionMessage);

            res.put("success", true);
            res.put("gameOver", true);
            res.put("winnerId", playerId);
            res.put("captured", capturedList);
            res.put("message", this.lastActionMessage);
            return res;
        }

        // Turn resolution: Rolling a 6 grants bonus roll on the same turn!
        boolean bonusRoll = (currentRoll == 6);
        if (bonusRoll) {
            this.phase = TurnPhase.WAITING_FOR_ROLL;
            this.currentRoll = 0;
            this.lastActionMessage = playerId + " rolled a 6 and gets another roll!";
            this.eventLog.add(this.lastActionMessage);
        } else {
            advanceTurn();
            this.phase = TurnPhase.WAITING_FOR_ROLL;
            this.currentRoll = 0;
            this.consecutiveSixes = 0;
            this.lastActionMessage = playerId + " moved token " + tokenId + " to step " + newStep + ". Next: " + getCurrentTurnPlayer();
            this.eventLog.add(playerId + " moved token " + tokenId + " to step " + newStep);
        }

        res.put("success", true);
        res.put("gameOver", false);
        res.put("bonusRoll", bonusRoll);
        res.put("captured", capturedList);
        res.put("nextPlayer", getCurrentTurnPlayer());
        res.put("message", this.lastActionMessage);
        return res;
    }

    private void advanceTurn() {
        if (!players.isEmpty()) {
            currentTurnIndex = (currentTurnIndex + 1) % players.size();
        }
    }

    @Override
    public synchronized GameState move(String playerId, Object moveData) {
        if (moveData instanceof Map<?, ?> map) {
            String action = (String) map.get("action");
            if ("roll".equalsIgnoreCase(action)) {
                int forced = 0;
                if (map.containsKey("forcedRoll")) {
                    try { forced = Integer.parseInt(map.get("forcedRoll").toString()); } catch (Exception ignored) {}
                }
                rollDice(playerId, forced);
                return getGameState();
            } else if ("move".equalsIgnoreCase(action)) {
                int tokenId = 0;
                try { tokenId = Integer.parseInt(map.get("tokenId").toString()); } catch (Exception ignored) {}
                moveToken(playerId, tokenId);
                return getGameState();
            }
        }
        return getGameState();
    }

    @Override
    public synchronized GameState getGameState() {
        Map<String, Object> map = new HashMap<>();
        map.put("phase", phase.name());
        map.put("currentRoll", currentRoll);
        map.put("consecutiveSixes", consecutiveSixes);
        map.put("currentTurnPlayer", getCurrentTurnPlayer());
        map.put("gameOver", gameOver);
        map.put("winnerId", winnerId);
        map.put("lastMessage", lastActionMessage);
        return new GameState(gameOver, getCurrentTurnPlayer(), map, winnerId, lastActionMessage);
    }

    /**
     * Serializes complete authoritative game state into JSON for clients.
     */
    public synchronized String toJson() {
        return toJson(null);
    }

    public synchronized String toJson(String viewerEmail) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"gameOver\":").append(gameOver).append(",");
        sb.append("\"winnerId\":").append(winnerId != null ? "\"" + winnerId + "\"" : "null").append(",");
        sb.append("\"currentTurnPlayer\":\"").append(getCurrentTurnPlayer() != null ? getCurrentTurnPlayer() : "").append("\",");
        sb.append("\"phase\":\"").append(phase.name()).append("\",");
        sb.append("\"currentRoll\":").append(currentRoll).append(",");
        sb.append("\"consecutiveSixes\":").append(consecutiveSixes).append(",");
        sb.append("\"lastMessage\":\"").append(escapeJson(lastActionMessage)).append("\",");

        // Legal moves for the viewer if viewer is active
        List<Integer> legal = (viewerEmail != null && viewerEmail.equals(getCurrentTurnPlayer())) ?
                getLegalMoves(viewerEmail) : Collections.emptyList();
        sb.append("\"legalMoves\":").append(legal.toString()).append(",");

        // Players & assigned colors
        sb.append("\"players\":[");
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            LudoColor col = playerColors.get(p.getId());
            if (i > 0) sb.append(",");
            sb.append("{")
              .append("\"id\":\"").append(p.getId()).append("\",")
              .append("\"name\":\"").append(p.getName()).append("\",")
              .append("\"color\":\"").append(col != null ? col.name() : "RED").append("\",")
              .append("\"colorHex\":\"").append(col != null ? col.getHexColor() : "#EF4444").append("\",")
              .append("\"colorName\":\"").append(col != null ? col.getDisplayName() : "Red").append("\"")
              .append("}");
        }
        sb.append("],");

        // Tokens by player
        sb.append("\"tokens\":{");
        int pCount = 0;
        for (Map.Entry<String, List<LudoToken>> e : playerTokens.entrySet()) {
            if (pCount++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":[");
            List<LudoToken> list = e.getValue();
            for (int t = 0; t < list.size(); t++) {
                if (t > 0) sb.append(",");
                sb.append(list.get(t).toJson());
            }
            sb.append("]");
        }
        sb.append("},");

        // Event log (last 15 items)
        sb.append("\"recentEvents\":[");
        int startIdx = Math.max(0, eventLog.size() - 15);
        for (int i = startIdx; i < eventLog.size(); i++) {
            if (i > startIdx) sb.append(",");
            sb.append("\"").append(escapeJson(eventLog.get(i))).append("\"");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
