package com.robinhood.engine.ludo;

/**
 * Represents a single Ludo token belonging to a player color.
 */
public class LudoToken {

    public enum State {
        YARD,
        TRACK,
        HOME_COLUMN,
        FINISHED
    }

    private final int id;
    private final LudoColor color;
    private final String ownerId;
    private int step; // -1 = Yard, 0..50 = Track, 51..55 = Home Column, 56 = Finished

    public LudoToken(int id, LudoColor color, String ownerId) {
        this.id = id;
        this.color = color;
        this.ownerId = ownerId;
        this.step = -1;
    }

    public int getId() {
        return id;
    }

    public LudoColor getColor() {
        return color;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public State getState() {
        if (step < 0) return State.YARD;
        if (step <= 50) return State.TRACK;
        if (step < LudoBoard.FINISH_STEP) return State.HOME_COLUMN;
        return State.FINISHED;
    }

    public boolean isInYard() {
        return step < 0;
    }

    public boolean isOnTrack() {
        return step >= 0 && step <= 50;
    }

    public boolean isInHomeColumn() {
        return step >= 51 && step < LudoBoard.FINISH_STEP;
    }

    public boolean isFinished() {
        return step >= LudoBoard.FINISH_STEP;
    }

    /**
     * Absolute track index (0-51) on the shared track, or -1 if not currently on track.
     */
    public int getTrackSquare() {
        if (!isOnTrack()) return -1;
        return (color.getStartTrackIndex() + step) % LudoBoard.TRACK_LENGTH;
    }

    /**
     * Checks if this token is on a safe square on the track.
     */
    public boolean isOnSafeSquare() {
        int trackSq = getTrackSquare();
        return trackSq >= 0 && LudoBoard.isSafeTrackSquare(trackSq);
    }

    public int[] getGridCoordinate() {
        return LudoBoard.getGridCoordinate(color, step, id);
    }

    public String toJson() {
        int[] coords = getGridCoordinate();
        return "{" +
                "\"id\":" + id + "," +
                "\"color\":\"" + color.name() + "\"," +
                "\"ownerId\":\"" + ownerId + "\"," +
                "\"step\":" + step + "," +
                "\"trackSquare\":" + getTrackSquare() + "," +
                "\"state\":\"" + getState().name() + "\"," +
                "\"isSafe\":" + isOnSafeSquare() + "," +
                "\"row\":" + coords[0] + "," +
                "\"col\":" + coords[1] +
                "}";
    }
}
