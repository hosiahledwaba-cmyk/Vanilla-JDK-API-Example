package com.robinhood.engine.snakesandladders;

import java.security.SecureRandom;
import java.util.*;

public class Board {
    private final Map<Integer, Snake> snakes = new HashMap<>();
    private final Map<Integer, Ladder> ladders = new HashMap<>();

    private static final int BOARD_SIZE = 100;
    private static final int SNAKE_COUNT = 4;
    private static final int LADDER_COUNT = 5;

    private static final int MIN_SNAKE_LENGTH = 10;
    private static final int MAX_SNAKE_LENGTH = 50;

    private static final int MIN_LADDER_LENGTH = 10;
    private static final int MAX_LADDER_LENGTH = 40;

    private final Set<Integer> usedPositions = new HashSet<>();
    private final SecureRandom random = new SecureRandom();

    public Board() {
        generateRandomSnakes();
        generateRandomLadders();
    }

    private void generateRandomSnakes() {
        int attempts = 0;
        while (snakes.size() < SNAKE_COUNT && attempts < 1000) {
            int head = randBetween(30, BOARD_SIZE - 1);
            int length = randBetween(MIN_SNAKE_LENGTH, MAX_SNAKE_LENGTH);
            int tail = head - length;

            if (tail < 1 || isConflict(head, tail)) {
                attempts++;
                continue;
            }

            Snake snake = new Snake(head, tail);
            snakes.put(head, snake);
            usedPositions.add(head);
            usedPositions.add(tail);
        }
    }

    private void generateRandomLadders() {
        int attempts = 0;
        while (ladders.size() < LADDER_COUNT && attempts < 1000) {
            int base = randBetween(2, BOARD_SIZE - MAX_LADDER_LENGTH - 1);
            int length = randBetween(MIN_LADDER_LENGTH, MAX_LADDER_LENGTH);
            int top = base + length;

            if (top >= BOARD_SIZE || isConflict(base, top)) {
                attempts++;
                continue;
            }

            Ladder ladder = new Ladder(base, top);
            ladders.put(base, ladder);
            usedPositions.add(base);
            usedPositions.add(top);
        }
    }

    private boolean isConflict(int a, int b) {
        return usedPositions.contains(a) || usedPositions.contains(b);
    }

    private int randBetween(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    public int resolveTile(int position) {
        if (snakes.containsKey(position)) {
            return snakes.get(position).getTail();
        }
        if (ladders.containsKey(position)) {
            return ladders.get(position).getTop();
        }
        return position;
    }

    public boolean isWinningPosition(int position) {
        return position >= BOARD_SIZE;
    }

    public Map<Integer, Integer> getSnakesMap() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Snake s : snakes.values()) map.put(s.getHead(), s.getTail());
        return map;
    }

    public Map<Integer, Integer> getLaddersMap() {
        Map<Integer, Integer> map = new HashMap<>();
        for (Ladder l : ladders.values()) map.put(l.getBase(), l.getTop());
        return map;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"snakes\":{");
        int count = 0;
        for (Snake s : snakes.values()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(s.getHead()).append("\":").append(s.getTail());
        }
        sb.append("},\"ladders\":{");
        count = 0;
        for (Ladder l : ladders.values()) {
            if (count++ > 0) sb.append(",");
            sb.append("\"").append(l.getBase()).append("\":").append(l.getTop());
        }
        sb.append("},\"snakeList\":[");
        count = 0;
        for (Snake s : snakes.values()) {
            if (count++ > 0) sb.append(",");
            sb.append("{\"head\":").append(s.getHead()).append(",\"tail\":").append(s.getTail()).append("}");
        }
        sb.append("],\"ladderList\":[");
        count = 0;
        for (Ladder l : ladders.values()) {
            if (count++ > 0) sb.append(",");
            sb.append("{\"base\":").append(l.getBase()).append(",\"top\":").append(l.getTop()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    public Map<String, Object> serializeBoard() {
        Map<String, Object> data = new HashMap<>();
        data.put("snakes", snakes.values().stream()
            .map(s -> Map.of("head", s.getHead(), "tail", s.getTail()))
            .toList());
        data.put("ladders", ladders.values().stream()
            .map(l -> Map.of("base", l.getBase(), "top", l.getTop()))
            .toList());
        return data;
    }
}
