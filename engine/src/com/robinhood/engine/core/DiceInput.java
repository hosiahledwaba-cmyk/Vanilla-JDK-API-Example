package com.robinhood.engine.core;

/**
 * Represents input data for a physical-style dice roll.
 */
public class DiceInput {
    private final double intensity; // How strong the throw is
    private final double angle;     // Direction of swipe/throw
    private final long timestamp;   // Millis since epoch
    private final String playerId;

    public DiceInput(double intensity, double angle, long timestamp, String playerId) {
        this.intensity = clamp(intensity, 0.0, 1.0);
        this.angle = angle % 360;
        this.timestamp = timestamp;
        this.playerId = playerId;
    }

    public double getIntensity() { return intensity; }
    public double getAngle() { return angle; }
    public long getTimestamp() { return timestamp; }
    public String getPlayerId() { return playerId; }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
