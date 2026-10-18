package com.robinhood.engine.khasino;

import java.util.*;

/**
 * Implements the authentic South African Khasino scoring rules.
 *
 * 11-point system (2-player and 4-player 2v2 partnerships):
 * - Ten of Diamonds ("Mummy"): 2 points
 * - Two of Spades ("Spy Two"): 1 point
 * - Each Ace: 1 point (4 points total)
 * - Most captured cards: 2 points (split 1-1 if tied)
 * - Most captured spades: 2 points (split 1-1 if tied)
 * - Sweeps: 1 point per sweep recorded
 *
 * 7-point system (3-player and 4-player singles):
 * - Ten of Diamonds ("Mummy"): 2 points
 * - Two of Spades ("Spy Two"): 1 point
 * - Each Ace: 1 point (4 points total)
 * - (Most cards and most spades are neglected)
 * - Sweeps: 1 point per sweep recorded
 *
 * Tie resolution:
 * - If final scores are tied, the player/team who captured the "Mummy" (10 of Diamonds) wins.
 */
public class KhasinoScoring {

    public static class ScoreBreakdown {
        public final String entityId; // playerId or teamId
        public int mummyPoints = 0;   // 2 pts for 10♦
        public int spyTwoPoints = 0;  // 1 pt for 2♠
        public int acePoints = 0;     // 1 pt per Ace
        public int cardsMajorityPoints = 0; // 2 pts (or 1 if tied)
        public int spadesMajorityPoints = 0;// 2 pts (or 1 if tied)
        public int sweepPoints = 0;   // 1 pt per sweep

        public int totalCardsCaptured = 0;
        public int totalSpadesCaptured = 0;
        public boolean hasMummy = false;
        public boolean hasSpyTwo = false;
        public int aceCount = 0;

        public ScoreBreakdown(String entityId) {
            this.entityId = entityId;
        }

        public int getTotalPoints() {
            return mummyPoints + spyTwoPoints + acePoints + cardsMajorityPoints + spadesMajorityPoints + sweepPoints;
        }

        public String toJson() {
            return "{" +
                    "\"entityId\":\"" + entityId + "\"," +
                    "\"totalPoints\":" + getTotalPoints() + "," +
                    "\"mummyPoints\":" + mummyPoints + "," +
                    "\"spyTwoPoints\":" + spyTwoPoints + "," +
                    "\"acePoints\":" + acePoints + "," +
                    "\"cardsMajorityPoints\":" + cardsMajorityPoints + "," +
                    "\"spadesMajorityPoints\":" + spadesMajorityPoints + "," +
                    "\"sweepPoints\":" + sweepPoints + "," +
                    "\"totalCards\":" + totalCardsCaptured + "," +
                    "\"totalSpades\":" + totalSpadesCaptured + "," +
                    "\"hasMummy\":" + hasMummy + "," +
                    "\"hasSpyTwo\":" + hasSpyTwo + "," +
                    "\"aceCount\":" + aceCount +
                    "}";
        }
    }

    /**
     * Calculates score breakdowns for all participants.
     *
     * @param capturedPiles Map of entityId (playerId or "Team 1"/"Team 2") to list of captured cards
     * @param sweepCounts Map of entityId to number of sweeps performed
     * @param isElevenPointMode true for 2-player or 4-player 2v2; false for 3-player or 4-player singles
     * @return Map of entityId to ScoreBreakdown
     */
    public static Map<String, ScoreBreakdown> calculateScores(
            Map<String, List<KhasinoCard>> capturedPiles,
            Map<String, Integer> sweepCounts,
            boolean isElevenPointMode) {

        Map<String, ScoreBreakdown> results = new LinkedHashMap<>();

        int maxCards = -1;
        int maxSpades = -1;

        for (Map.Entry<String, List<KhasinoCard>> entry : capturedPiles.entrySet()) {
            String id = entry.getKey();
            List<KhasinoCard> pile = entry.getValue() != null ? entry.getValue() : Collections.emptyList();
            ScoreBreakdown sb = new ScoreBreakdown(id);

            for (KhasinoCard c : pile) {
                sb.totalCardsCaptured++;
                if (c.isSpade()) sb.totalSpadesCaptured++;
                if (c.isMummy()) {
                    sb.hasMummy = true;
                    sb.mummyPoints = 2;
                }
                if (c.isSpyTwo()) {
                    sb.hasSpyTwo = true;
                    sb.spyTwoPoints = 1;
                }
                if (c.isAce()) {
                    sb.aceCount++;
                    sb.acePoints++;
                }
            }

            int sweeps = (sweepCounts != null) ? sweepCounts.getOrDefault(id, 0) : 0;
            sb.sweepPoints = sweeps;

            if (sb.totalCardsCaptured > maxCards) maxCards = sb.totalCardsCaptured;
            if (sb.totalSpadesCaptured > maxSpades) maxSpades = sb.totalSpadesCaptured;

            results.put(id, sb);
        }

        // Apply majorities if in 11-point mode
        if (isElevenPointMode && maxCards > 0) {
            List<ScoreBreakdown> topCards = new ArrayList<>();
            List<ScoreBreakdown> topSpades = new ArrayList<>();

            for (ScoreBreakdown sb : results.values()) {
                if (sb.totalCardsCaptured == maxCards) topCards.add(sb);
                if (sb.totalSpadesCaptured == maxSpades && maxSpades > 0) topSpades.add(sb);
            }

            // Cards Majority: 2 points if sole leader; if tied between 2 players/teams, split 1-1
            if (topCards.size() == 1) {
                topCards.get(0).cardsMajorityPoints = 2;
            } else if (topCards.size() == 2) {
                topCards.get(0).cardsMajorityPoints = 1;
                topCards.get(1).cardsMajorityPoints = 1;
            }

            // Spades Majority: 2 points if sole leader; if tied between 2 players/teams, split 1-1
            if (topSpades.size() == 1) {
                topSpades.get(0).spadesMajorityPoints = 2;
            } else if (topSpades.size() == 2) {
                topSpades.get(0).spadesMajorityPoints = 1;
                topSpades.get(1).spadesMajorityPoints = 1;
            }
        }

        return results;
    }

    /**
     * Resolves the winner among entities using Khasino rules (highest score, tiebreak with Mummy rule).
     */
    public static String resolveWinner(Map<String, ScoreBreakdown> scores) {
        if (scores == null || scores.isEmpty()) return "";

        int highestScore = -1;
        List<ScoreBreakdown> topScorers = new ArrayList<>();

        for (ScoreBreakdown sb : scores.values()) {
            int total = sb.getTotalPoints();
            if (total > highestScore) {
                highestScore = total;
                topScorers.clear();
                topScorers.add(sb);
            } else if (total == highestScore) {
                topScorers.add(sb);
            }
        }

        if (topScorers.size() == 1) {
            return topScorers.get(0).entityId;
        }

        // Tie-breaker: Mummy Rule (player/team with 10♦ wins)
        for (ScoreBreakdown sb : topScorers) {
            if (sb.hasMummy) {
                return sb.entityId;
            }
        }

        // Fallback: Spy Two
        for (ScoreBreakdown sb : topScorers) {
            if (sb.hasSpyTwo) {
                return sb.entityId;
            }
        }

        return topScorers.get(0).entityId;
    }
}
