package com.robinhood.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GameResult {
    private String sessionId;
    private String email;
    private int rank;
    private int xpAwarded;
    private int tokensWon;
    private LocalDateTime timestamp;

    // Optional multi-player session result metadata
    private String boardSeed;
    private Map<String, Integer> playerRanks;
    private Map<String, Integer> playerXP;
    private Map<Integer, String> movesLog;
    private Set<String> playerEmails;

    public GameResult() {
        this.timestamp = LocalDateTime.now();
    }

    public GameResult(String sessionId, String email, int rank, int xpAwarded, int tokensWon) {
        this.sessionId = sessionId;
        this.email = email;
        this.rank = rank;
        this.xpAwarded = xpAwarded;
        this.tokensWon = tokensWon;
        this.timestamp = LocalDateTime.now();
    }

    public GameResult(String sessionId, String boardSeed, LocalDateTime timestamp,
                      Map<String, Integer> playerRanks, Map<String, Integer> playerXP,
                      Map<Integer, String> movesLog, Set<String> playerEmails) {
        this.sessionId = sessionId;
        this.boardSeed = boardSeed;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.playerRanks = playerRanks;
        this.playerXP = playerXP;
        this.movesLog = movesLog;
        this.playerEmails = playerEmails;
        if (playerEmails != null && !playerEmails.isEmpty()) {
            this.email = playerEmails.iterator().next();
        }
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public int getXpAwarded() { return xpAwarded; }
    public void setXpAwarded(int xpAwarded) { this.xpAwarded = xpAwarded; }

    public int getTokensWon() { return tokensWon; }
    public void setTokensWon(int tokensWon) { this.tokensWon = tokensWon; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getBoardSeed() { return boardSeed; }
    public void setBoardSeed(String boardSeed) { this.boardSeed = boardSeed; }

    public Map<String, Integer> getPlayerRanks() { return playerRanks; }
    public void setPlayerRanks(Map<String, Integer> playerRanks) { this.playerRanks = playerRanks; }

    public Map<String, Integer> getPlayerXP() { return playerXP; }
    public void setPlayerXP(Map<String, Integer> playerXP) { this.playerXP = playerXP; }

    public Map<Integer, String> getMovesLog() { return movesLog; }
    public void setMovesLog(Map<Integer, String> movesLog) { this.movesLog = movesLog; }

    public Set<String> getPlayerEmails() { return playerEmails; }
    public void setPlayerEmails(Set<String> playerEmails) { this.playerEmails = playerEmails; }
}
