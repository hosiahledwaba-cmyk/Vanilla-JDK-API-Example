package com.robinhood.engine.khasino;

import com.robinhood.engine.Game;
import com.robinhood.engine.GameState;
import com.robinhood.engine.core.Player;
import com.robinhood.engine.core.TurnManager;

import java.util.*;

/**
 * Deterministic, authoritative rule engine for South African Khasino (Kasi Kasino).
 *
 * Implements:
 * - 40-card deck (A-10, no picture cards)
 * - Anti-clockwise turn rotation
 * - 2-player mode with 2 deals of 10 cards and deal 1 drift restriction
 * - 3-player mode (13 cards each + 1 face-up floor card, 7-point scoring)
 * - 4-player singles mode (10 cards each, 7-point scoring)
 * - 4-player 2v2 partnership mode (10 cards each, Team 1 [0 & 2] vs Team 2 [1 & 3], 11-point scoring)
 * - Capturing (Chow), Simple Builds, Compound Builds, Changing Opponent Build Value,
 *   Drifting, Stealing from Opponent Captured Piles, Sweeps, and End-of-Deal Floor Card Awards.
 */
public class KhasinoGame implements Game {

    public enum Mode {
        TWO_PLAYER,                 // 1v1, 2 deals of 10 cards, 11-point
        THREE_PLAYER,               // 3 players, 13 cards + 1 floor, 7-point
        FOUR_PLAYER_SINGLES,        // 4 players, 10 cards, 7-point
        FOUR_PLAYER_PARTNERSHIPS    // 2v2 (seats 0&2 vs 1&3), 10 cards, 11-point
    }

    private Mode mode = Mode.TWO_PLAYER;
    private int dealNumber = 1; // 1 or 2 for TWO_PLAYER; 1 for others
    private final KhasinoDeck deck;
    private final List<Player> players = new ArrayList<>();
    private final Map<String, Integer> playerTeams = new HashMap<>(); // playerId -> 1 or 2 (or 0)
    private TurnManager turnManager;

    private final Map<String, List<KhasinoCard>> hands = new HashMap<>();
    private final List<KhasinoCard> floorCards = new ArrayList<>();
    private final Map<String, KhasinoBuild> builds = new LinkedHashMap<>();
    private int buildCounter = 1;

    // Captured piles: for partnerships, keys are "Team 1" / "Team 2"; for singles, keys are playerId
    private final Map<String, List<KhasinoCard>> capturedPiles = new HashMap<>();
    private final Map<String, Integer> sweepCounts = new HashMap<>();

    private String lastCapturingPlayerId = null;
    private boolean gameOver = false;
    private String winnerId = null;
    private String lastActionMessage = "Game initialized";
    private final List<String> actionLog = new ArrayList<>();
    private Map<String, KhasinoScoring.ScoreBreakdown> finalScores = null;

    public KhasinoGame() {
        this(Mode.TWO_PLAYER, new Random());
    }

    public KhasinoGame(Mode mode) {
        this(mode, new Random());
    }

    public KhasinoGame(Mode mode, Random random) {
        this.mode = mode != null ? mode : Mode.TWO_PLAYER;
        this.deck = new KhasinoDeck(random != null ? random : new Random());
    }

    public void setMode(Mode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    @Override
    public void start(List<Player> playerList) {
        if (playerList == null || playerList.isEmpty()) {
            throw new IllegalArgumentException("Player list cannot be empty");
        }

        players.clear();
        players.addAll(playerList);

        // Adjust mode based on player count if needed
        if (players.size() == 2) {
            this.mode = Mode.TWO_PLAYER;
        } else if (players.size() == 3) {
            this.mode = Mode.THREE_PLAYER;
        } else if (players.size() >= 4 && mode != Mode.FOUR_PLAYER_PARTNERSHIPS) {
            this.mode = Mode.FOUR_PLAYER_SINGLES;
        }

        // Setup teams if 2v2 partnership
        playerTeams.clear();
        if (this.mode == Mode.FOUR_PLAYER_PARTNERSHIPS) {
            playerTeams.put(players.get(0).getId(), 1);
            playerTeams.put(players.get(1).getId(), 2);
            playerTeams.put(players.get(2).getId(), 1);
            playerTeams.put(players.get(3).getId(), 2);
            capturedPiles.put("Team 1", new ArrayList<>());
            capturedPiles.put("Team 2", new ArrayList<>());
            sweepCounts.put("Team 1", 0);
            sweepCounts.put("Team 2", 0);
        } else {
            for (Player p : players) {
                playerTeams.put(p.getId(), 0);
                capturedPiles.put(p.getId(), new ArrayList<>());
                sweepCounts.put(p.getId(), 0);
            }
        }

        hands.clear();
        floorCards.clear();
        builds.clear();
        buildCounter = 1;
        dealNumber = 1;
        gameOver = false;
        winnerId = null;
        lastCapturingPlayerId = null;
        actionLog.clear();
        finalScores = null;

        // Anti-clockwise turn manager
        turnManager = new TurnManager(players);

        // Reset and shuffle 40-card deck
        deck.reset();
        deck.shuffle();

        // Perform initial deal according to South African Khasino rules
        dealInitialCards();

        lastActionMessage = "Match started (" + mode.name() + "). Deal " + dealNumber + " dealt.";
        logAction(lastActionMessage);
    }

    private void dealInitialCards() {
        if (mode == Mode.TWO_PLAYER) {
            // Two players: Deal 10 cards to each player. No floor cards.
            for (Player p : players) {
                hands.put(p.getId(), new ArrayList<>(deck.draw(10)));
            }
        } else if (mode == Mode.THREE_PLAYER) {
            // Three players: Deal 13 cards to each player (39 total), 1 card on the floor face-up.
            for (Player p : players) {
                hands.put(p.getId(), new ArrayList<>(deck.draw(13)));
            }
            KhasinoCard floorCard = deck.draw();
            if (floorCard != null) floorCards.add(floorCard);
        } else {
            // Four players (singles or partnerships): Deal 10 cards to each player. No floor cards.
            for (Player p : players) {
                hands.put(p.getId(), new ArrayList<>(deck.draw(10)));
            }
        }
    }

    public Mode getMode() {
        return mode;
    }

    public int getDealNumber() {
        return dealNumber;
    }

    public boolean isElevenPointMode() {
        return mode == Mode.TWO_PLAYER || mode == Mode.FOUR_PLAYER_PARTNERSHIPS;
    }

    public String getCaptureEntityKey(String playerId) {
        if (mode == Mode.FOUR_PLAYER_PARTNERSHIPS) {
            int team = playerTeams.getOrDefault(playerId, 1);
            return "Team " + team;
        }
        return playerId;
    }

    public int getPlayerTeam(String playerId) {
        return playerTeams.getOrDefault(playerId, 0);
    }

    public boolean arePartners(String playerA, String playerB) {
        if (mode != Mode.FOUR_PLAYER_PARTNERSHIPS || playerA == null || playerB == null) return false;
        int tA = playerTeams.getOrDefault(playerA, 1);
        int tB = playerTeams.getOrDefault(playerB, 2);
        return tA == tB;
    }

    public String getCurrentTurnPlayer() {
        return (turnManager != null && turnManager.getCurrentPlayer() != null) ?
                turnManager.getCurrentPlayer().getId() : "";
    }

    public List<KhasinoCard> getHand(String playerId) {
        return hands.getOrDefault(playerId, Collections.emptyList());
    }

    public List<KhasinoCard> getFloorCards() {
        return Collections.unmodifiableList(floorCards);
    }

    public Map<String, KhasinoBuild> getBuilds() {
        return Collections.unmodifiableMap(builds);
    }

    public List<KhasinoCard> getCapturedPile(String entityKey) {
        return capturedPiles.getOrDefault(entityKey, Collections.emptyList());
    }

    public KhasinoCard getTopCapturedCard(String entityKey) {
        List<KhasinoCard> pile = capturedPiles.get(entityKey);
        if (pile == null || pile.isEmpty()) return null;
        return pile.get(pile.size() - 1);
    }

    public int getSweepCount(String entityKey) {
        return sweepCounts.getOrDefault(entityKey, 0);
    }

    public boolean isOver() {
        return gameOver;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public String getLastAction() {
        return lastActionMessage;
    }

    public Map<String, KhasinoScoring.ScoreBreakdown> getFinalScores() {
        return finalScores;
    }

    // --- Action Execution ---

    public synchronized Map<String, Object> executeAction(String playerId, KhasinoAction action) {
        Map<String, Object> result = new HashMap<>();

        if (gameOver) {
            result.put("success", false);
            result.put("error", "Game has already concluded");
            return result;
        }

        if (!getCurrentTurnPlayer().equals(playerId)) {
            result.put("success", false);
            result.put("error", "Not your turn (current: " + getCurrentTurnPlayer() + ")");
            return result;
        }

        List<KhasinoCard> hand = hands.get(playerId);
        if (hand == null) {
            result.put("success", false);
            result.put("error", "Player not in game");
            return result;
        }

        // Validate played card from hand if specified
        KhasinoCard playedCard = null;
        if (action.getPlayedCardId() != null) {
            for (KhasinoCard c : hand) {
                if (c.getId().equals(action.getPlayedCardId())) {
                    playedCard = c;
                    break;
                }
            }
            if (playedCard == null) {
                result.put("success", false);
                result.put("error", "Played card not in hand: " + action.getPlayedCardId());
                return result;
            }
        }

        Map<String, Object> subRes = null;
        switch (action.getType()) {
            case CHOW -> {
                subRes = handleChow(playerId, hand, playedCard, action);
            }
            case BUILD_SIMPLE -> {
                subRes = handleBuildSimple(playerId, hand, playedCard, action);
            }
            case BUILD_COMPOUND -> {
                subRes = handleBuildCompound(playerId, hand, playedCard, action);
            }
            case CHANGE_BUILD_VALUE -> {
                subRes = handleChangeBuildValue(playerId, hand, playedCard, action);
            }
            case DRIFT -> {
                subRes = handleDrift(playerId, hand, playedCard);
            }
            case STASH -> {
                subRes = handleStash(playerId, hand, playedCard, action);
            }
            case SHIYA -> {
                subRes = handleShiya(playerId, hand, action);
            }
        }

        if (subRes != null) {
            if (!Boolean.TRUE.equals(subRes.get("success"))) return subRes;
            result.putAll(subRes);
        }

        // Remove played card from hand (if any action consumed it)
        if (playedCard != null && action.getType() != KhasinoAction.ActionType.SHIYA) {
            hand.remove(playedCard);
        }

        // Check if hand/deal is finished
        checkEndOfDealOrGame();

        if (!gameOver) {
            turnManager.nextTurn();
        }

        result.put("success", true);
        result.put("won", gameOver);
        result.put("winner", winnerId);
        result.put("nextPlayer", getCurrentTurnPlayer());
        result.put("lastAction", lastActionMessage);
        return result;
    }

    private Map<String, Object> handleChow(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        if (playedCard == null) {
            res.put("success", false);
            res.put("error", "Chow requires playing a card from hand");
            return res;
        }

        int targetVal = playedCard.getValue();
        List<KhasinoCard> cardsToCapture = new ArrayList<>();
        cardsToCapture.add(playedCard);

        // 1. Capture targeted builds
        for (String bId : action.getTargetBuildIds()) {
            KhasinoBuild b = builds.get(bId);
            if (b == null) {
                res.put("success", false);
                res.put("error", "Target build not found: " + bId);
                return res;
            }
            if (b.getCaptureValue() != targetVal) {
                res.put("success", false);
                res.put("error", "Build " + bId + " capture value (" + b.getCaptureValue() + ") does not match played card (" + targetVal + ")");
                return res;
            }
            cardsToCapture.addAll(b.getAllCards());
            builds.remove(bId);
        }

        // 2. Capture targeted floor cards
        List<KhasinoCard> floorTargets = new ArrayList<>();
        for (String cId : action.getTargetFloorCardIds()) {
            KhasinoCard match = null;
            for (KhasinoCard fc : floorCards) {
                if (fc.getId().equals(cId)) {
                    match = fc;
                    break;
                }
            }
            if (match == null) {
                res.put("success", false);
                res.put("error", "Target floor card not on floor: " + cId);
                return res;
            }
            floorTargets.add(match);
        }

        // Verify valid sum/matching of floor targets
        if (!floorTargets.isEmpty()) {
            // Partition into groups summing to targetVal, or single cards matching targetVal
            boolean valid = validateFloorCaptureGrouping(floorTargets, targetVal);
            if (!valid) {
                res.put("success", false);
                res.put("error", "Floor targets do not partition into sums of " + targetVal);
                return res;
            }
            cardsToCapture.addAll(floorTargets);
            floorCards.removeAll(floorTargets);
        }

        if (cardsToCapture.size() <= 1) {
            res.put("success", false);
            res.put("error", "Chow must capture at least one card or build from the table");
            return res;
        }

        String entityKey = getCaptureEntityKey(playerId);
        capturedPiles.get(entityKey).addAll(cardsToCapture);
        lastCapturingPlayerId = playerId;

        // Check for Sweep: all floor cards and all builds cleared!
        boolean isSweep = floorCards.isEmpty() && builds.isEmpty();
        String sweepStr = "";
        if (isSweep) {
            sweepCounts.put(entityKey, sweepCounts.getOrDefault(entityKey, 0) + 1);
            sweepStr = " — ★ SWEEP!";
        }

        lastActionMessage = playerId + " Chowed with " + playedCard + " (" + (cardsToCapture.size() - 1) + " cards captured)" + sweepStr;
        logAction(lastActionMessage);

        res.put("success", true);
        res.put("capturedCount", cardsToCapture.size());
        res.put("isSweep", isSweep);
        return res;
    }

    private boolean validateFloorCaptureGrouping(List<KhasinoCard> cards, int target) {
        // Direct match: all single cards of value target
        boolean allExact = true;
        for (KhasinoCard c : cards) {
            if (c.getValue() != target) {
                allExact = false;
                break;
            }
        }
        if (allExact) return true;

        // Sum partitioning check
        int totalSum = 0;
        for (KhasinoCard c : cards) totalSum += c.getValue();
        if (totalSum % target != 0) return false;

        return canPartitionIntoSums(new ArrayList<>(cards), target);
    }

    private boolean canPartitionIntoSums(List<KhasinoCard> remaining, int target) {
        if (remaining.isEmpty()) return true;
        List<List<KhasinoCard>> subsets = findSubsetsSummingTo(remaining, target);
        for (List<KhasinoCard> sub : subsets) {
            List<KhasinoCard> nextRemaining = new ArrayList<>(remaining);
            for (KhasinoCard c : sub) nextRemaining.remove(c);
            if (canPartitionIntoSums(nextRemaining, target)) return true;
        }
        return false;
    }

    private List<List<KhasinoCard>> findSubsetsSummingTo(List<KhasinoCard> pool, int target) {
        List<List<KhasinoCard>> results = new ArrayList<>();
        findSubsetsRec(pool, 0, target, new ArrayList<>(), results);
        return results;
    }

    private void findSubsetsRec(List<KhasinoCard> pool, int idx, int remaining, List<KhasinoCard> current, List<List<KhasinoCard>> results) {
        if (remaining == 0 && !current.isEmpty()) {
            results.add(new ArrayList<>(current));
            return;
        }
        if (remaining < 0 || idx >= pool.size()) return;

        // Include pool[idx]
        current.add(pool.get(idx));
        findSubsetsRec(pool, idx + 1, remaining - pool.get(idx).getValue(), current, results);
        current.remove(current.size() - 1);

        // Exclude pool[idx]
        findSubsetsRec(pool, idx + 1, remaining, current, results);
    }

    private Map<String, Object> handleBuildSimple(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        int declVal = action.getDeclaredValue();

        if (declVal < 2 || declVal > 10) {
            res.put("success", false);
            res.put("error", "Build value must be between 2 and 10 (got " + declVal + ")");
            return res;
        }

        // Rule: Player must retain a card in hand to capture the build later!
        int availableOfDeclVal = 0;
        for (KhasinoCard c : hand) {
            if (c.getValue() == declVal) availableOfDeclVal++;
        }
        // If playedCard itself equals declVal, that cannot be the capturing card (you must retain another!)
        if (playedCard != null && playedCard.getValue() == declVal) {
            availableOfDeclVal--;
        }
        if (availableOfDeclVal <= 0) {
            res.put("success", false);
            res.put("error", "Must hold a card of value " + declVal + " in hand to capture the build");
            return res;
        }

        // Rule: There cannot be two separate builds of the same value on the table
        for (KhasinoBuild b : builds.values()) {
            if (b.getCaptureValue() == declVal) {
                res.put("success", false);
                res.put("error", "A build of value " + declVal + " already exists on the floor (use compound/augment instead)");
                return res;
            }
        }

        List<KhasinoCard> componentCards = new ArrayList<>();
        if (playedCard != null) componentCards.add(playedCard);

        for (String cId : action.getTargetFloorCardIds()) {
            KhasinoCard fc = findFloorCard(cId);
            if (fc == null) {
                res.put("success", false);
                res.put("error", "Floor card not found: " + cId);
                return res;
            }
            componentCards.add(fc);
        }

        int sum = 0;
        for (KhasinoCard c : componentCards) sum += c.getValue();
        if (sum != declVal) {
            res.put("success", false);
            res.put("error", "Components sum (" + sum + ") does not equal declared build value (" + declVal + ")");
            return res;
        }

        // Remove used floor cards
        for (String cId : action.getTargetFloorCardIds()) {
            removeFloorCard(cId);
        }

        String buildId = "b_" + (buildCounter++);
        int team = getPlayerTeam(playerId);
        KhasinoBuild newBuild = new KhasinoBuild(buildId, playerId, team, declVal, componentCards);
        builds.put(buildId, newBuild);

        lastActionMessage = playerId + " created a Simple Build of " + declVal + " (" + componentCards + ")";
        logAction(lastActionMessage);

        res.put("success", true);
        res.put("buildId", buildId);
        return res;
    }

    private Map<String, Object> handleBuildCompound(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        String targetBId = action.getTargetBuildId();
        KhasinoBuild build = builds.get(targetBId);
        if (build == null) {
            res.put("success", false);
            res.put("error", "Target build not found: " + targetBId);
            return res;
        }

        // May only augment a build owned by self or partner (unless capturing in the same turn)
        int playerTeam = getPlayerTeam(playerId);
        boolean isOwner = build.getOwnerId().equals(playerId);
        boolean isPartner = (mode == Mode.FOUR_PLAYER_PARTNERSHIPS && build.getTeamId() == playerTeam);

        if (!isOwner && !isPartner) {
            res.put("success", false);
            res.put("error", "Cannot augment an opponent's build without capturing it in the same turn");
            return res;
        }

        int targetVal = build.getCaptureValue();
        List<KhasinoCard> groupCards = new ArrayList<>();
        if (playedCard != null) groupCards.add(playedCard);

        for (String cId : action.getTargetFloorCardIds()) {
            KhasinoCard fc = findFloorCard(cId);
            if (fc == null) {
                res.put("success", false);
                res.put("error", "Floor card not found: " + cId);
                return res;
            }
            groupCards.add(fc);
        }

        // Stealing top card of opponent capture pile if specified
        if (action.getStolenCardId() != null && action.getStolenOpponentId() != null) {
            String oppKey = getCaptureEntityKey(action.getStolenOpponentId());
            KhasinoCard top = getTopCapturedCard(oppKey);
            if (top == null || !top.getId().equals(action.getStolenCardId())) {
                res.put("success", false);
                res.put("error", "Exposed card on opponent's captured pile mismatch or unavailable");
                return res;
            }
            if (arePartners(playerId, action.getStolenOpponentId())) {
                res.put("success", false);
                res.put("error", "Cannot steal from partner's captured pile");
                return res;
            }
            // Remove top card from opponent pile
            List<KhasinoCard> oppPile = capturedPiles.get(oppKey);
            oppPile.remove(oppPile.size() - 1);
            groupCards.add(top);
        }

        int sum = 0;
        for (KhasinoCard c : groupCards) sum += c.getValue();
        if (sum != targetVal) {
            res.put("success", false);
            res.put("error", "Augment group sum (" + sum + ") does not equal build capture value (" + targetVal + ")");
            return res;
        }

        // Remove floor cards used
        for (String cId : action.getTargetFloorCardIds()) {
            removeFloorCard(cId);
        }

        build.addGroup(groupCards);
        lastActionMessage = playerId + " augmented Build of " + targetVal + " into a Compound Build (" + groupCards + ")";
        logAction(lastActionMessage);

        res.put("success", true);
        return res;
    }

    private Map<String, Object> handleChangeBuildValue(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        String targetBId = action.getTargetBuildId();
        KhasinoBuild build = builds.get(targetBId);
        if (build == null) {
            res.put("success", false);
            res.put("error", "Target build not found: " + targetBId);
            return res;
        }

        if (build.isCompound()) {
            res.put("success", false);
            res.put("error", "Cannot change the value of a Compound/Augmented build");
            return res;
        }

        if (build.getOwnerId().equals(playerId) || arePartners(playerId, build.getOwnerId())) {
            res.put("success", false);
            res.put("error", "Cannot change value of your own or partner's build (can only augment with same value)");
            return res;
        }

        if (playedCard == null) {
            res.put("success", false);
            res.put("error", "Must play a card from hand to change build value");
            return res;
        }

        int newVal = action.getDeclaredValue();
        if (newVal <= build.getCaptureValue() || newVal > 10) {
            res.put("success", false);
            res.put("error", "New build value must be greater than " + build.getCaptureValue() + " and <= 10");
            return res;
        }

        if (build.getCaptureValue() + playedCard.getValue() != newVal) {
            res.put("success", false);
            res.put("error", "Old build value (" + build.getCaptureValue() + ") + played card (" + playedCard.getValue() + ") does not equal " + newVal);
            return res;
        }

        // Player must hold a card of the new value in hand!
        boolean holdsNewVal = false;
        for (KhasinoCard c : hand) {
            if (!c.getId().equals(playedCard.getId()) && c.getValue() == newVal) {
                holdsNewVal = true;
                break;
            }
        }
        if (!holdsNewVal) {
            res.put("success", false);
            res.put("error", "Must hold a card of value " + newVal + " in hand to take over this build");
            return res;
        }

        int team = getPlayerTeam(playerId);
        build.changeValue(newVal, playerId, team, playedCard);

        lastActionMessage = playerId + " increased opponent's build to " + newVal + " with " + playedCard + " and took ownership!";
        logAction(lastActionMessage);

        res.put("success", true);
        return res;
    }

    private Map<String, Object> handleDrift(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard) {
        Map<String, Object> res = new HashMap<>();
        if (playedCard == null) {
            res.put("success", false);
            res.put("error", "Drift requires playing a card from hand");
            return res;
        }

        // 2-player deal 1 restriction: cannot drift if you own an uncaptured build on the table
        if (mode == Mode.TWO_PLAYER && dealNumber == 1) {
            for (KhasinoBuild b : builds.values()) {
                if (b.getOwnerId().equals(playerId)) {
                    res.put("success", false);
                    res.put("error", "Cannot drift in Deal 1 while owning an uncaptured build on the table");
                    return res;
                }
            }
        }

        floorCards.add(playedCard);
        lastActionMessage = playerId + " drifted " + playedCard + " onto the floor";
        logAction(lastActionMessage);

        res.put("success", true);
        return res;
    }

    private Map<String, Object> handleStash(String playerId, List<KhasinoCard> hand, KhasinoCard playedCard, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        String targetBId = action.getTargetBuildId();
        KhasinoBuild build = builds.get(targetBId);
        if (build == null) {
            res.put("success", false);
            res.put("error", "Build not found: " + targetBId);
            return res;
        }

        if (!build.getOwnerId().equals(playerId) && !arePartners(playerId, build.getOwnerId())) {
            res.put("success", false);
            res.put("error", "Can only Stash on a build owned by yourself or partner");
            return res;
        }

        if (playedCard == null || playedCard.getValue() != build.getCaptureValue()) {
            res.put("success", false);
            res.put("error", "Stash card must match build capture value (" + build.getCaptureValue() + ")");
            return res;
        }

        // Add single card as a group into build (making it compound / stashed)
        build.addGroup(Collections.singletonList(playedCard));
        lastActionMessage = playerId + " called 'Stash' on Build of " + build.getCaptureValue() + " with " + playedCard;
        logAction(lastActionMessage);

        res.put("success", true);
        return res;
    }

    private Map<String, Object> handleShiya(String playerId, List<KhasinoCard> hand, KhasinoAction action) {
        Map<String, Object> res = new HashMap<>();
        if (mode != Mode.FOUR_PLAYER_PARTNERSHIPS) {
            res.put("success", false);
            res.put("error", "Shiya is only applicable in 2v2 Partnership play");
            return res;
        }

        String targetBId = action.getTargetBuildId();
        KhasinoBuild build = builds.get(targetBId);
        if (build == null) {
            res.put("success", false);
            res.put("error", "Build not found: " + targetBId);
            return res;
        }

        // Transfer ownership to partner
        build.setOwnerId(playerId);
        lastActionMessage = playerId + " called 'Shiya' — keeping Build of " + build.getCaptureValue() + " on floor for their side!";
        logAction(lastActionMessage);

        res.put("success", true);
        return res;
    }

    private KhasinoCard findFloorCard(String id) {
        for (KhasinoCard c : floorCards) {
            if (c.getId().equals(id)) return c;
        }
        return null;
    }

    private void removeFloorCard(String id) {
        floorCards.removeIf(c -> c.getId().equals(id));
    }

    private void checkEndOfDealOrGame() {
        boolean allHandsEmpty = true;
        for (List<KhasinoCard> h : hands.values()) {
            if (!h.isEmpty()) {
                allHandsEmpty = false;
                break;
            }
        }

        if (!allHandsEmpty) return;

        // In 2-player mode, after deal 1 hands are empty, deal the second batch of 10 cards!
        if (mode == Mode.TWO_PLAYER && dealNumber == 1 && deck.size() >= 20) {
            dealNumber = 2;
            for (Player p : players) {
                hands.get(p.getId()).addAll(deck.draw(10));
            }
            lastActionMessage = "First deal complete! Second deal dealt (10 cards each). Drifting is now fully unlocked.";
            logAction(lastActionMessage);
            return;
        }

        // All cards across all deals have been played! Conclude the match.
        endGame();
    }

    private void endGame() {
        gameOver = true;

        // "When all the cards have been played, whoever made the last capture takes whatever cards are left in the layout."
        if (lastCapturingPlayerId != null && (!floorCards.isEmpty() || !builds.isEmpty())) {
            String entityKey = getCaptureEntityKey(lastCapturingPlayerId);
            List<KhasinoCard> leftover = new ArrayList<>(floorCards);
            for (KhasinoBuild b : builds.values()) {
                leftover.addAll(b.getAllCards());
            }
            floorCards.clear();
            builds.clear();
            capturedPiles.get(entityKey).addAll(leftover);
            logAction(lastCapturingPlayerId + " made the last capture and took the " + leftover.size() + " remaining cards from the layout.");
        }

        // Calculate scores
        finalScores = KhasinoScoring.calculateScores(capturedPiles, sweepCounts, isElevenPointMode());
        winnerId = KhasinoScoring.resolveWinner(finalScores);

        lastActionMessage = "Match Concluded! Winner: " + winnerId;
        logAction(lastActionMessage);
    }

    // --- Legal Move Enumeration ---

    public List<KhasinoAction> getLegalActions(String playerId) {
        List<KhasinoAction> actions = new ArrayList<>();
        if (gameOver || !getCurrentTurnPlayer().equals(playerId)) return actions;

        List<KhasinoCard> hand = hands.get(playerId);
        if (hand == null || hand.isEmpty()) return actions;

        int playerTeam = getPlayerTeam(playerId);

        for (KhasinoCard card : hand) {
            int val = card.getValue();

            // 1. Chows (Captures)
            // A. Single matching floor card or combinations
            List<List<KhasinoCard>> floorCombos = findFloorCaptureCombinations(val);
            // B. Matching builds
            List<String> matchingBuildIds = new ArrayList<>();
            for (KhasinoBuild b : builds.values()) {
                if (b.getCaptureValue() == val) {
                    matchingBuildIds.add(b.getId());
                }
            }

            if (!floorCombos.isEmpty() || !matchingBuildIds.isEmpty()) {
                if (!floorCombos.isEmpty() && !matchingBuildIds.isEmpty()) {
                    // Combine all matching floor cards and builds
                    List<String> allFloorIds = new ArrayList<>();
                    for (List<KhasinoCard> combo : floorCombos) {
                        for (KhasinoCard fc : combo) allFloorIds.add(fc.getId());
                    }
                    actions.add(KhasinoAction.chow(card.getId(), allFloorIds, matchingBuildIds));
                } else if (!floorCombos.isEmpty()) {
                    for (List<KhasinoCard> combo : floorCombos) {
                        List<String> fIds = new ArrayList<>();
                        for (KhasinoCard fc : combo) fIds.add(fc.getId());
                        actions.add(KhasinoAction.chow(card.getId(), fIds, Collections.emptyList()));
                    }
                } else {
                    actions.add(KhasinoAction.chow(card.getId(), Collections.emptyList(), matchingBuildIds));
                }
            }

            // 2. Simple Builds
            for (int target = val + 1; target <= 10; target++) {
                // Must hold another card of value target
                boolean holdsTarget = false;
                for (KhasinoCard h : hand) {
                    if (!h.getId().equals(card.getId()) && h.getValue() == target) {
                        holdsTarget = true;
                        break;
                    }
                }
                if (!holdsTarget) continue;

                // Check no existing build of this value
                boolean exists = false;
                for (KhasinoBuild b : builds.values()) {
                    if (b.getCaptureValue() == target) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;

                int needed = target - val;
                List<List<KhasinoCard>> validSubsets = findSubsetsSummingTo(floorCards, needed);
                for (List<KhasinoCard> sub : validSubsets) {
                    List<String> fIds = new ArrayList<>();
                    for (KhasinoCard fc : sub) fIds.add(fc.getId());
                    actions.add(KhasinoAction.buildSimple(card.getId(), fIds, target));
                }
            }

            // 3. Compound Builds / Augmentation of owned/partner builds
            for (KhasinoBuild b : builds.values()) {
                boolean isOwned = b.getOwnerId().equals(playerId);
                boolean isPartner = (mode == Mode.FOUR_PLAYER_PARTNERSHIPS && b.getTeamId() == playerTeam);
                if (isOwned || isPartner) {
                    int bVal = b.getCaptureValue();
                    if (val == bVal) {
                        // Stash or augment with single card
                        actions.add(KhasinoAction.stash(b.getId(), card.getId()));
                    } else if (val < bVal) {
                        int needed = bVal - val;
                        List<List<KhasinoCard>> subsets = findSubsetsSummingTo(floorCards, needed);
                        for (List<KhasinoCard> sub : subsets) {
                            List<String> fIds = new ArrayList<>();
                            for (KhasinoCard fc : sub) fIds.add(fc.getId());
                            actions.add(KhasinoAction.buildCompound(b.getId(), card.getId(), fIds, null, null));
                        }
                    }
                }
            }

            // 4. Changing Opponent Simple Build Value
            for (KhasinoBuild b : builds.values()) {
                boolean isOpp = !b.getOwnerId().equals(playerId) && !arePartners(playerId, b.getOwnerId());
                if (isOpp && !b.isCompound()) {
                    int newVal = b.getCaptureValue() + val;
                    if (newVal <= 10) {
                        boolean holdsNewVal = false;
                        for (KhasinoCard h : hand) {
                            if (!h.getId().equals(card.getId()) && h.getValue() == newVal) {
                                holdsNewVal = true;
                                break;
                            }
                        }
                        if (holdsNewVal) {
                            actions.add(KhasinoAction.changeBuildValue(b.getId(), card.getId(), newVal));
                        }
                    }
                }
            }

            // 5. Drifting
            boolean canDrift = true;
            if (mode == Mode.TWO_PLAYER && dealNumber == 1) {
                for (KhasinoBuild b : builds.values()) {
                    if (b.getOwnerId().equals(playerId)) {
                        canDrift = false;
                        break;
                    }
                }
            }
            if (canDrift) {
                actions.add(KhasinoAction.drift(card.getId()));
            }
        }

        return actions;
    }

    private List<List<KhasinoCard>> findFloorCaptureCombinations(int targetVal) {
        List<List<KhasinoCard>> validCombos = new ArrayList<>();

        // Exact single matches
        List<KhasinoCard> singles = new ArrayList<>();
        for (KhasinoCard fc : floorCards) {
            if (fc.getValue() == targetVal) singles.add(fc);
        }
        if (!singles.isEmpty()) {
            validCombos.add(singles);
        }

        // Subsets summing to targetVal
        List<KhasinoCard> nonExact = new ArrayList<>();
        for (KhasinoCard fc : floorCards) {
            if (fc.getValue() < targetVal) nonExact.add(fc);
        }
        List<List<KhasinoCard>> subsets = findSubsetsSummingTo(nonExact, targetVal);
        validCombos.addAll(subsets);

        return validCombos;
    }

    private void logAction(String msg) {
        actionLog.add(msg);
        if (actionLog.size() > 50) actionLog.remove(0);
    }

    public List<String> getActionLog() {
        return Collections.unmodifiableList(actionLog);
    }

    @Override
    public GameState move(String playerId, Object moveData) {
        if (moveData instanceof KhasinoAction action) {
            executeAction(playerId, action);
        }
        return getGameState();
    }

    @Override
    public GameState getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mode", mode.name());
        state.put("dealNumber", dealNumber);
        state.put("currentTurn", getCurrentTurnPlayer());
        state.put("gameOver", gameOver);
        state.put("winnerId", winnerId);
        return new GameState(gameOver, getCurrentTurnPlayer(), state, winnerId, lastActionMessage);
    }

    public String toJson() {
        return toJson(null);
    }

    public String toJson(String requestingPlayerId) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"mode\":\"").append(mode.name()).append("\",");
        sb.append("\"dealNumber\":").append(dealNumber).append(",");
        sb.append("\"isElevenPoint\":").append(isElevenPointMode()).append(",");
        sb.append("\"isElevenPointMode\":").append(isElevenPointMode()).append(",");
        sb.append("\"currentTurn\":\"").append(getCurrentTurnPlayer()).append("\",");
        sb.append("\"gameOver\":").append(gameOver).append(",");
        sb.append("\"isOver\":").append(gameOver).append(",");
        sb.append("\"winnerId\":").append(winnerId != null ? "\"" + winnerId + "\"" : "null").append(",");
        sb.append("\"lastAction\":\"").append(lastActionMessage.replace("\"", "\\\"")).append("\",");
        sb.append("\"deckRemaining\":").append(deck.size()).append(",");

        // Floor cards
        sb.append("\"floorCards\":[");
        for (int i = 0; i < floorCards.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(floorCards.get(i).toJson());
        }
        sb.append("],");

        // Builds as array
        sb.append("\"builds\":[");
        int count = 0;
        for (KhasinoBuild b : builds.values()) {
            if (count++ > 0) sb.append(",");
            sb.append(b.toJson());
        }
        sb.append("],");

        // Builds map for key lookup
        sb.append("\"buildsMap\":{");
        count = 0;
        for (Map.Entry<String, KhasinoBuild> entry : builds.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().toJson());
        }
        sb.append("},");

        // Hand for requesting player
        sb.append("\"hand\":[");
        List<KhasinoCard> myHand = (requestingPlayerId != null) ? hands.get(requestingPlayerId) : null;
        if (myHand == null && !hands.isEmpty()) {
            myHand = hands.values().iterator().next();
        }
        if (myHand != null) {
            for (int i = 0; i < myHand.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(myHand.get(i).toJson());
            }
        }
        sb.append("],");

        // Card counts for all players
        sb.append("\"cardCounts\":{");
        count = 0;
        for (Map.Entry<String, List<KhasinoCard>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().size());
        }
        sb.append("},");

        // Opponent counts alias
        sb.append("\"opponentCounts\":{");
        count = 0;
        for (Map.Entry<String, List<KhasinoCard>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().size());
        }
        sb.append("},");

        // Captured counts and top exposed cards info
        sb.append("\"capturedInfo\":{");
        count = 0;
        for (Map.Entry<String, List<KhasinoCard>> entry : capturedPiles.entrySet()) {
            if (count++ > 0) sb.append(",");
            String key = entry.getKey();
            List<KhasinoCard> pile = entry.getValue();
            KhasinoCard top = pile.isEmpty() ? null : pile.get(pile.size() - 1);
            int sweeps = sweepCounts.getOrDefault(key, 0);
            sb.append("\"").append(key).append("\":{")
              .append("\"count\":").append(pile.size()).append(",")
              .append("\"sweeps\":").append(sweeps).append(",")
              .append("\"topCard\":").append(top != null ? top.toJson() : "null")
              .append("}");
        }
        sb.append("},");

        // Full captured piles as arrays of card objects
        sb.append("\"capturedPiles\":{");
        count = 0;
        for (Map.Entry<String, List<KhasinoCard>> entry : capturedPiles.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":[");
            List<KhasinoCard> p = entry.getValue();
            for (int i = 0; i < p.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(p.get(i).toJson());
            }
            sb.append("]");
        }
        sb.append("},");

        // Sweep counts
        sb.append("\"sweepCounts\":{");
        count = 0;
        for (Map.Entry<String, Integer> entry : sweepCounts.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        sb.append("},");

        // Player Teams
        sb.append("\"playerTeams\":{");
        count = 0;
        for (Map.Entry<String, Integer> entry : playerTeams.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
        }
        sb.append("},");

        // Scores (if game ended or live preview)
        Map<String, KhasinoScoring.ScoreBreakdown> liveScores = (finalScores != null) ? finalScores :
                KhasinoScoring.calculateScores(capturedPiles, sweepCounts, isElevenPointMode());
        sb.append("\"scores\":{");
        count = 0;
        for (Map.Entry<String, KhasinoScoring.ScoreBreakdown> entry : liveScores.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().toJson());
        }
        sb.append("},");

        sb.append("\"finalScores\":{");
        count = 0;
        for (Map.Entry<String, KhasinoScoring.ScoreBreakdown> entry : liveScores.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().toJson());
        }
        sb.append("},");

        // Hands: include all hands
        sb.append("\"hands\":{");
        count = 0;
        for (Map.Entry<String, List<KhasinoCard>> entry : hands.entrySet()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":[");
            List<KhasinoCard> h = entry.getValue();
            for (int j = 0; j < h.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(h.get(j).toJson());
            }
            sb.append("]");
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }
}
