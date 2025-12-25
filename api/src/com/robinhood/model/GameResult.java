package com.robinhood.model;

import java.time.LocalDateTime;

public class GameResult {
    private final String sessionId;
    private final String email;
    private final int rank;
    private final int xpAwarded;
    private final int tokensWon;
    private final LocalDateTime timestamp;

    public GameResult(String sessionId, String email, int rank, int xpAwarded, int tokensWon) {
        this.sessionId = sessionId;
        this.email = email;
        this.rank = rank;
        this.xpAwarded = xpAwarded;
        this.tokensWon = tokensWon;
        this.timestamp = LocalDateTime.now();
    }

    // Getters...
}
