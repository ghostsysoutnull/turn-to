package com.tas.neo.analysis;

import com.tas.neo.scripting.AdventureScriptState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimulatedGameState {

    private final Set<String> inventory = new HashSet<>();
    private int gold = 0;
    private final Map<String, Integer> stats = new HashMap<>();
    private final LinkedHashMap<Integer, Integer> visitCounts = new LinkedHashMap<>();
    private final List<Integer> visitOrder = new ArrayList<>();
    private final List<ChapterSnapshot> snapshots = new ArrayList<>();
    private final AdventureScriptState scriptState = new AdventureScriptState();

    // -------------------------------------------------------------------------
    // Inventory
    // -------------------------------------------------------------------------

    public void addItem(String itemName) {
        inventory.add(itemName);
    }

    public void removeItem(String itemName) {
        inventory.remove(itemName);
    }

    public boolean hasItem(String itemName) {
        return inventory.contains(itemName);
    }

    /** Returns an immutable snapshot of the current inventory. */
    public Set<String> inventory() {
        return Set.copyOf(inventory);
    }

    // -------------------------------------------------------------------------
    // Gold
    // -------------------------------------------------------------------------

    public void modifyGold(int delta) {
        gold = Math.max(0, gold + delta);
    }

    public int gold() {
        return gold;
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    public void setStat(String name, int value) {
        stats.put(name, value);
    }

    public void modifyStat(String name, int delta) {
        int current = stats.getOrDefault(name, 0);
        stats.put(name, Math.max(0, current + delta));
    }

    public int stat(String name) {
        return stats.getOrDefault(name, 0);
    }

    // -------------------------------------------------------------------------
    // Visit tracking
    // -------------------------------------------------------------------------

    public void recordVisit(int section) {
        visitOrder.add(section);
        visitCounts.merge(section, 1, Integer::sum);
    }

    public int visitCount(int section) {
        return visitCounts.getOrDefault(section, 0);
    }

    public List<Integer> sectionsVisited() {
        return List.copyOf(visitOrder);
    }

    // -------------------------------------------------------------------------
    // Chapter snapshots
    // -------------------------------------------------------------------------

    public void recordChapterSnapshot(String chapterId, int atSection) {
        snapshots.add(new ChapterSnapshot(
            chapterId,
            atSection,
            Set.copyOf(inventory),
            gold,
            scriptState.snapshot()
        ));
    }

    public List<ChapterSnapshot> chapterSnapshots() {
        return List.copyOf(snapshots);
    }

    // -------------------------------------------------------------------------
    // Script state
    // -------------------------------------------------------------------------

    public AdventureScriptState scriptState() {
        return scriptState;
    }
}
