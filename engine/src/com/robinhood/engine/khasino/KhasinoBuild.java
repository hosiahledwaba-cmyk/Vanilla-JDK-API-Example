package com.robinhood.engine.khasino;

import java.util.*;

/**
 * Represents a Simple or Compound Build on the floor in South African Khasino.
 *
 * Rules:
 * - Simple Build: Contains a single set of cards summing to captureValue (<= 10).
 *   Its value can be increased/changed by an opponent by adding one card from hand,
 *   provided the opponent holds a card of the new value.
 * - Compound / Augmented Build: Contains two or more sets/cards that independently equal
 *   the captureValue. Once compound, its captureValue can NEVER be changed.
 * - Cards are stacked following the "small card on top" convention.
 */
public class KhasinoBuild {

    private final String id;
    private String ownerId;
    private int teamId; // 1 or 2 for partnership, or 0 for singles
    private int captureValue;
    // Each element in groups is an independent set summing to captureValue
    private final List<List<KhasinoCard>> groups = new ArrayList<>();

    public KhasinoBuild(String id, String ownerId, int teamId, int captureValue, List<KhasinoCard> initialGroup) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");
        this.teamId = teamId;
        this.captureValue = captureValue;
        if (initialGroup != null && !initialGroup.isEmpty()) {
            addGroup(initialGroup);
        }
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public int getTeamId() {
        return teamId;
    }

    public void setTeamId(int teamId) {
        this.teamId = teamId;
    }

    public int getCaptureValue() {
        return captureValue;
    }

    public boolean isCompound() {
        return groups.size() > 1;
    }

    public List<List<KhasinoCard>> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    /**
     * Adds an independent group of cards that equals captureValue.
     * Orders cards inside the group according to the South African "small card on top" convention
     * (largest at index 0 [bottom], smallest at the end [top]).
     */
    public void addGroup(List<KhasinoCard> groupCards) {
        List<KhasinoCard> sorted = new ArrayList<>(groupCards);
        // Sort descending so largest is at index 0 (bottom) and smallest is at the top
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        groups.add(sorted);
    }

    /**
     * Changes the value of an opponent's simple build by adding a card from hand.
     * Only valid if the build is simple (not compound).
     */
    public boolean changeValue(int newCaptureValue, String newOwnerId, int newTeamId, KhasinoCard cardFromHand) {
        if (isCompound()) {
            return false; // Compound build value can never be changed!
        }
        if (newCaptureValue <= captureValue || newCaptureValue > 10) {
            return false;
        }

        this.captureValue = newCaptureValue;
        this.ownerId = newOwnerId;
        this.teamId = newTeamId;

        // Add cardFromHand into the single group
        if (!groups.isEmpty()) {
            List<KhasinoCard> group = new ArrayList<>(groups.get(0));
            group.add(cardFromHand);
            group.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            groups.set(0, group);
        } else {
            List<KhasinoCard> group = new ArrayList<>();
            group.add(cardFromHand);
            groups.add(group);
        }
        return true;
    }

    public List<KhasinoCard> getAllCards() {
        List<KhasinoCard> all = new ArrayList<>();
        for (List<KhasinoCard> g : groups) {
            all.addAll(g);
        }
        return all;
    }

    public int getTotalCardCount() {
        int count = 0;
        for (List<KhasinoCard> g : groups) {
            count += g.size();
        }
        return count;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"ownerId\":\"").append(ownerId).append("\",");
        sb.append("\"teamId\":").append(teamId).append(",");
        sb.append("\"captureValue\":").append(captureValue).append(",");
        sb.append("\"isCompound\":").append(isCompound()).append(",");
        sb.append("\"groups\":[");
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("[");
            List<KhasinoCard> g = groups.get(i);
            for (int j = 0; j < g.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append(g.get(j).toJson());
            }
            sb.append("]");
        }
        sb.append("],");
        sb.append("\"allCards\":[");
        List<KhasinoCard> all = getAllCards();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(all.get(i).toJson());
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }
}
