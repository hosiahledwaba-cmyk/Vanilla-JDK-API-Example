package com.robinhood.game;

import com.robinhood.controller.WalletController;
import com.robinhood.db.GameResultLogger;
import com.robinhood.db.LedgerManager;
import com.robinhood.db.MatchSessionDao;
import com.robinhood.engine.GameType;
import com.robinhood.engine.crazyeight.CrazyEightAi;
import com.robinhood.engine.crazyeight.CrazyEightGame;
import com.robinhood.engine.khasino.KhasinoAi;
import com.robinhood.engine.khasino.KhasinoAction;
import com.robinhood.engine.khasino.KhasinoGame;
import com.robinhood.engine.ludo.LudoAi;
import com.robinhood.engine.ludo.LudoGame;
import com.robinhood.engine.snakesandladders.Board;
import com.robinhood.engine.uno.UnoAi;
import com.robinhood.engine.uno.UnoGame;
import com.robinhood.model.GameResult;
import com.robinhood.model.LedgerEntry;
import com.robinhood.realtime.RealTimeDispatcher;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GameSession {
    private final String sessionId;
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final int maxPlayers;
    private final int wagerAmount;
    private final GameType gameType;

    // Game Engines
    private final Board board = new Board();
    private final CrazyEightGame crazyEightGame = new CrazyEightGame();
    private final UnoGame unoGame = new UnoGame();
    private final KhasinoGame khasinoGame = new KhasinoGame();
    private final LudoGame ludoGame = new LudoGame();

    private final Map<String, Integer> playerRanks = new HashMap<>();
    private final Map<String, Integer> playerXP = new HashMap<>();
    private final Map<String, Integer> playerPositions = new ConcurrentHashMap<>();
    private final List<String> turnOrder = new ArrayList<>();
    private final AtomicInteger currentTurnIndex = new AtomicInteger(0);
    private final Map<Integer, String> movesLog = new LinkedHashMap<>();
    private final Map<String, String> playerTurnKeys = new HashMap<>();
    private final Map<String, Long> lastMoveTimestamps = new HashMap<>();
    private volatile boolean started = false;
    private final String boardSeed = UUID.randomUUID().toString();
    private final long TURN_TIMEOUT_MS = 30_000; // 30 seconds

    private int stateVersion = 1;
    private final AtomicInteger actionSeq = new AtomicInteger(1);
    private boolean autoFillBots = true;

    public void setAutoFillBots(boolean autoFill) {
        this.autoFillBots = autoFill;
    }

    public int getStateVersion() {
        return stateVersion;
    }

    public GameSession(String sessionId, int maxPlayers, int wagerAmount) {
        this(sessionId, maxPlayers, wagerAmount, GameType.SNAKES_AND_LADDERS);
    }

    public GameSession(String sessionId, int maxPlayers, int wagerAmount, GameType gameType) {
        this.sessionId = sessionId;
        this.maxPlayers = maxPlayers;
        this.wagerAmount = wagerAmount;
        this.gameType = (gameType != null) ? gameType : GameType.SNAKES_AND_LADDERS;
    }

    public GameType getGameType() {
        return gameType;
    }

    public Board getBoard() {
        return board;
    }

    public CrazyEightGame getCrazyEightGame() {
        return crazyEightGame;
    }

    public UnoGame getUnoGame() {
        return unoGame;
    }

    public KhasinoGame getKhasinoGame() {
        return khasinoGame;
    }

    public LudoGame getLudoGame() {
        return ludoGame;
    }

    private KhasinoAi.Difficulty khasinoDifficulty = KhasinoAi.Difficulty.MEDIUM;
    private KhasinoGame.Mode khasinoMode = null;

    public void setKhasinoDifficulty(KhasinoAi.Difficulty difficulty) {
        if (difficulty != null) this.khasinoDifficulty = difficulty;
    }

    public KhasinoAi.Difficulty getKhasinoDifficulty() {
        return khasinoDifficulty;
    }

    public void setKhasinoMode(KhasinoGame.Mode mode) {
        this.khasinoMode = mode;
        if (khasinoGame != null && mode != null) {
            khasinoGame.setMode(mode);
        }
    }

    public KhasinoGame.Mode getKhasinoMode() {
        return khasinoMode != null ? khasinoMode : (khasinoGame != null ? khasinoGame.getMode() : null);
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getWagerAmount() {
        return wagerAmount;
    }

    public boolean isStarted() {
        return started;
    }

    public String getBoardSeed() {
        return boardSeed;
    }

    public synchronized boolean addPlayer(Player player) {
        if (players.size() >= maxPlayers || players.containsKey(player.getEmail()) || started) {
            return false;
        }

        boolean deducted = WalletController.deduct(player.getEmail(), wagerAmount);
        if (!deducted) return false;

        players.put(player.getEmail(), player);
        playerPositions.put(player.getEmail(), 1);
        turnOrder.add(player.getEmail());

        LedgerManager.log(new LedgerEntry(
                player.getEmail(),
                LedgerEntry.Type.WAGER,
                wagerAmount,
                "Wager for game " + sessionId + " (" + gameType + ")"
        ));

        return true;
    }

    public synchronized boolean start() {
        if (players.size() < 1) return false;

        // Auto-fill remaining slot(s) with CPU bot(s) if allowed
        if (autoFillBots) {
            while (players.size() < maxPlayers) {
                String botName = (players.size() == 1) ? "RobinBot" : "CPU_" + players.size();
                WalletController.credit(botName, wagerAmount * 10);
                addPlayer(new Player(botName, "bot-token"));
            }
        }

        started = true;
        currentTurnIndex.set(0);

        List<com.robinhood.engine.core.Player> enginePlayers = new ArrayList<>();
        for (String pId : turnOrder) {
            enginePlayers.add(new com.robinhood.engine.core.Player(pId, pId));
        }

        if (gameType == GameType.CRAZY_EIGHT) {
            crazyEightGame.start(enginePlayers);
            RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"gameType\":\"CRAZY_EIGHT\",\"players\":" + playerListJson() + ",\"state\":" + crazyEightGame.toJson() + "}");
        } else if (gameType == GameType.UNO) {
            unoGame.start(enginePlayers);
            RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"gameType\":\"UNO\",\"players\":" + playerListJson() + ",\"state\":" + unoGame.toJson() + "}");
        } else if (gameType == GameType.KHASINO) {
            if (khasinoMode != null) {
                khasinoGame.setMode(khasinoMode);
            }
            khasinoGame.start(enginePlayers);
            RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"gameType\":\"KHASINO\",\"players\":" + playerListJson() + ",\"state\":" + khasinoGame.toJson() + "}");
        } else if (gameType == GameType.LUDO) {
            ludoGame.start(enginePlayers);
            RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"gameType\":\"LUDO\",\"players\":" + playerListJson() + ",\"state\":" + ludoGame.toJson() + "}");
        } else {
            RealTimeDispatcher.publish(sessionId, "{\"type\":\"start\",\"gameType\":\"SNAKES_AND_LADDERS\",\"players\":" + playerListJson() + "}");
        }

        // Persist match in PostgreSQL
        try {
            MatchSessionDao.createMatch(sessionId, null, gameType.name(), (khasinoMode != null ? khasinoMode.name() : null), maxPlayers, wagerAmount, getCurrentTurnPlayer());
            MatchSessionDao.saveGameState(sessionId, stateVersion, getSessionStateJson());
            MatchSessionDao.logAction(sessionId, "SYSTEM", "START", "{\"gameType\":\"" + gameType.name() + "\"}", actionSeq.getAndIncrement(), stateVersion);
        } catch (Exception e) {
            System.err.println("[GameSession] Error recording start in DB: " + e.getMessage());
        }

        triggerBotMoveIfNeeded();
        return true;
    }

    public String getSessionStateJson() {
        if (gameType == GameType.KHASINO && khasinoGame != null) {
            return khasinoGame.toJson();
        } else if (gameType == GameType.LUDO && ludoGame != null) {
            return ludoGame.toJson();
        } else if (gameType == GameType.UNO && unoGame != null) {
            return unoGame.toJson();
        } else if (gameType == GameType.CRAZY_EIGHT && crazyEightGame != null) {
            return crazyEightGame.toJson();
        } else if (board != null) {
            return board.toJson();
        }
        return "{}";
    }

    public static boolean isBot(String email) {
        if (email == null) return false;
        return email.startsWith("RobinBot") || email.startsWith("CPU") || email.contains("Bot");
    }

    public void triggerBotMoveIfNeeded() {
        if (!started) return;
        String current = getCurrentTurnPlayer();
        if (current == null || !isBot(current)) return;

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1100);
                if (!started) return;
                String active = getCurrentTurnPlayer();
                if (active == null || !isBot(active)) return;

                if (gameType == GameType.SNAKES_AND_LADDERS) {
                    int botRoll = (int) (Math.random() * 6) + 1;
                    submitMove(active, String.valueOf(botRoll));
                } else if (gameType == GameType.CRAZY_EIGHT) {
                    CrazyEightAi.MoveDecision decision = CrazyEightAi.decideMove(crazyEightGame, active);
                    if ("play".equalsIgnoreCase(decision.action)) {
                        submitCrazyEightMove(active, decision.cardId, decision.declaredSuit);
                    } else if ("draw".equalsIgnoreCase(decision.action)) {
                        Map<String, Object> drawRes = submitCrazyEightDraw(active);
                        if (Boolean.TRUE.equals(drawRes.get("canPlay"))) {
                            Thread.sleep(600);
                            CrazyEightAi.MoveDecision followUp = CrazyEightAi.decideMove(crazyEightGame, active);
                            if ("play".equalsIgnoreCase(followUp.action)) {
                                submitCrazyEightMove(active, followUp.cardId, followUp.declaredSuit);
                            } else {
                                submitCrazyEightPass(active);
                            }
                        } else {
                            submitCrazyEightPass(active);
                        }
                    } else {
                        submitCrazyEightPass(active);
                    }
                } else if (gameType == GameType.UNO) {
                    UnoAi.MoveDecision decision = UnoAi.decideMove(unoGame, active);
                    if ("play".equalsIgnoreCase(decision.action)) {
                        submitUnoMove(active, decision.cardId, decision.declaredColor);
                    } else if ("draw".equalsIgnoreCase(decision.action)) {
                        Map<String, Object> drawRes = submitUnoDraw(active);
                        if (Boolean.TRUE.equals(drawRes.get("canPlay"))) {
                            Thread.sleep(600);
                            UnoAi.MoveDecision followUp = UnoAi.decideMove(unoGame, active);
                            if ("play".equalsIgnoreCase(followUp.action)) {
                                submitUnoMove(active, followUp.cardId, followUp.declaredColor);
                            } else {
                                submitUnoPass(active);
                            }
                        } else {
                            submitUnoPass(active);
                        }
                    } else {
                        submitUnoPass(active);
                    }
                } else if (gameType == GameType.KHASINO) {
                    KhasinoAction decision = KhasinoAi.decideMove(khasinoGame, active, khasinoDifficulty);
                    if (decision != null) {
                        submitKhasinoMove(active, decision);
                    }
                } else if (gameType == GameType.LUDO) {
                    if (ludoGame.getPhase() == LudoGame.TurnPhase.WAITING_FOR_ROLL) {
                        Map<String, Object> rollRes = submitLudoRoll(active);
                        if (ludoGame.getPhase() == LudoGame.TurnPhase.WAITING_FOR_MOVE) {
                            Thread.sleep(600);
                            int chosen = LudoAi.decideTokenMove(ludoGame, active);
                            if (chosen >= 0) {
                                submitLudoMove(active, chosen);
                            }
                        }
                    } else if (ludoGame.getPhase() == LudoGame.TurnPhase.WAITING_FOR_MOVE) {
                        int chosen = LudoAi.decideTokenMove(ludoGame, active);
                        if (chosen >= 0) {
                            submitLudoMove(active, chosen);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public synchronized boolean isPlayerTurn(String email) {
        String current = getCurrentTurnPlayer();
        return started && current != null && current.equals(email);
    }

    public synchronized String getCurrentTurnPlayer() {
        if (!started) return null;
        if (gameType == GameType.CRAZY_EIGHT) {
            return crazyEightGame.getCurrentTurnPlayer();
        } else if (gameType == GameType.UNO) {
            return unoGame.getCurrentTurnPlayer();
        } else if (gameType == GameType.KHASINO) {
            return khasinoGame.getCurrentTurnPlayer();
        } else if (gameType == GameType.LUDO) {
            return ludoGame.getCurrentTurnPlayer();
        } else {
            return (!turnOrder.isEmpty()) ? turnOrder.get(currentTurnIndex.get() % turnOrder.size()) : null;
        }
    }

    public synchronized boolean validateTurnKey(String email, String turnKey) {
        String expected = playerTurnKeys.get(email);
        return expected == null || expected.equals(turnKey);
    }

    // --- Snakes and Ladders Move ---
    public synchronized boolean submitMove(String email, String moveData, String turnKey) {
        if (!started || !isPlayerTurn(email) || !validateTurnKey(email, turnKey)) return false;

        int turnNum = currentTurnIndex.get();
        movesLog.put(turnNum, moveData);
        playerTurnKeys.remove(email);
        updateLastMove(email);

        int roll = 1;
        try {
            roll = Integer.parseInt(moveData.replaceAll("[^0-9]", ""));
            if (roll < 1 || roll > 6) roll = (int) (Math.random() * 6) + 1;
        } catch (Exception ignored) {
            roll = (int) (Math.random() * 6) + 1;
        }
        int currentPos = playerPositions.getOrDefault(email, 1);
        int stepPos = currentPos + roll;
        if (stepPos > 100) stepPos = currentPos;
        int newPos = board.resolveTile(stepPos);
        playerPositions.put(email, newPos);

        boolean won = (newPos >= 100);
        String nextPlayer = null;
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            ranks.put(email, 1);
            int r = 2;
            for (String p : turnOrder) {
                if (!p.equals(email)) ranks.put(p, r++);
            }
            endGame(ranks);
        } else {
            int nextTurn = (turnNum + 1) % turnOrder.size();
            currentTurnIndex.set(nextTurn);
            nextPlayer = getCurrentTurnPlayer();
        }

        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, (nextPlayer != null ? nextPlayer : ""), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "ROLL", "{\"roll\":" + roll + ",\"from\":" + currentPos + ",\"to\":" + newPos + "}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, board.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"move\","
                + "\"turn\":" + turnNum + ","
                + "\"player\":\"" + email + "\","
                + "\"roll\":" + roll + ","
                + "\"from\":" + currentPos + ","
                + "\"to\":" + newPos + ","
                + "\"won\":" + won + ","
                + "\"move\":\"" + moveData + "\","
                + "\"nextPlayer\":\"" + (nextPlayer != null ? nextPlayer : "") + "\""
                + "}"
        );

        if (!won) triggerBotMoveIfNeeded();
        return true;
    }

    public synchronized boolean submitMove(String email, String moveData) {
        String key = playerTurnKeys.get(email);
        return submitMove(email, moveData, key != null ? key : "");
    }

    // --- Crazy Eights Moves ---
    public synchronized Map<String, Object> submitCrazyEightMove(String email, String cardId, String suit) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = crazyEightGame.playCard(email, cardId, suit);
        boolean won = Boolean.TRUE.equals(res.get("won"));
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            ranks.put(email, 1);
            int r = 2;
            for (String p : turnOrder) {
                if (!p.equals(email)) ranks.put(p, r++);
            }
            endGame(ranks);
        }

        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, crazyEightGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "PLAY", "{\"cardId\":\"" + cardId + "\",\"suit\":\"" + suit + "\"}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, crazyEightGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"CRAZY_EIGHT\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"play\","
                + "\"cardId\":\"" + cardId + "\","
                + "\"won\":" + won + ","
                + "\"state\":" + crazyEightGame.toJson()
                + "}"
        );

        if (!won) triggerBotMoveIfNeeded();
        return res;
    }

    public synchronized Map<String, Object> submitCrazyEightDraw(String email) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = crazyEightGame.drawCard(email);
        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, crazyEightGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "DRAW", "{}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, crazyEightGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"CRAZY_EIGHT\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"draw\","
                + "\"won\":false,"
                + "\"state\":" + crazyEightGame.toJson()
                + "}"
        );
        return res;
    }

    public synchronized Map<String, Object> submitCrazyEightPass(String email) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = crazyEightGame.passTurn(email);
        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, crazyEightGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "PASS", "{}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, crazyEightGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"CRAZY_EIGHT\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"pass\","
                + "\"won\":false,"
                + "\"state\":" + crazyEightGame.toJson()
                + "}"
        );
        triggerBotMoveIfNeeded();
        return res;
    }

    // --- Uno Moves ---
    public synchronized Map<String, Object> submitUnoMove(String email, String cardId, String color) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = unoGame.playCard(email, cardId, color);
        boolean won = Boolean.TRUE.equals(res.get("won"));
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            ranks.put(email, 1);
            int r = 2;
            for (String p : turnOrder) {
                if (!p.equals(email)) ranks.put(p, r++);
            }
            endGame(ranks);
        }

        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, unoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "PLAY", "{\"cardId\":\"" + cardId + "\",\"color\":\"" + color + "\"}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, unoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"UNO\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"play\","
                + "\"cardId\":\"" + cardId + "\","
                + "\"won\":" + won + ","
                + "\"state\":" + unoGame.toJson()
                + "}"
        );

        if (!won) triggerBotMoveIfNeeded();
        return res;
    }

    public synchronized Map<String, Object> submitUnoDraw(String email) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = unoGame.drawCard(email);
        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, unoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "DRAW", "{}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, unoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"UNO\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"draw\","
                + "\"won\":false,"
                + "\"state\":" + unoGame.toJson()
                + "}"
        );
        return res;
    }

    public synchronized Map<String, Object> submitUnoPass(String email) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = unoGame.passTurn(email);
        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, unoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "PASS", "{}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, unoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"card_move\","
                + "\"game\":\"UNO\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"pass\","
                + "\"won\":false,"
                + "\"state\":" + unoGame.toJson()
                + "}"
        );
        triggerBotMoveIfNeeded();
        return res;
    }

    // --- Khasino Moves ---
    public synchronized Map<String, Object> submitKhasinoMove(String email, KhasinoAction action) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = khasinoGame.executeAction(email, action);
        boolean won = khasinoGame.isOver();
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            String winner = khasinoGame.getWinnerId();
            if (winner != null && winner.startsWith("Team ")) {
                int winningTeam = winner.equals("Team 1") ? 1 : 2;
                for (String p : turnOrder) {
                    if (khasinoGame.getPlayerTeam(p) == winningTeam) {
                        ranks.put(p, 1);
                    } else {
                        ranks.put(p, 2);
                    }
                }
            } else {
                ranks.put(winner, 1);
                int r = 2;
                for (String p : turnOrder) {
                    if (!p.equals(winner)) ranks.put(p, r++);
                }
            }
            endGame(ranks);
        }

        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, khasinoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, action.getType().name(), action.toJson(), actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, khasinoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"khasino_move\","
                + "\"game\":\"KHASINO\","
                + "\"player\":\"" + email + "\","
                + "\"action\":\"" + action.getType().name() + "\","
                + "\"won\":" + won + ","
                + "\"state\":" + khasinoGame.toJson(email)
                + "}"
        );

        if (!won) triggerBotMoveIfNeeded();
        return res;
    }

    // --- Ludo Moves ---
    public synchronized Map<String, Object> submitLudoRoll(String email) {
        return submitLudoRoll(email, -1);
    }

    public synchronized Map<String, Object> submitLudoRoll(String email, int forcedRoll) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = ludoGame.rollDice(email, forcedRoll);
        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, ludoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "ROLL", "{\"roll\":" + res.get("roll") + "}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, ludoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"ludo_roll\","
                + "\"game\":\"LUDO\","
                + "\"player\":\"" + email + "\","
                + "\"roll\":" + res.get("roll") + ","
                + "\"noLegalMoves\":" + Boolean.TRUE.equals(res.get("noLegalMoves")) + ","
                + "\"penalty\":" + Boolean.TRUE.equals(res.get("consecutiveSixPenalty")) + ","
                + "\"state\":" + ludoGame.toJson()
                + "}"
        );

        if (Boolean.TRUE.equals(res.get("noLegalMoves")) || Boolean.TRUE.equals(res.get("consecutiveSixPenalty"))) {
            triggerBotMoveIfNeeded();
        }
        return res;
    }

    public synchronized Map<String, Object> submitLudoMove(String email, int tokenId) {
        if (!started || !isPlayerTurn(email)) {
            return Map.of("success", false, "error", "Not your turn");
        }
        Map<String, Object> res = ludoGame.moveToken(email, tokenId);
        boolean won = Boolean.TRUE.equals(res.get("gameOver"));
        if (won) {
            Map<String, Integer> ranks = new HashMap<>();
            ranks.put(email, 1);
            int r = 2;
            for (String p : turnOrder) {
                if (!p.equals(email)) ranks.put(p, r++);
            }
            endGame(ranks);
        }

        stateVersion++;
        try {
            MatchSessionDao.updateTurn(sessionId, ludoGame.getCurrentTurnPlayer(), stateVersion);
            MatchSessionDao.logAction(sessionId, email, "MOVE", "{\"tokenId\":" + tokenId + "}", actionSeq.getAndIncrement(), stateVersion);
            MatchSessionDao.saveGameState(sessionId, stateVersion, ludoGame.toJson());
        } catch (Exception ignored) {}

        RealTimeDispatcher.publish(sessionId,
                "{"
                + "\"type\":\"ludo_move\","
                + "\"game\":\"LUDO\","
                + "\"player\":\"" + email + "\","
                + "\"tokenId\":" + tokenId + ","
                + "\"bonusRoll\":" + Boolean.TRUE.equals(res.get("bonusRoll")) + ","
                + "\"won\":" + won + ","
                + "\"state\":" + ludoGame.toJson()
                + "}"
        );

        if (!won) triggerBotMoveIfNeeded();
        return res;
    }

    public synchronized boolean removePlayer(String email) {
        if (email == null) return false;
        players.remove(email);
        playerPositions.remove(email);
        turnOrder.remove(email);
        playerTurnKeys.remove(email);
        lastMoveTimestamps.remove(email);
        return true;
    }

    public Map<String, Integer> getPlayerPositions() {
        return Collections.unmodifiableMap(playerPositions);
    }

    public void setPlayerPosition(String email, int pos) {
        playerPositions.put(email, pos);
    }

    public synchronized void updateLastMove(String email) {
        if (isPlayerTurn(email)) {
            lastMoveTimestamps.put(email, System.currentTimeMillis());
        }
    }

    public synchronized boolean hasTimedOut(String email) {
        Long last = lastMoveTimestamps.get(email);
        return last != null && System.currentTimeMillis() - last > TURN_TIMEOUT_MS;
    }

    public synchronized boolean currentPlayerTimedOut() {
        String current = getCurrentTurnPlayer();
        if (current == null) return false;
        Long last = lastMoveTimestamps.get(current);
        return last != null && System.currentTimeMillis() - last > TURN_TIMEOUT_MS;
    }

    public synchronized boolean forfeit(String playerEmail) {
        if (!started || !players.containsKey(playerEmail)) return false;
        if (!playerRanks.isEmpty()) return false; // Already finished

        System.out.println("[GameSession] Player " + playerEmail + " forfeited in session " + sessionId);

        String winner = null;
        Integer winnerTeam = null;

        if (gameType == GameType.KHASINO && khasinoMode == KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS) {
            int forfeiterTeam = khasinoGame.getPlayerTeam(playerEmail);
            winnerTeam = (forfeiterTeam == 1) ? 2 : 1;
            Map<String, Integer> ranks = new HashMap<>();
            for (String p : turnOrder) {
                if (khasinoGame.getPlayerTeam(p) == winnerTeam) {
                    ranks.put(p, 1);
                    if (winner == null) winner = p;
                } else {
                    ranks.put(p, 2);
                }
            }
            this.playerRanks.putAll(ranks);
        } else {
            List<String> opponents = new ArrayList<>();
            for (String p : turnOrder) {
                if (!p.equalsIgnoreCase(playerEmail)) {
                    opponents.add(p);
                }
            }
            winner = !opponents.isEmpty() ? opponents.get(0) : "RobinBot";
            Map<String, Integer> ranks = new HashMap<>();
            int r = 1;
            for (String opp : opponents) {
                ranks.put(opp, r++);
            }
            ranks.put(playerEmail, turnOrder.size());
            this.playerRanks.putAll(ranks);
        }

        assignXPandPayouts();
        this.started = false;
        persistGameResult();

        // Persist forfeit in PostgreSQL
        try {
            stateVersion++;
            MatchSessionDao.finalizeMatch(sessionId, "FORFEIT", winner, winnerTeam, playerEmail, "Player forfeited", "FORFEIT");
            MatchSessionDao.logAction(sessionId, playerEmail, "FORFEIT", "{\"reason\":\"User left match\"}", actionSeq.getAndIncrement(), stateVersion);
            for (Map.Entry<String, Integer> e : playerRanks.entrySet()) {
                boolean isWinner = (e.getValue() == 1);
                boolean isForfeit = e.getKey().equalsIgnoreCase(playerEmail);
                int xp = playerXP.getOrDefault(e.getKey(), 0);
                MatchSessionDao.recordResult(sessionId, e.getKey(), gameType.name(), isWinner, isForfeit, xp, (isWinner ? "WON" : "FORFEIT"), "{}");
            }
        } catch (Exception ex) {
            System.err.println("[GameSession] Error recording forfeit in DB: " + ex.getMessage());
        }

        String forfeitMsg = "{\"type\":\"forfeit\",\"player\":\"" + playerEmail + "\",\"winner\":\"" + (winner != null ? winner : "") + "\",\"sessionId\":\"" + sessionId + "\",\"results\":" + resultsJson() + "}";
        RealTimeDispatcher.publish(sessionId, forfeitMsg);
        RealTimeDispatcher.publish(sessionId, "{\"type\":\"end\",\"results\":" + resultsJson() + "}");
        return true;
    }

    public synchronized void endGame(Map<String, Integer> finalRanks) {
        this.playerRanks.putAll(finalRanks);
        assignXPandPayouts();
        this.started = false;
        persistGameResult();

        // Persist completed match in PostgreSQL
        try {
            String winner = null;
            Integer winnerTeam = null;
            for (Map.Entry<String, Integer> e : finalRanks.entrySet()) {
                if (e.getValue() == 1) {
                    winner = e.getKey();
                    if (gameType == GameType.KHASINO && khasinoMode == KhasinoGame.Mode.FOUR_PLAYER_PARTNERSHIPS) {
                        winnerTeam = khasinoGame.getPlayerTeam(winner);
                    }
                    break;
                }
            }
            stateVersion++;
            MatchSessionDao.finalizeMatch(sessionId, "COMPLETED", winner, winnerTeam, null, null, "VICTORY");
            MatchSessionDao.logAction(sessionId, (winner != null ? winner : "SYSTEM"), "VICTORY", "{\"winner\":\"" + winner + "\"}", actionSeq.getAndIncrement(), stateVersion);
            for (Map.Entry<String, Integer> e : finalRanks.entrySet()) {
                boolean isWinner = (e.getValue() == 1);
                int xp = playerXP.getOrDefault(e.getKey(), 0);
                MatchSessionDao.recordResult(sessionId, e.getKey(), gameType.name(), isWinner, false, xp, (isWinner ? "WON" : "LOST"), "{}");
            }
        } catch (Exception ex) {
            System.err.println("[GameSession] Error recording game result in DB: " + ex.getMessage());
        }

        RealTimeDispatcher.publish(sessionId,
                "{\"type\":\"end\",\"results\":" + resultsJson() + "}");
    }

    private void assignXPandPayouts() {
        int playersCount = players.size();
        int[] xpDistribution;
        int[] payoutDistribution;
        int houseRake;

        if (playersCount == 1) {
            houseRake = 0;
            xpDistribution = new int[]{20};
            payoutDistribution = new int[]{wagerAmount};
        } else if (playersCount == 2) {
            houseRake = Math.max(5, wagerAmount / 10);
            xpDistribution = new int[]{25, 10};
            payoutDistribution = new int[]{(wagerAmount * 2) - houseRake, 0};
        } else if (playersCount == 3) {
            houseRake = 35;
            xpDistribution = new int[]{30, 15, 5};
            payoutDistribution = new int[]{100, 15, 0};
        } else if (playersCount == 4) {
            houseRake = 50;
            xpDistribution = new int[]{40, 20, 10, 5};
            payoutDistribution = new int[]{115, 20, 5, 0};
        } else {
            return;
        }

        playerRanks.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEachOrdered(entry -> {
                    String email = entry.getKey();
                    int rank = entry.getValue();
                    int idx = rank - 1;

                    int xp = (idx < xpDistribution.length) ? xpDistribution[idx] : 0;
                    int payout = (idx < payoutDistribution.length) ? payoutDistribution[idx] : 0;

                    playerXP.put(email, xp);
                    WalletController.credit(email, payout);

                    LedgerManager.log(new LedgerEntry(
                            email,
                            LedgerEntry.Type.WIN,
                            payout,
                            "Winnings for game " + sessionId + " (" + gameType + "), Rank: " + rank
                    ));
                });

        LedgerManager.log(new LedgerEntry(
                "HOUSE",
                LedgerEntry.Type.RAKE,
                houseRake,
                "House rake for game " + sessionId
        ));
    }

    private void persistGameResult() {
        GameResult result = new GameResult(
                sessionId,
                boardSeed,
                LocalDateTime.now(),
                new HashMap<>(playerRanks),
                new HashMap<>(playerXP),
                new LinkedHashMap<>(movesLog),
                players.keySet()
        );
        GameResultLogger.save(result);
    }

    public Collection<Player> getPlayers() {
        return players.values();
    }

    public Map<String, Integer> getPlayerXP() {
        return Collections.unmodifiableMap(playerXP);
    }

    public Map<String, Integer> getFinalRanks() {
        return Collections.unmodifiableMap(playerRanks);
    }

    public Map<Integer, String> getMovesLog() {
        return Collections.unmodifiableMap(movesLog);
    }

    private String playerListJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < turnOrder.size(); i++) {
            sb.append("\"").append(turnOrder.get(i)).append("\"");
            if (i < turnOrder.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String resultsJson() {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (String email : playerRanks.keySet()) {
            sb.append("\"").append(email).append("\":{")
              .append("\"rank\":").append(playerRanks.get(email)).append(",")
              .append("\"xp\":").append(playerXP.getOrDefault(email, 0))
              .append("}");
            if (++i < playerRanks.size()) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    public String getCurrentPlayer() {
        return getCurrentTurnPlayer();
    }

    public boolean isGameOver() {
        return !playerRanks.isEmpty() || !started;
    }

    public String getWinner() {
        for (Map.Entry<String, Integer> e : playerRanks.entrySet()) {
            if (e.getValue() == 1) return e.getKey();
        }
        return null;
    }

    public String getPlayersJson() {
        return playerListJson();
    }
}
