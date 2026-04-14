package com.tas.neo.scripting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test double implementing {@link ScriptContext}. Records every call made by a script
 * for inspection.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class RecordingScriptContext implements ScriptContext {

    /** Captured stat change. */
    public record StatChange(String attribute, int delta) {}

    private final List<String> messages = new ArrayList<>();
    private final List<StatChange> statChanges = new ArrayList<>();
    private final Set<String> itemsAdded = new HashSet<>();
    private final Set<String> itemsRemoved = new HashSet<>();
    private final Map<String, Integer> itemCounts = new HashMap<>();
    private final Map<String, Integer> stats = new HashMap<>();
    private int gold;
    private int navigatedTo = -1;
    private int currentSection = -1;

    public List<String> getMessages() {
        return List.copyOf(messages);
    }

    public List<StatChange> getStatChanges() {
        return List.copyOf(statChanges);
    }

    public int navigatedTo() {
        return navigatedTo;
    }

    public boolean itemAdded(String name) {
        return itemsAdded.contains(name);
    }

    public boolean itemRemoved(String name) {
        return itemsRemoved.contains(name);
    }

    public void setCurrentSection(int section) {
        this.currentSection = section;
    }

    public void setItemCount(String name, int count) {
        itemCounts.put(name, count);
    }

    public void setStat(String name, int value) {
        stats.put(name, value);
    }

    public void setGold(int value) {
        this.gold = value;
    }

    @Override
    public void modifyStat(String attribute, int delta) {
        statChanges.add(new StatChange(attribute, delta));
        stats.merge(attribute, delta, Integer::sum);
    }

    @Override
    public int getStat(String attribute) {
        return stats.getOrDefault(attribute, 0);
    }

    @Override
    public void modifyGold(int delta) {
        gold += delta;
    }

    @Override
    public int getGold() {
        return gold;
    }

    @Override
    public void addItem(String itemName) {
        addItem(itemName, 1);
    }

    @Override
    public void addItem(String itemName, int quantity) {
        itemsAdded.add(itemName);
        itemCounts.merge(itemName, quantity, Integer::sum);
    }

    @Override
    public void removeItem(String itemName) {
        removeItem(itemName, 1);
    }

    @Override
    public void removeItem(String itemName, int quantity) {
        itemsRemoved.add(itemName);
        itemCounts.merge(itemName, -quantity, Integer::sum);
    }

    @Override
    public boolean hasItem(String itemName) {
        return itemCounts.getOrDefault(itemName, 0) > 0;
    }

    @Override
    public int getItemCount(String itemName) {
        return itemCounts.getOrDefault(itemName, 0);
    }

    @Override
    public PartyMemberProxy getPartyMember(String id) {
        return null;
    }

    @Override
    public void addPartyMember(String id) {
        // not tracked in this recorder
    }

    @Override
    public void removePartyMember(String id) {
        // not tracked in this recorder
    }

    @Override
    public void navigateTo(int section) {
        navigatedTo = section;
    }

    @Override
    public int currentSection() {
        return currentSection;
    }

    @Override
    public void showMessage(String message) {
        messages.add(message);
    }

    @Override
    public void addChoice(String text, int targetSection) {
        // not tracked in this recorder
    }

    @Override
    public void hideChoice(String id) {
        // not tracked in this recorder
    }
}
