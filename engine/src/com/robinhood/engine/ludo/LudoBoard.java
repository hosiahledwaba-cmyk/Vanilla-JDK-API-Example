package com.robinhood.engine.ludo;

import java.util.*;

/**
 * Authoritative geometric and logical representation of a standard 15x15 Ludo board.
 * Contains track indexing (0-51), safe square definitions, and (row, col) grid coordinates.
 */
public class LudoBoard {

    public static final int TRACK_LENGTH = 52;
    public static final int HOME_COLUMN_LENGTH = 5;
    public static final int FINISH_STEP = 56; // 0..50 (track, 51 squares) + 51..55 (home col, 5 squares) + 56 (finish)

    // 8 standard safe squares: 4 starting squares (0, 13, 26, 39) + 4 star squares (8, 21, 34, 47)
    public static final Set<Integer> SAFE_TRACK_SQUARES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(0, 8, 13, 21, 26, 34, 39, 47))
    );

    // Coordinate mapping for all 52 track squares: [row, col] on a 15x15 grid (0-14)
    public static final int[][] TRACK_COORDINATES = new int[][] {
        {6, 1},  // 0  Red Start (Safe)
        {6, 2},  // 1
        {6, 3},  // 2
        {6, 4},  // 3
        {6, 5},  // 4
        {5, 6},  // 5
        {4, 6},  // 6
        {3, 6},  // 7
        {2, 6},  // 8  Star Safe
        {1, 6},  // 9
        {0, 6},  // 10
        {0, 7},  // 11
        {0, 8},  // 12
        {1, 8},  // 13 Green Start (Safe)
        {2, 8},  // 14
        {3, 8},  // 15
        {4, 8},  // 16
        {5, 8},  // 17
        {6, 9},  // 18
        {6, 10}, // 19
        {6, 11}, // 20
        {6, 12}, // 21 Star Safe
        {6, 13}, // 22
        {6, 14}, // 23
        {7, 14}, // 24
        {8, 14}, // 25
        {8, 13}, // 26 Yellow Start (Safe)
        {8, 12}, // 27
        {8, 11}, // 28
        {8, 10}, // 29
        {8, 9},  // 30
        {9, 8},  // 31
        {10, 8}, // 32
        {11, 8}, // 33
        {12, 8}, // 34 Star Safe
        {13, 8}, // 35
        {14, 8}, // 36
        {14, 7}, // 37
        {14, 6}, // 38
        {13, 6}, // 39 Blue Start (Safe)
        {12, 6}, // 40
        {11, 6}, // 41
        {10, 6}, // 42
        {9, 6},  // 43
        {8, 5},  // 44
        {8, 4},  // 45
        {8, 3},  // 46
        {8, 2},  // 47 Star Safe
        {8, 1},  // 48
        {8, 0},  // 49
        {7, 0},  // 50
        {6, 0}   // 51
    };

    // Home column coordinates for each color (5 squares per color: step 51..55)
    public static final Map<LudoColor, int[][]> HOME_COLUMNS = Map.of(
        LudoColor.RED, new int[][] { {7, 1}, {7, 2}, {7, 3}, {7, 4}, {7, 5} },
        LudoColor.GREEN, new int[][] { {1, 7}, {2, 7}, {3, 7}, {4, 7}, {5, 7} },
        LudoColor.YELLOW, new int[][] { {7, 13}, {7, 12}, {7, 11}, {7, 10}, {7, 9} },
        LudoColor.BLUE, new int[][] { {13, 7}, {12, 7}, {11, 7}, {10, 7}, {9, 7} }
    );

    // Finish / Home Center coordinates for each color: step 56
    public static final Map<LudoColor, int[]> FINISH_COORDINATES = Map.of(
        LudoColor.RED, new int[] {7, 6},
        LudoColor.GREEN, new int[] {6, 7},
        LudoColor.YELLOW, new int[] {7, 8},
        LudoColor.BLUE, new int[] {8, 7}
    );

    // Yard slot coordinates for tokens (4 slots per color)
    public static final Map<LudoColor, int[][]> YARD_COORDINATES = Map.of(
        LudoColor.RED, new int[][] { {2, 2}, {2, 3}, {3, 2}, {3, 3} },
        LudoColor.GREEN, new int[][] { {2, 11}, {2, 12}, {3, 11}, {3, 12} },
        LudoColor.YELLOW, new int[][] { {11, 11}, {11, 12}, {12, 11}, {12, 12} },
        LudoColor.BLUE, new int[][] { {11, 2}, {11, 3}, {12, 2}, {12, 3} }
    );

    public static boolean isSafeTrackSquare(int trackIndex) {
        return SAFE_TRACK_SQUARES.contains(trackIndex);
    }

    /**
     * Resolves the (row, col) grid coordinate for a token given its color, step, and token index.
     */
    public static int[] getGridCoordinate(LudoColor color, int step, int tokenIndex) {
        if (step < 0) {
            int[][] yard = YARD_COORDINATES.get(color);
            int idx = Math.max(0, Math.min(3, tokenIndex));
            return yard[idx];
        }
        if (step <= 50) {
            int trackIndex = (color.getStartTrackIndex() + step) % TRACK_LENGTH;
            return TRACK_COORDINATES[trackIndex];
        }
        if (step <= 55) {
            int[][] homeCol = HOME_COLUMNS.get(color);
            return homeCol[step - 51];
        }
        return FINISH_COORDINATES.get(color);
    }
}
