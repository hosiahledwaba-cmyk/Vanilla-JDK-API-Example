package com.robinhood.game;

public class Player {
    private final String email;
    private final String token;
    private boolean isReady;

    public Player(String email, String token) {
        this.email = email;
        this.token = token;
        this.isReady = false;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }
}
