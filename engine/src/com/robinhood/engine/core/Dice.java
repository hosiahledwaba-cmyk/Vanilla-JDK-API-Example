package com.robinhood.engine.core;

import java.security.SecureRandom;

/**
 * Simulates a physical die using swipe inputs and pseudo-physics.
 */
public class Dice {
    private static final SecureRandom random = new SecureRandom();

    /**
     * Rolls a die based on realistic user input (like swipes).
     */
    public static int roll(DiceInput input) {
        // Use intensity to calculate "energy"
        double energy = input.getIntensity() * 100;

        // Use angle to introduce rotational skew
        double skew = Math.sin(Math.toRadians(input.getAngle())) * 0.5;

        // Simulate number of "bounces" — minimum 1, maximum 10
        int rolls = (int)(energy / 10) + random.nextInt(3);

        // Start from a seeded initial value
        long seed = input.getTimestamp() ^ input.getPlayerId().hashCode();
        SecureRandom seededRandom = new SecureRandom(longToBytes(seed));
        int value = 1 + seededRandom.nextInt(6);

        // Apply physics-like bounces (simulated)
        for (int i = 0; i < rolls; i++) {
            value += (random.nextDouble() < 0.5) ? 1 : -1;
        }

        // Apply skew
        value += (skew > 0.4) ? 1 : (skew < -0.4) ? -1 : 0;

        // Clamp to 1–6
        value = Math.max(1, Math.min(6, value));
        return value;
    }

    private static byte[] longToBytes(long val) {
        byte[] b = new byte[8];
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte)(val & 0xffL);
            val >>= 8;
        }
        return b;
    }
}
