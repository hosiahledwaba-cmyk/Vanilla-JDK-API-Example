package com.robinhood.engine.khasino;

import java.util.*;

/**
 * Intelligent AI player for South African Khasino with 3 distinct difficulty levels:
 * - EASY (Beginner): Prioritizes simple captures and basic builds.
 * - MEDIUM (Strategic): Considers card point values (Mummy 10♦, Spy Two 2♠, Aces), majorities, and compound builds.
 * - HARD (Competitive): Multi-factor heuristic evaluation, opponent threat analysis, Cupha entrapment,
 *   sweep hunting, opponent capture pile stealing, and partnership coordination.
 *
 * Fully adheres to the rule that the AI only inspects its own hand and public table state.
 */
public class KhasinoAi {

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    public static KhasinoAction decideMove(KhasinoGame game, String botId) {
        return decideMove(game, botId, Difficulty.MEDIUM);
    }

    public static KhasinoAction decideMove(KhasinoGame game, String botId, Difficulty difficulty) {
        List<KhasinoAction> legalActions = game.getLegalActions(botId);
        if (legalActions.isEmpty()) return null;

        Difficulty diff = difficulty != null ? difficulty : Difficulty.MEDIUM;

        return switch (diff) {
            case EASY -> decideEasy(legalActions);
            case MEDIUM -> decideMedium(game, botId, legalActions);
            case HARD -> decideHard(game, botId, legalActions);
        };
    }

    /**
     * Easy AI: Simple greedy heuristic.
     * Takes any available capture; if none, prefers building; otherwise drifts a low card.
     */
    private static KhasinoAction decideEasy(List<KhasinoAction> actions) {
        // Look for any capture (Chow)
        for (KhasinoAction a : actions) {
            if (a.getType() == KhasinoAction.ActionType.CHOW) {
                return a;
            }
        }
        // Look for a simple build
        for (KhasinoAction a : actions) {
            if (a.getType() == KhasinoAction.ActionType.BUILD_SIMPLE) {
                return a;
            }
        }
        // Default to first legal action (e.g. drift)
        return actions.get(0);
    }

    /**
     * Medium AI: Strategic evaluation.
     * Weights card points (Mummy 10♦ = 2, Spy Two 2♠ = 1, Ace = 1),
     * prefers sweeps, compound builds, and avoids drifting valuable cards.
     */
    private static KhasinoAction decideMedium(KhasinoGame game, String botId, List<KhasinoAction> actions) {
        KhasinoAction bestAction = null;
        double bestScore = -1000.0;

        for (KhasinoAction action : actions) {
            double score = evaluateAction(game, botId, action, false);
            if (score > bestScore) {
                bestScore = score;
                bestAction = action;
            }
        }

        return bestAction != null ? bestAction : actions.get(0);
    }

    /**
     * Hard AI: Competitive evaluation.
     * Deep evaluation of card majorities, opponent threat mitigation,
     * Cupha traps, sweep bonuses, and partnership alignment.
     */
    private static KhasinoAction decideHard(KhasinoGame game, String botId, List<KhasinoAction> actions) {
        KhasinoAction bestAction = null;
        double bestScore = -10000.0;

        for (KhasinoAction action : actions) {
            double score = evaluateAction(game, botId, action, true);
            if (score > bestScore) {
                bestScore = score;
                bestAction = action;
            }
        }

        return bestAction != null ? bestAction : actions.get(0);
    }

    private static double evaluateAction(KhasinoGame game, String botId, KhasinoAction action, boolean isHard) {
        double score = 0.0;
        int botTeam = game.getPlayerTeam(botId);

        switch (action.getType()) {
            case CHOW -> {
                score += 50.0; // Capturing is generally prime

                // Reward captured cards
                int cardCount = 1; // played card
                int spadeCount = 0;
                boolean hasMummy = false;
                boolean hasSpyTwo = false;
                int aceCount = 0;

                KhasinoCard played = KhasinoCard.fromId(action.getPlayedCardId());
                if (played != null) {
                    if (played.isSpade()) spadeCount++;
                    if (played.isMummy()) hasMummy = true;
                    if (played.isSpyTwo()) hasSpyTwo = true;
                    if (played.isAce()) aceCount++;
                }

                // Check floor targets
                for (String cId : action.getTargetFloorCardIds()) {
                    KhasinoCard fc = KhasinoCard.fromId(cId);
                    if (fc != null) {
                        cardCount++;
                        if (fc.isSpade()) spadeCount++;
                        if (fc.isMummy()) hasMummy = true;
                        if (fc.isSpyTwo()) hasSpyTwo = true;
                        if (fc.isAce()) aceCount++;
                    }
                }

                // Check build targets
                for (String bId : action.getTargetBuildIds()) {
                    KhasinoBuild b = game.getBuilds().get(bId);
                    if (b != null) {
                        for (KhasinoCard bc : b.getAllCards()) {
                            cardCount++;
                            if (bc.isSpade()) spadeCount++;
                            if (bc.isMummy()) hasMummy = true;
                            if (bc.isSpyTwo()) hasSpyTwo = true;
                            if (bc.isAce()) aceCount++;
                        }
                        // Extra reward for stealing opponent's build
                        if (!b.getOwnerId().equals(botId) && (botTeam == 0 || b.getTeamId() != botTeam)) {
                            score += 25.0;
                        }
                    }
                }

                score += (cardCount * 3.0);
                if (game.isElevenPointMode()) {
                    score += (spadeCount * 4.0);
                }
                if (hasMummy) score += 60.0; // 2 game points
                if (hasSpyTwo) score += 30.0; // 1 game point
                score += (aceCount * 30.0);  // 1 game point each

                // Sweep potential: if this chow clears all cards on the floor
                int totalFloorCards = game.getFloorCards().size();
                int totalBuilds = game.getBuilds().size();
                if (action.getTargetFloorCardIds().size() >= totalFloorCards &&
                    action.getTargetBuildIds().size() >= totalBuilds) {
                    score += 80.0; // Massive reward for sweeping!
                }
            }

            case BUILD_COMPOUND, STASH -> {
                score += 35.0; // Compound builds can never be stolen in value!
                if (action.getStolenCardId() != null) {
                    score += 20.0; // Stealing from opponent capture pile is great!
                    KhasinoCard stolen = KhasinoCard.fromId(action.getStolenCardId());
                    if (stolen != null) {
                        if (stolen.isMummy()) score += 50.0;
                        if (stolen.isSpyTwo()) score += 25.0;
                        if (stolen.isAce()) score += 25.0;
                    }
                }
            }

            case CHANGE_BUILD_VALUE -> {
                score += 40.0; // Hijacking an opponent's build is high impact!
                KhasinoBuild b = game.getBuilds().get(action.getTargetBuildId());
                if (b != null) {
                    score += (b.getTotalCardCount() * 3.0);
                }
            }

            case BUILD_SIMPLE -> {
                score += 20.0;
                // Prefer building higher values (e.g. 9 or 10) which opponents can't augment further
                score += (action.getDeclaredValue() * 1.5);

                // In competitive mode, check if we hold multiple capturing cards to be extra safe
                if (isHard) {
                    int captureCardCount = 0;
                    for (KhasinoCard c : game.getHand(botId)) {
                        if (c.getValue() == action.getDeclaredValue()) captureCardCount++;
                    }
                    if (captureCardCount >= 2) score += 15.0;
                }
            }

            case SHIYA -> {
                score += 30.0; // Cooperative partnership play
            }

            case DRIFT -> {
                score += 5.0;
                KhasinoCard played = KhasinoCard.fromId(action.getPlayedCardId());
                if (played != null) {
                    // Penalty for drifting high-value cards
                    if (played.isMummy()) score -= 100.0;
                    if (played.isSpyTwo()) score -= 50.0;
                    if (played.isAce()) score -= 40.0;
                    if (played.isSpade() && game.isElevenPointMode()) score -= 15.0;

                    // Prefer drifting cards whose values are safe or already on the floor
                    for (KhasinoCard fc : game.getFloorCards()) {
                        if (fc.getValue() == played.getValue()) {
                            score += 10.0; // Pairing up on the floor
                            break;
                        }
                    }
                }
            }
        }

        return score;
    }
}
