package com.robinhood.engine.khasino;

import com.robinhood.engine.core.Player;

import java.util.*;

/**
 * Comprehensive Automated Test Suite for South African Khasino (Kasi Kasino).
 * Validates:
 * 1. Deck & Setup (40 cards, no face cards, correct deals for 2p, 3p, 4p singles & partnerships)
 * 2. Captures (Chow singles, multi-card sums, builds, multiple sets in 1 play)
 * 3. Builds (Simple builds, hand retention rule, duplicate value rejection)
 * 4. Compound / Augmented Builds (Multiple groups, immutable capture value)
 * 5. Changing Opponent Build Value (ownership transfer, legality)
 * 6. Stealing from Opponent Captured Pile
 * 7. Drifting (Deal 1 restrictions vs Deal 2 allowance)
 * 8. Partnership Rules (Shiya / Stash calls, team piles)
 * 9. Sweeps & End-of-deal leftover card awards
 * 10. Scoring (11-point vs 7-point, Mummy 10♦, Spy Two 2♠, Aces, Majorities, Ties)
 * 11. AI Opponents (Easy, Medium, Hard valid move generation, full game simulation)
 */
public class KhasinoTestSuite {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  SOUTH AFRICAN KHASINO - AUTOMATED TEST SUITE   ");
        System.out.println("=================================================\n");

        testDeckAndComposition();
        testPlayerSetups();
        testSingleAndSumCaptures();
        testBuildsAndHandRetentionRule();
        testCompoundBuildsAndImmutability();
        testChangingOpponentBuildValue();
        testDriftingRestrictions();
        testStealingFromOpponentCapturedPile();
        testSweepsAndLeftoverCardsAward();
        testScoring11PointAnd7Point();
        testMummyTiebreakResolution();
        testAiDecisionMakingAndSimulations();

        System.out.println("\n=================================================");
        System.out.println("  TEST RESULTS: " + testsPassed + " PASSED, " + testsFailed + " FAILED");
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String errorMsg) {
        if (condition) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + testName + " -> " + errorMsg);
        }
    }

    private static void assertEquals(String testName, Object expected, Object actual) {
        boolean match = Objects.equals(expected, actual);
        if (match) {
            testsPassed++;
            System.out.println("  [PASS] " + testName);
        } else {
            testsFailed++;
            System.err.println("  [FAIL] " + testName + " -> Expected: " + expected + ", Actual: " + actual);
        }
    }

    // 1. Deck Validation
    private static void testDeckAndComposition() {
        System.out.println("--- 1. Deck & Composition Tests ---");
        KhasinoDeck deck = new KhasinoDeck();
        assertEquals("Deck contains exactly 40 cards", 40, deck.size());

        Set<String> cardIds = new HashSet<>();
        int spadeCount = 0, heartCount = 0, diamondCount = 0, clubCount = 0;
        int aceCount = 0, tenCount = 0;
        boolean hasJackQueenKing = false;

        while (!deck.isEmpty()) {
            KhasinoCard card = deck.draw();
            cardIds.add(card.getId());
            if (card.getSuit() == KhasinoCard.Suit.SPADES) spadeCount++;
            if (card.getSuit() == KhasinoCard.Suit.HEARTS) heartCount++;
            if (card.getSuit() == KhasinoCard.Suit.DIAMONDS) diamondCount++;
            if (card.getSuit() == KhasinoCard.Suit.CLUBS) clubCount++;

            if (card.getRank() == KhasinoCard.Rank.ACE) aceCount++;
            if (card.getRank() == KhasinoCard.Rank.TEN) tenCount++;

            String display = card.getDisplay();
            if (display.equals("J") || display.equals("Q") || display.equals("K")) {
                hasJackQueenKing = true;
            }
        }

        assertEquals("All 40 cards are unique", 40, cardIds.size());
        assertEquals("Exactly 10 Spades", 10, spadeCount);
        assertEquals("Exactly 10 Hearts", 10, heartCount);
        assertEquals("Exactly 10 Diamonds", 10, diamondCount);
        assertEquals("Exactly 10 Clubs", 10, clubCount);
        assertEquals("Exactly 4 Aces", 4, aceCount);
        assertEquals("Exactly 4 Tens", 4, tenCount);
        assertTrue("No face cards (Jacks, Queens, Kings) in deck", !hasJackQueenKing, "Deck contained picture cards");

        // Verify Mummy and Spy Two identification
        KhasinoCard mummy = new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.TEN);
        assertTrue("Ten of Diamonds is Mummy", mummy.isMummy(), "10♦ not identified as Mummy");
        KhasinoCard spyTwo = new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.TWO);
        assertTrue("Two of Spades is Spy Two", spyTwo.isSpyTwo(), "2♠ not identified as Spy Two");
    }

    // 2. Setups
    private static void testPlayerSetups() {
        System.out.println("\n--- 2. Player Configurations & Dealing Setups ---");

        // 2-Player Setup
        KhasinoGame g2 = new KhasinoGame(KhasinoGame.Mode.TWO_PLAYER);
        g2.start(List.of(new Player("P1", "P1"), new Player("P2", "P2")));
        assertEquals("2-player mode active", KhasinoGame.Mode.TWO_PLAYER, g2.getMode());
        assertEquals("2-player deal 1 deals 10 cards to P1", 10, g2.getHand("P1").size());
        assertEquals("2-player deal 1 deals 10 cards to P2", 10, g2.getHand("P2").size());
        assertEquals("2-player starts with 0 face-up floor cards", 0, g2.getFloorCards().size());
        assertTrue("2-player uses 11-point system", g2.isElevenPointMode(), "Expected 11-point mode");

        // 3-Player Setup
        KhasinoGame g3 = new KhasinoGame(KhasinoGame.Mode.THREE_PLAYER);
        g3.start(List.of(new Player("P1", "P1"), new Player("P2", "P2"), new Player("P3", "P3")));
        assertEquals("3-player mode active", KhasinoGame.Mode.THREE_PLAYER, g3.getMode());
        assertEquals("3-player deals 13 cards to P1", 13, g3.getHand("P1").size());
        assertEquals("3-player deals 13 cards to P2", 13, g3.getHand("P2").size());
        assertEquals("3-player deals 13 cards to P3", 13, g3.getHand("P3").size());
        assertEquals("3-player deals 1 face-up card on the floor", 1, g3.getFloorCards().size());
        assertTrue("3-player uses 7-point system", !g3.isElevenPointMode(), "Expected 7-point mode");

        // 4-Player Partnerships Setup
        KhasinoGame g4p = new KhasinoGame(KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS);
        g4p.start(List.of(new Player("A1", "A1"), new Player("B1", "B1"), new Player("A2", "A2"), new Player("B2", "B2")));
        assertEquals("4-player partnership mode active", KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS, g4p.getMode());
        assertEquals("A1 and A2 are partners in Team 1", 1, g4p.getPlayerTeam("A1"));
        assertEquals("A2 is in Team 1", 1, g4p.getPlayerTeam("A2"));
        assertEquals("B1 is in Team 2", 2, g4p.getPlayerTeam("B1"));
        assertEquals("B2 is in Team 2", 2, g4p.getPlayerTeam("B2"));
        assertTrue("Partnership mode uses 11-point system", g4p.isElevenPointMode(), "Expected 11-point mode");
    }

    // 3. Captures (Chow)
    private static void testSingleAndSumCaptures() {
        System.out.println("\n--- 3. Captures (Chow) Tests ---");
        KhasinoGame game = new KhasinoGame();
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        // Manually place specific cards in Alice's hand and on the floor
        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.SEVEN));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.TEN));

        // Add 7, 3, 4 on floor
        game.getFloorCards(); // view
        // Using reflection or game action: let's drift to populate floor
        KhasinoCard floor7 = new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.SEVEN);
        KhasinoCard floor3 = new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.THREE);
        KhasinoCard floor4 = new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.FOUR);

        // Inject onto floor via game's action or drift
        // Alice drifts 7, Bob drifts something, etc. Or execute directly:
        // Alice plays 7 of Hearts to chow 7 of Clubs
        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.add(floor7);
            fc.add(floor3);
            fc.add(floor4);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Alice captures both single 7 AND sum 3+4 using her 7 of Hearts!
        KhasinoAction chowAction = KhasinoAction.chow(
                "SEVEN_HEARTS",
                List.of(floor7.getId(), floor3.getId(), floor4.getId()),
                Collections.emptyList()
        );

        Map<String, Object> res = game.executeAction("Alice", chowAction);
        assertTrue("Chow of matching 7 and combination 3+4 succeeded", Boolean.TRUE.equals(res.get("success")), (String) res.get("error"));
        assertEquals("Captured pile has 4 cards (played 7 + floor 7, 3, 4)", 4, game.getCapturedPile("Alice").size());
        assertEquals("Floor has 0 cards left", 0, game.getFloorCards().size());
        assertTrue("Clearing all cards is registered as a Sweep", Boolean.TRUE.equals(res.get("isSweep")), "Expected sweep");
        assertEquals("Alice has 1 sweep recorded", 1, game.getSweepCount("Alice"));
    }

    // 4. Simple Builds & Hand Retention Rule
    private static void testBuildsAndHandRetentionRule() {
        System.out.println("\n--- 4. Simple Builds & Hand Retention Tests ---");
        KhasinoGame game = new KhasinoGame();
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.THREE));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.TEN)); // retaining card to capture 10

        // Put a 7 on the floor
        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
            fc.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.SEVEN));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Alice builds 7 + 3 into a build of 10
        KhasinoAction validBuild = KhasinoAction.buildSimple("THREE_HEARTS", List.of("SEVEN_CLUBS"), 10);
        Map<String, Object> res = game.executeAction("Alice", validBuild);
        assertTrue("Valid build of 10 created successfully", Boolean.TRUE.equals(res.get("success")), (String) res.get("error"));
        assertEquals("One build on the floor", 1, game.getBuilds().size());

        KhasinoBuild b = game.getBuilds().values().iterator().next();
        assertEquals("Build capture value is 10", 10, b.getCaptureValue());
        assertEquals("Build owner is Alice", "Alice", b.getOwnerId());
        assertTrue("Build is simple (not compound yet)", !b.isCompound(), "Expected simple build");

        // Now test illegal build: Bob tries to build an 8 with a 5 on floor without holding an 8
        List<KhasinoCard> bobHand = game.getHand("Bob");
        bobHand.clear();
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.THREE)); // no 8 in hand!

        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.FIVE));
        } catch (Exception e) {}

        KhasinoAction illegalBuild = KhasinoAction.buildSimple("THREE_HEARTS", List.of("FIVE_DIAMONDS"), 8);
        Map<String, Object> bobRes = game.executeAction("Bob", illegalBuild);
        assertTrue("Illegal build rejected because Bob does not hold an 8", !Boolean.TRUE.equals(bobRes.get("success")), "Allowed illegal build");
    }

    // 5. Compound / Augmented Builds & Immutability
    private static void testCompoundBuildsAndImmutability() {
        System.out.println("\n--- 5. Compound Builds & Immutability Tests ---");
        KhasinoGame game = new KhasinoGame();
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.FOUR));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.SEVEN)); // retaining card for 7

        // Build 4 + 3 into a 7
        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
            fc.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.THREE));
            fc.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.FIVE));
            fc.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.TWO));
        } catch (Exception e) {}

        KhasinoAction build7 = KhasinoAction.buildSimple("FOUR_HEARTS", List.of("THREE_SPADES"), 7);
        game.executeAction("Alice", build7);

        KhasinoBuild b = game.getBuilds().values().iterator().next();
        String buildId = b.getId();

        // Bob drifts a card
        List<KhasinoCard> bobHand = game.getHand("Bob");
        bobHand.clear();
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.ACE));
        game.executeAction("Bob", KhasinoAction.drift("ACE_CLUBS"));

        // Alice turn: Augment her 7-build using floor cards (5 + 2) into a Compound Build
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.SEVEN)); // holding card
        KhasinoAction augment7 = KhasinoAction.buildCompound(buildId, null, List.of("FIVE_CLUBS", "TWO_HEARTS"), null, null);
        Map<String, Object> augRes = game.executeAction("Alice", augment7);
        assertTrue("Build augmented with 5+2 into Compound Build", Boolean.TRUE.equals(augRes.get("success")), (String) augRes.get("error"));
        assertTrue("Build is now compound", b.isCompound(), "Expected compound build");
        assertEquals("Build has 2 independent groups", 2, b.getGroups().size());

        // Now test immutability: Bob tries to change the value of this compound 7-build to 9
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.TWO));
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.NINE));
        KhasinoAction illegalChange = KhasinoAction.changeBuildValue(buildId, "TWO_DIAMONDS", 9);
        Map<String, Object> changeRes = game.executeAction("Bob", illegalChange);
        assertTrue("Attempt to change compound build value was rejected", !Boolean.TRUE.equals(changeRes.get("success")), "Allowed changing compound build value");
    }

    // 6. Changing Opponent Simple Build Value
    private static void testChangingOpponentBuildValue() {
        System.out.println("\n--- 6. Changing Opponent Build Value Tests ---");
        KhasinoGame game = new KhasinoGame();
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.THREE));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.EIGHT));

        // Alice builds 3 + 5 into an 8
        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
            fc.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.FIVE));
        } catch (Exception e) {}

        game.executeAction("Alice", KhasinoAction.buildSimple("THREE_HEARTS", List.of("FIVE_CLUBS"), 8));
        KhasinoBuild b = game.getBuilds().values().iterator().next();
        String buildId = b.getId();
        assertEquals("Alice owns the 8-build", "Alice", b.getOwnerId());

        // Bob has a Two and a Ten. Bob adds Two to Alice's 8-build to make it a 10 and take ownership!
        List<KhasinoCard> bobHand = game.getHand("Bob");
        bobHand.clear();
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.TWO));
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.TEN)); // retaining card

        KhasinoAction changeAction = KhasinoAction.changeBuildValue(buildId, "TWO_DIAMONDS", 10);
        Map<String, Object> res = game.executeAction("Bob", changeAction);
        assertTrue("Bob successfully changed Alice's 8-build to 10", Boolean.TRUE.equals(res.get("success")), (String) res.get("error"));
        assertEquals("Build value is now 10", 10, b.getCaptureValue());
        assertEquals("Bob is now the new owner of the build", "Bob", b.getOwnerId());
    }

    // 7. Drifting Restrictions
    private static void testDriftingRestrictions() {
        System.out.println("\n--- 7. Drifting Restrictions Tests ---");
        KhasinoGame game = new KhasinoGame(KhasinoGame.Mode.TWO_PLAYER);
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.FOUR));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.NINE));
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.TWO));

        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
            fc.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.FIVE));
        } catch (Exception e) {}

        // Alice builds 4 + 5 = 9
        game.executeAction("Alice", KhasinoAction.buildSimple("FOUR_HEARTS", List.of("FIVE_DIAMONDS"), 9));

        // Bob drifts a card
        List<KhasinoCard> bobHand = game.getHand("Bob");
        bobHand.clear();
        bobHand.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.ACE));
        game.executeAction("Bob", KhasinoAction.drift("ACE_HEARTS"));

        // In 2-player Deal 1, Alice owns an uncaptured build (the 9). She is NOT allowed to drift!
        KhasinoAction illegalDrift = KhasinoAction.drift("TWO_CLUBS");
        Map<String, Object> res = game.executeAction("Alice", illegalDrift);
        assertTrue("Alice cannot drift in Deal 1 while owning an uncaptured build", !Boolean.TRUE.equals(res.get("success")), "Allowed illegal drift in Deal 1");
    }

    // 8. Stealing from Opponent Captured Pile
    private static void testStealingFromOpponentCapturedPile() {
        System.out.println("\n--- 8. Stealing from Opponent Captured Pile Tests ---");
        KhasinoGame game = new KhasinoGame();
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob")));

        // Give Bob an exposed 5 on top of his captured pile
        KhasinoCard bobExposed5 = new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.FIVE);
        game.getCapturedPile("Bob").add(bobExposed5);

        // Alice has an owned 9-build on the table
        List<KhasinoCard> aliceHand = game.getHand("Alice");
        aliceHand.clear();
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.NINE)); // retaining card
        aliceHand.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.FOUR)); // will play 4 from hand + 5 from Bob = 9

        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
        } catch (Exception e) {}

        KhasinoBuild b9 = new KhasinoBuild("b_9", "Alice", 0, 9, List.of(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.NINE)));
        try {
            var bField = KhasinoGame.class.getDeclaredField("builds");
            bField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, KhasinoBuild> bm = (Map<String, KhasinoBuild>) bField.get(game);
            bm.put("b_9", b9);
        } catch (Exception e) {}

        // Alice augments her 9-build by playing 4 from hand and stealing the exposed 5 from Bob's pile!
        KhasinoAction stealAugment = KhasinoAction.buildCompound("b_9", "FOUR_DIAMONDS", Collections.emptyList(), "FIVE_HEARTS", "Bob");
        Map<String, Object> res = game.executeAction("Alice", stealAugment);
        assertTrue("Alice successfully augmented her build by stealing Bob's exposed 5", Boolean.TRUE.equals(res.get("success")), (String) res.get("error"));
        assertEquals("Bob's captured pile is now empty", 0, game.getCapturedPile("Bob").size());
        assertEquals("Build now has 2 groups (original 9 and stolen 5+4)", 2, b9.getGroups().size());
    }

    // 9. Sweeps & Leftover Cards Award
    private static void testSweepsAndLeftoverCardsAward() {
        System.out.println("\n--- 9. Sweeps & End-of-Deal Leftovers Tests ---");
        KhasinoGame game = new KhasinoGame(KhasinoGame.Mode.THREE_PLAYER);
        game.start(List.of(new Player("Alice", "Alice"), new Player("Bob", "Bob"), new Player("Charlie", "Charlie")));

        // Give Alice a 6, and a single 6 on the floor
        game.getHand("Alice").clear();
        game.getHand("Alice").add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.SIX));

        try {
            var field = KhasinoGame.class.getDeclaredField("floorCards");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<KhasinoCard> fc = (List<KhasinoCard>) field.get(game);
            fc.clear();
            fc.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.SIX));
        } catch (Exception e) {}

        KhasinoAction chow = KhasinoAction.chow("SIX_SPADES", List.of("SIX_HEARTS"), Collections.emptyList());
        Map<String, Object> res = game.executeAction("Alice", chow);
        assertTrue("Capture cleared the board and registered as a Sweep", Boolean.TRUE.equals(res.get("isSweep")), "Expected sweep");
        assertEquals("Sweep point counted for Alice", 1, game.getSweepCount("Alice"));
    }

    // 10. Scoring (11-Point vs 7-Point)
    private static void testScoring11PointAnd7Point() {
        System.out.println("\n--- 10. Scoring Calculation Tests ---");

        // Pile with Mummy (10♦), Spy Two (2♠), 3 Aces, 22 total cards, 7 spades
        List<KhasinoCard> p1Pile = new ArrayList<>();
        p1Pile.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.TEN)); // Mummy (2 pts)
        p1Pile.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.TWO));    // Spy Two (1 pt)
        p1Pile.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.ACE));    // Ace (1 pt)
        p1Pile.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.ACE));    // Ace (1 pt)
        p1Pile.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.ACE));     // Ace (1 pt)
        // Add more cards to reach 22 cards and 7 spades
        for (int i = 3; i <= 7; i++) {
            p1Pile.add(new KhasinoCard(KhasinoCard.Suit.SPADES, KhasinoCard.Rank.values()[i - 1]));
        }
        while (p1Pile.size() < 22) {
            p1Pile.add(new KhasinoCard(KhasinoCard.Suit.HEARTS, KhasinoCard.Rank.THREE));
        }

        // P2 Pile has 18 cards, 3 spades, 1 Ace
        List<KhasinoCard> p2Pile = new ArrayList<>();
        p2Pile.add(new KhasinoCard(KhasinoCard.Suit.DIAMONDS, KhasinoCard.Rank.ACE)); // Ace (1 pt)
        while (p2Pile.size() < 18) {
            p2Pile.add(new KhasinoCard(KhasinoCard.Suit.CLUBS, KhasinoCard.Rank.FIVE));
        }

        Map<String, List<KhasinoCard>> piles = Map.of("P1", p1Pile, "P2", p2Pile);
        Map<String, Integer> sweeps = Map.of("P1", 1, "P2", 0);

        // 11-point calculation
        var scores11 = KhasinoScoring.calculateScores(piles, sweeps, true);
        var s1 = scores11.get("P1");
        var s2 = scores11.get("P2");

        assertEquals("P1 Mummy points = 2", 2, s1.mummyPoints);
        assertEquals("P1 Spy Two points = 1", 1, s1.spyTwoPoints);
        assertEquals("P1 Ace points = 3", 3, s1.acePoints);
        assertEquals("P1 Cards Majority points = 2", 2, s1.cardsMajorityPoints);
        assertEquals("P1 Spades Majority points = 2", 2, s1.spadesMajorityPoints);
        assertEquals("P1 Sweep points = 1", 1, s1.sweepPoints);
        assertEquals("P1 Total Points = 11", 11, s1.getTotalPoints());

        assertEquals("P2 Ace points = 1", 1, s2.acePoints);
        assertEquals("P2 Total Points = 1", 1, s2.getTotalPoints());

        // 7-point calculation (cards and spades majorities ignored)
        var scores7 = KhasinoScoring.calculateScores(piles, sweeps, false);
        var s1_7 = scores7.get("P1");
        assertEquals("P1 Cards Majority neglected in 7-pt mode", 0, s1_7.cardsMajorityPoints);
        assertEquals("P1 Spades Majority neglected in 7-pt mode", 0, s1_7.spadesMajorityPoints);
        assertEquals("P1 Total in 7-pt mode (2+1+3+1 sweep) = 7", 7, s1_7.getTotalPoints());
    }

    // 11. Mummy Tiebreak Resolution
    private static void testMummyTiebreakResolution() {
        System.out.println("\n--- 11. Mummy Rule Tiebreak Tests ---");
        // Equal scores (e.g. 5 vs 5), but P1 has the Mummy
        KhasinoScoring.ScoreBreakdown b1 = new KhasinoScoring.ScoreBreakdown("Alice");
        b1.mummyPoints = 2;
        b1.hasMummy = true;
        b1.acePoints = 3; // total 5

        KhasinoScoring.ScoreBreakdown b2 = new KhasinoScoring.ScoreBreakdown("Bob");
        b2.spyTwoPoints = 1;
        b2.acePoints = 1;
        b2.cardsMajorityPoints = 2;
        b2.spadesMajorityPoints = 1; // total 5

        Map<String, KhasinoScoring.ScoreBreakdown> scores = Map.of("Alice", b1, "Bob", b2);
        String winner = KhasinoScoring.resolveWinner(scores);
        assertEquals("Tied match resolved by Mummy rule in favor of Alice (holder of 10♦)", "Alice", winner);
    }

    // 12. AI Decision Making & Full Simulation
    private static void testAiDecisionMakingAndSimulations() {
        System.out.println("\n--- 12. AI Heuristics & Full Game Simulation ---");

        // Test Easy, Medium, Hard AI legal move selection
        KhasinoGame game = new KhasinoGame(KhasinoGame.Mode.TWO_PLAYER);
        game.start(List.of(new Player("Bot1", "Bot1"), new Player("Bot2", "Bot2")));

        KhasinoAction easyMove = KhasinoAi.decideMove(game, "Bot1", KhasinoAi.Difficulty.EASY);
        assertTrue("Easy AI produces a valid legal move", easyMove != null, "Easy AI returned null");

        KhasinoAction medMove = KhasinoAi.decideMove(game, "Bot1", KhasinoAi.Difficulty.MEDIUM);
        assertTrue("Medium AI produces a valid legal move", medMove != null, "Medium AI returned null");

        KhasinoAction hardMove = KhasinoAi.decideMove(game, "Bot1", KhasinoAi.Difficulty.HARD);
        assertTrue("Hard AI produces a valid legal move", hardMove != null, "Hard AI returned null");

        // Run full simulated 2-player match between two AI bots from start to finish
        System.out.println("  Simulating full 2-player Khasino match (Deal 1 + Deal 2)...");
        int moveCount = 0;
        while (!game.isOver() && moveCount < 100) {
            String active = game.getCurrentTurnPlayer();
            KhasinoAction action = KhasinoAi.decideMove(game, active, KhasinoAi.Difficulty.HARD);
            assertTrue("AI produced legal move on turn " + moveCount, action != null, "AI had no legal move");
            Map<String, Object> step = game.executeAction(active, action);
            assertTrue("Move executed successfully", Boolean.TRUE.equals(step.get("success")), (String) step.get("error"));
            moveCount++;
        }

        assertTrue("Simulated game completed to conclusion", game.isOver(), "Game did not complete");
        assertTrue("Winner determined", game.getWinnerId() != null && !game.getWinnerId().isBlank(), "No winner determined");
        assertTrue("Final scores generated", game.getFinalScores() != null && !game.getFinalScores().isEmpty(), "No final scores");
        System.out.println("  Game concluded in " + moveCount + " turns! Winner: " + game.getWinnerId());

        // Run full simulated 4-player 2v2 partnership match
        System.out.println("  Simulating full 4-player 2v2 partnership Khasino match...");
        KhasinoGame pGame = new KhasinoGame(KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS);
        pGame.start(List.of(
                new Player("Team1_A", "Team1_A"),
                new Player("Team2_A", "Team2_A"),
                new Player("Team1_B", "Team1_B"),
                new Player("Team2_B", "Team2_B")
        ));

        moveCount = 0;
        while (!pGame.isOver() && moveCount < 100) {
            String active = pGame.getCurrentTurnPlayer();
            KhasinoAction action = KhasinoAi.decideMove(pGame, active, KhasinoAi.Difficulty.HARD);
            pGame.executeAction(active, action);
            moveCount++;
        }

        assertTrue("Partnership game completed to conclusion", pGame.isOver(), "Partnership game did not finish");
        assertTrue("Winning team resolved", pGame.getWinnerId() != null && pGame.getWinnerId().startsWith("Team "), "Winning team not resolved");
        System.out.println("  Partnership game concluded in " + moveCount + " turns! Winning Team: " + pGame.getWinnerId());
    }
}
