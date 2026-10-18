package com.robinhood.engine.ludo;

/**
 * Standard Ludo player colors with board start offsets and visual metadata.
 */
public enum LudoColor {
    RED(0, "#EF4444", "Red", "🔴"),
    GREEN(13, "#10B981", "Green", "🟢"),
    YELLOW(26, "#F59E0B", "Yellow", "🟡"),
    BLUE(39, "#3B82F6", "Blue", "🔵");

    private final int startTrackIndex;
    private final String hexColor;
    private final String displayName;
    private final String emoji;

    LudoColor(int startTrackIndex, String hexColor, String displayName, String emoji) {
        this.startTrackIndex = startTrackIndex;
        this.hexColor = hexColor;
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public int getStartTrackIndex() {
        return startTrackIndex;
    }

    public String getHexColor() {
        return hexColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }
}
