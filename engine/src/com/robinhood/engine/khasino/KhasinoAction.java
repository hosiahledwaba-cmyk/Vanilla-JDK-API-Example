package com.robinhood.engine.khasino;

import java.util.*;

/**
 * Encapsulates an action taken by a player on their turn in Khasino.
 */
public class KhasinoAction {

    public enum ActionType {
        CHOW,               // Capture cards/builds from floor
        BUILD_SIMPLE,       // Form a new simple build
        BUILD_COMPOUND,     // Augment an existing build into a compound build
        CHANGE_BUILD_VALUE, // Increase value of an opponent's simple build
        DRIFT,              // Trail/discard a card to the floor
        STASH,              // Solidify owned build by loading same-value card
        SHIYA               // Partnership call: leave captured build for partner
    }

    private final ActionType type;
    private final String playedCardId;
    private final List<String> targetFloorCardIds = new ArrayList<>();
    private final List<String> targetBuildIds = new ArrayList<>();
    private final String targetBuildId; // for compound, change value, stash
    private final int declaredValue;    // for build, change value
    private final String stolenCardId;  // top card from opponent's capture pile
    private final String stolenOpponentId;

    public KhasinoAction(ActionType type,
                         String playedCardId,
                         List<String> targetFloorCardIds,
                         List<String> targetBuildIds,
                         String targetBuildId,
                         int declaredValue,
                         String stolenCardId,
                         String stolenOpponentId) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.playedCardId = playedCardId;
        if (targetFloorCardIds != null) this.targetFloorCardIds.addAll(targetFloorCardIds);
        if (targetBuildIds != null) this.targetBuildIds.addAll(targetBuildIds);
        this.targetBuildId = targetBuildId;
        this.declaredValue = declaredValue;
        this.stolenCardId = stolenCardId;
        this.stolenOpponentId = stolenOpponentId;
    }

    // --- Factory Helpers ---
    public static KhasinoAction chow(String playedCardId, List<String> floorCardIds, List<String> buildIds) {
        return new KhasinoAction(ActionType.CHOW, playedCardId, floorCardIds, buildIds, null, 0, null, null);
    }

    public static KhasinoAction drift(String playedCardId) {
        return new KhasinoAction(ActionType.DRIFT, playedCardId, null, null, null, 0, null, null);
    }

    public static KhasinoAction buildSimple(String playedCardId, List<String> floorCardIds, int declaredValue) {
        return new KhasinoAction(ActionType.BUILD_SIMPLE, playedCardId, floorCardIds, null, null, declaredValue, null, null);
    }

    public static KhasinoAction buildCompound(String targetBuildId, String playedCardId, List<String> floorCardIds, String stolenCardId, String stolenOpponentId) {
        return new KhasinoAction(ActionType.BUILD_COMPOUND, playedCardId, floorCardIds, null, targetBuildId, 0, stolenCardId, stolenOpponentId);
    }

    public static KhasinoAction changeBuildValue(String targetBuildId, String playedCardId, int newDeclaredValue) {
        return new KhasinoAction(ActionType.CHANGE_BUILD_VALUE, playedCardId, null, null, targetBuildId, newDeclaredValue, null, null);
    }

    public static KhasinoAction stash(String targetBuildId, String playedCardId) {
        return new KhasinoAction(ActionType.STASH, playedCardId, null, null, targetBuildId, 0, null, null);
    }

    public static KhasinoAction shiya(String targetBuildId, String playedCardId) {
        return new KhasinoAction(ActionType.SHIYA, playedCardId, null, null, targetBuildId, 0, null, null);
    }

    public ActionType getType() {
        return type;
    }

    public String getPlayedCardId() {
        return playedCardId;
    }

    public List<String> getTargetFloorCardIds() {
        return Collections.unmodifiableList(targetFloorCardIds);
    }

    public List<String> getTargetBuildIds() {
        return Collections.unmodifiableList(targetBuildIds);
    }

    public String getTargetBuildId() {
        return targetBuildId;
    }

    public int getDeclaredValue() {
        return declaredValue;
    }

    public String getStolenCardId() {
        return stolenCardId;
    }

    public String getStolenOpponentId() {
        return stolenOpponentId;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"").append(type.name()).append("\",");
        sb.append("\"playedCardId\":").append(playedCardId != null ? "\"" + playedCardId + "\"" : "null").append(",");
        sb.append("\"targetFloorCardIds\":[");
        for (int i = 0; i < targetFloorCardIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(targetFloorCardIds.get(i)).append("\"");
        }
        sb.append("],");
        sb.append("\"targetBuildIds\":[");
        for (int i = 0; i < targetBuildIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(targetBuildIds.get(i)).append("\"");
        }
        sb.append("],");
        sb.append("\"targetBuildId\":").append(targetBuildId != null ? "\"" + targetBuildId + "\"" : "null").append(",");
        sb.append("\"declaredValue\":").append(declaredValue).append(",");
        sb.append("\"stolenCardId\":").append(stolenCardId != null ? "\"" + stolenCardId + "\"" : "null").append(",");
        sb.append("\"stolenOpponentId\":").append(stolenOpponentId != null ? "\"" + stolenOpponentId + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }
}
