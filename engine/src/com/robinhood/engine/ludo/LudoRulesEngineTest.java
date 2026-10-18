package com.robinhood.engine.ludo;

import com.robinhood.engine.core.Player;
import java.util.*;

/**
 * Automated test suite for the Ludo rules engine covering all 14 mandatory rules cases.
 */
public class LudoRulesEngineTest {

    private static int testsPassed = 0;
    private static int testsTotal = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   Ludo Authoritative Rules Engine Test Suite   ");
        System.out.println("=================================================");

        testCase1_Initialization();
        testCase2_CannotLeaveYardWithoutSix();
        testCase3_SixGrantsBonusRoll();
        testCase4_ThreeConsecutiveSixesForfeitTurn();
        testCase5_ValidMovementDirectionAndTrack();
        testCase6_InvalidMovementRejected();
        testCase7_CannotMoveOtherPlayersToken();
        testCase8_SafeSquaresPreventCaptures();
        testCase9_CapturesReturnOpponentToYard();
        testCase10_HomeColumnEntry();
        testCase11_ExactRollFinishingEnforced();
        testCase12_WinOnlyWhenAllFourTokensHome();
        testCase13_NoLegalMovesAdvancesTurnCleanly();
        testCase14_ActionsAfterCompletionRejected();

        System.out.println("=================================================");
        System.out.printf("Results: %d/%d tests PASSED!\n", testsPassed, testsTotal);
        System.out.println("=================================================");

        if (testsPassed != testsTotal) {
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String details) {
        testsTotal++;
        if (condition) {
            testsPassed++;
            System.out.printf("  [PASS] %-45s\n", testName);
        } else {
            System.err.printf("  [FAIL] %-45s -> %s\n", testName, details);
        }
    }

    // 1. A new game initializes the correct players and token positions.
    private static void testCase1_Initialization() {
        LudoGame game = new LudoGame();
        List<Player> players = List.of(new Player("p1", "Alice"), new Player("p2", "Bob"));
        game.start(players);

        boolean p1Red = game.getPlayerColor("p1") == LudoColor.RED;
        boolean p2Yellow = game.getPlayerColor("p2") == LudoColor.YELLOW;
        boolean allYard = true;
        for (LudoToken t : game.getTokens("p1")) {
            if (!t.isInYard() || t.getStep() != -1) allYard = false;
        }
        for (LudoToken t : game.getTokens("p2")) {
            if (!t.isInYard() || t.getStep() != -1) allYard = false;
        }

        assertTrue("Case 1: Game Initialization & Yard Setup",
                p1Red && p2Yellow && allYard && !game.isOver(),
                "Expected p1=RED, p2=YELLOW, and all 8 tokens in yard");
    }

    // 2. Tokens cannot leave the yard without a six.
    private static void testCase2_CannotLeaveYardWithoutSix() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Roll 5
        Map<String, Object> rollRes = game.rollDice("p1", 5);
        boolean noMoves = Boolean.TRUE.equals(rollRes.get("noLegalMoves"));
        boolean advancedToP2 = "p2".equals(game.getCurrentTurnPlayer());

        assertTrue("Case 2: Cannot leave yard without a 6",
                noMoves && advancedToP2,
                "Rolling 5 with all tokens in yard should yield no legal moves and pass turn");
    }

    // 3. A six correctly grants an extra roll.
    private static void testCase3_SixGrantsBonusRoll() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Roll 6
        Map<String, Object> rollRes = game.rollDice("p1", 6);
        List<Integer> legal = game.getLegalMoves("p1");
        boolean canMove = legal.contains(0);

        // Move token 0 out of yard
        Map<String, Object> moveRes = game.moveToken("p1", 0);
        boolean bonus = Boolean.TRUE.equals(moveRes.get("bonusRoll"));
        boolean stillP1 = "p1".equals(game.getCurrentTurnPlayer());
        boolean waitingRoll = game.getPhase() == LudoGame.TurnPhase.WAITING_FOR_ROLL;

        assertTrue("Case 3: Six grants bonus roll on same turn",
                canMove && bonus && stillP1 && waitingRoll,
                "Moving with a 6 must grant an extra roll to active player");
    }

    // 4. Three consecutive sixes resolve according to the selected rules.
    private static void testCase4_ThreeConsecutiveSixesForfeitTurn() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Six #1
        game.rollDice("p1", 6);
        game.moveToken("p1", 0); // Exits yard to 0

        // Six #2
        game.rollDice("p1", 6);
        game.moveToken("p1", 0); // Advances from 0 to 6

        // Six #3
        Map<String, Object> roll3 = game.rollDice("p1", 6);
        boolean penalty = Boolean.TRUE.equals(roll3.get("consecutiveSixPenalty"));
        boolean turnPassed = "p2".equals(game.getCurrentTurnPlayer());

        assertTrue("Case 4: 3 consecutive sixes forfeits turn",
                penalty && turnPassed,
                "Third consecutive six must forfeit turn and pass to next player");
    }

    // 5. Valid token movement uses the correct path and direction.
    private static void testCase5_ValidMovementDirectionAndTrack() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // P1 (RED): starts at track index 0
        game.rollDice("p1", 6);
        game.moveToken("p1", 0); // Step 0 -> Track square 0
        LudoToken t0 = game.getTokens("p1").get(0);
        int trackSq0 = t0.getTrackSquare();

        game.rollDice("p1", 4);
        game.moveToken("p1", 0); // Step 4 -> Track square 4
        int trackSq4 = t0.getTrackSquare();

        assertTrue("Case 5: Clockwise track movement & indexing",
                trackSq0 == 0 && trackSq4 == 4 && t0.getStep() == 4,
                "Expected token to advance clockwise along track from 0 to 4");
    }

    // 6. Invalid token movement is rejected.
    private static void testCase6_InvalidMovementRejected() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Attempt to move without rolling
        Map<String, Object> errMove = game.moveToken("p1", 0);
        boolean rejectedNoRoll = Boolean.FALSE.equals(errMove.get("success"));

        // Roll 6
        game.rollDice("p1", 6);
        // Attempt to move invalid token ID 99
        Map<String, Object> errToken = game.moveToken("p1", 99);
        boolean rejectedInvalidId = Boolean.FALSE.equals(errToken.get("success"));

        assertTrue("Case 6: Invalid movement requests rejected",
                rejectedNoRoll && rejectedInvalidId,
                "Moving without roll or with invalid token ID must fail");
    }

    // 7. A player cannot move another player's token.
    private static void testCase7_CannotMoveOtherPlayersToken() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        game.rollDice("p1", 6);
        // P2 attempts to move while it is P1's turn
        Map<String, Object> p2Move = game.moveToken("p2", 0);
        boolean rejected = Boolean.FALSE.equals(p2Move.get("success"));

        assertTrue("Case 7: Player cannot move another player's token",
                rejected,
                "Action from non-active player must be rejected");
    }

    // 8. Safe squares prevent captures.
    private static void testCase8_SafeSquaresPreventCaptures() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Red (P1) exits yard to Track 0 (which is Red's start square, a SAFE square)
        LudoToken redToken = game.getTokens("p1").get(0);
        redToken.setStep(0); // Track square 0 (Safe)

        // Yellow (P2) is on Track 50. Track 52 wraps around to 0.
        // Yellow start is 26. Track 0 is Yellow step (0 - 26 + 52) = 26.
        LudoToken yellowToken = game.getTokens("p2").get(0);
        yellowToken.setStep(20); // Track 46

        // Yellow rolls 6 to land on Track 0 (Step 26 for Yellow)
        // Simulate P2 turn:
        // Set turn to P2
        game.rollDice("p1", 1); // P1 rolls 1 with tokens in yard -> passes to P2
        game.rollDice("p2", 6); // P2 rolls 6
        game.moveToken("p2", 0); // Yellow moves to step 26 (Track 0)

        // Check if Red token is still at Step 0 (NOT sent to yard because Track 0 is safe!)
        boolean redStillSafe = redToken.getStep() == 0;

        assertTrue("Case 8: Safe squares prevent captures",
                redStillSafe,
                "Token on safe starting square must not be captured by opponent");
    }

    // 9. Captures return the correct opponent token to its yard.
    private static void testCase9_CapturesReturnOpponentToYard() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // Position P2's Yellow token on Track Square 6 (NOT safe)
        // Yellow start is 26. Track 6 is Yellow step (6 - 26 + 52) = 32.
        LudoToken yellowToken = game.getTokens("p2").get(0);
        yellowToken.setStep(32); // Track square 6

        // Position P1's Red token on Track Square 2
        LudoToken redToken = game.getTokens("p1").get(0);
        redToken.setStep(2); // Track square 2

        // P1 rolls 4 (from Track 2 to Track 6)
        game.rollDice("p1", 4);
        Map<String, Object> moveRes = game.moveToken("p1", 0);

        boolean captured = yellowToken.isInYard() && yellowToken.getStep() == -1;
        boolean redAt6 = redToken.getTrackSquare() == 6;

        assertTrue("Case 9: Captures return opponent token to yard",
                captured && redAt6,
                "Landing on opponent on unsafe square must send opponent token to yard");
    }

    // 10. Home-column entry works correctly.
    private static void testCase10_HomeColumnEntry() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        LudoToken redToken = game.getTokens("p1").get(0);
        redToken.setStep(50); // Final track square before home column

        // Roll 3 -> enters home column at step 53
        game.rollDice("p1", 3);
        game.moveToken("p1", 0);

        boolean inHomeCol = redToken.isInHomeColumn() && redToken.getStep() == 53;
        int trackSq = redToken.getTrackSquare(); // Should be -1 since it's off the outer track

        assertTrue("Case 10: Home column entry works correctly",
                inHomeCol && trackSq == -1,
                "Token advancing beyond step 50 must enter private home column");
    }

    // 11. Exact-roll finishing is enforced.
    private static void testCase11_ExactRollFinishingEnforced() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        LudoToken redToken = game.getTokens("p1").get(0);
        redToken.setStep(54); // Needs exactly 2 to reach finish step 56

        // Roll 4 (overshoot)
        game.rollDice("p1", 4);
        List<Integer> legalOvershoot = game.getLegalMoves("p1");
        boolean rejectedOvershoot = !legalOvershoot.contains(0);

        // Turn passed to P2; pass P2 back to P1
        game.rollDice("p2", 1); // P2 passes

        // Roll 2 (exact finish!)
        game.rollDice("p1", 2);
        List<Integer> legalExact = game.getLegalMoves("p1");
        boolean allowedExact = legalExact.contains(0);
        game.moveToken("p1", 0);
        boolean finished = redToken.isFinished() && redToken.getStep() == 56;

        assertTrue("Case 11: Exact-roll finishing enforced",
                rejectedOvershoot && allowedExact && finished,
                "Must require exact roll to reach finish step 56");
    }

    // 12. A player wins only after all four tokens reach home.
    private static void testCase12_WinOnlyWhenAllFourTokensHome() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        List<LudoToken> p1Tokens = game.getTokens("p1");
        p1Tokens.get(0).setStep(56);
        p1Tokens.get(1).setStep(56);
        p1Tokens.get(2).setStep(56);
        p1Tokens.get(3).setStep(55); // Needs 1 to finish

        // 3 tokens home, not yet over
        boolean notOverWith3 = !game.isOver();

        // Roll 1 for P1 and move token 3
        game.rollDice("p1", 1);
        Map<String, Object> winMove = game.moveToken("p1", 3);

        boolean won = game.isOver() && "p1".equals(game.getWinnerId()) && Boolean.TRUE.equals(winMove.get("gameOver"));

        assertTrue("Case 12: Win detected only after all 4 tokens reach home",
                notOverWith3 && won,
                "Game must complete with winner declared only when all 4 tokens are home");
    }

    // 13. No legal moves resolve the turn correctly.
    private static void testCase13_NoLegalMovesAdvancesTurnCleanly() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        // P1 rolls 3 with all tokens in yard
        Map<String, Object> res = game.rollDice("p1", 3);
        boolean noMoves = Boolean.TRUE.equals(res.get("noLegalMoves"));
        boolean activeNowP2 = "p2".equals(game.getCurrentTurnPlayer());
        boolean waitingRoll = game.getPhase() == LudoGame.TurnPhase.WAITING_FOR_ROLL;

        assertTrue("Case 13: No legal moves cleanly advances turn",
                noMoves && activeNowP2 && waitingRoll,
                "Rolling without legal moves must advance turn cleanly without getting stuck");
    }

    // 14. Actions after completion are rejected.
    private static void testCase14_ActionsAfterCompletionRejected() {
        LudoGame game = new LudoGame();
        game.start(List.of(new Player("p1", "Alice"), new Player("p2", "Bob")));

        List<LudoToken> p1Tokens = game.getTokens("p1");
        p1Tokens.get(0).setStep(56);
        p1Tokens.get(1).setStep(56);
        p1Tokens.get(2).setStep(56);
        p1Tokens.get(3).setStep(55); // Needs 1 to finish

        game.rollDice("p1", 1);
        game.moveToken("p1", 3); // Finishes 4th token -> game is over!

        // Attempt actions after game over
        Map<String, Object> postRoll = game.rollDice("p1", 6);
        Map<String, Object> postMove = game.moveToken("p1", 0);

        boolean rollBlocked = Boolean.FALSE.equals(postRoll.get("success"));
        boolean moveBlocked = Boolean.FALSE.equals(postMove.get("success"));

        assertTrue("Case 14: Actions after game completion rejected",
                rollBlocked && moveBlocked && game.isOver(),
                "All actions after game completion must be rejected");
    }
}
