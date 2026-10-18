package com.robinhood.engine.ludo;

import java.util.List;

/**
 * Intelligent deterministic AI opponent for Ludo.
 * Evaluates candidate legal moves and selects moves based on winning, captures,
 * exiting the yard, safe squares, and risk mitigation.
 */
public class LudoAi {

    public static int decideTokenMove(LudoGame game, String botPlayerId) {
        if (game == null || botPlayerId == null) return -1;
        List<Integer> legal = game.getLegalMoves(botPlayerId);
        if (legal.isEmpty()) return -1;
        if (legal.size() == 1) return legal.get(0);

        List<LudoToken> tokens = game.getTokens(botPlayerId);
        int roll = game.getCurrentRoll();
        LudoColor myColor = game.getPlayerColor(botPlayerId);

        int bestTokenId = legal.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (int tokenId : legal) {
            LudoToken token = tokens.get(tokenId);
            int score = scoreMove(game, botPlayerId, token, roll, myColor);
            if (score > bestScore) {
                bestScore = score;
                bestTokenId = tokenId;
            }
        }

        return bestTokenId;
    }

    private static int scoreMove(LudoGame game, String botPlayerId, LudoToken token, int roll, LudoColor myColor) {
        int score = 0;
        int currentStep = token.getStep();
        int targetStep = token.isInYard() ? 0 : currentStep + roll;

        // 1. Immediate Win Priority: Reaching Home Center (Step 56)
        if (targetStep == LudoBoard.FINISH_STEP) {
            return 10_000;
        }

        // 2. Capture Opponent Priority: Landing on an opponent on an unsafe track square
        if (targetStep <= 50) {
            int targetTrackSquare = (myColor.getStartTrackIndex() + targetStep) % LudoBoard.TRACK_LENGTH;
            if (!LudoBoard.isSafeTrackSquare(targetTrackSquare)) {
                // Check if any opponent token occupies this square
                for (LudoColor color : LudoColor.values()) {
                    if (color == myColor) continue;
                    // Check all tokens in game
                    for (LudoToken oppToken : getOtherPlayerTokens(game, botPlayerId)) {
                        if (oppToken.isOnTrack() && oppToken.getTrackSquare() == targetTrackSquare) {
                            score += 3_000; // Major reward for capturing!
                        }
                    }
                }
            }
        }

        // 3. Exiting Yard on 6: Getting tokens into play
        if (token.isInYard() && roll == 6) {
            score += 1_500;
        }

        // 4. Entering Home Column: Secure from all captures
        if (currentStep <= 50 && targetStep >= 51) {
            score += 800;
        }

        // 5. Landing on Safe Square (Star or Starting Square)
        if (targetStep <= 50) {
            int targetTrackSquare = (myColor.getStartTrackIndex() + targetStep) % LudoBoard.TRACK_LENGTH;
            if (LudoBoard.isSafeTrackSquare(targetTrackSquare)) {
                score += 500;
            }
        }

        // 6. Escaping Danger: Token is currently on unsafe square with an opponent within 1-6 squares behind
        if (token.isOnTrack() && !token.isOnSafeSquare()) {
            int myTrack = token.getTrackSquare();
            boolean inDanger = false;
            for (LudoToken opp : getOtherPlayerTokens(game, botPlayerId)) {
                if (opp.isOnTrack()) {
                    int dist = (myTrack - opp.getTrackSquare() + LudoBoard.TRACK_LENGTH) % LudoBoard.TRACK_LENGTH;
                    if (dist >= 1 && dist <= 6) {
                        inDanger = true;
                        break;
                    }
                }
            }
            if (inDanger) {
                score += 400; // Move threatened piece
            }
        }

        // 7. Progress towards finish: Advance pieces further along
        score += targetStep * 5;

        return score;
    }

    private static List<LudoToken> getOtherPlayerTokens(LudoGame game, String myPlayerId) {
        java.util.List<LudoToken> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, List<LudoToken>> entry : game.getAllPlayerTokens().entrySet()) {
            if (!entry.getKey().equals(myPlayerId)) {
                list.addAll(entry.getValue());
            }
        }
        return list;
    }
}
