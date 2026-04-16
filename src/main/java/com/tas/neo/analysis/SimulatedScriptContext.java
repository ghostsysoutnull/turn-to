package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.scripting.PartyMemberProxy;
import com.tas.neo.scripting.ScriptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public class SimulatedScriptContext implements ScriptContext {

    private final SimulatedGameState state;
    private OptionalInt navigationTarget = OptionalInt.empty();
    private final List<Choice> dynamicChoices = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int currentSection = 0;

    public SimulatedScriptContext(SimulatedGameState state) {
        this.state = state;
    }

    public void setCurrentSection(int section) {
        this.currentSection = section;
    }

    // -------------------------------------------------------------------------
    // Delegation to SimulatedGameState
    // -------------------------------------------------------------------------

    @Override
    public void addItem(String itemName) {
        state.addItem(itemName);
    }

    @Override
    public void addItem(String itemName, int quantity) {
        state.addItem(itemName);
    }

    @Override
    public void removeItem(String itemName) {
        state.removeItem(itemName);
    }

    @Override
    public void removeItem(String itemName, int quantity) {
        state.removeItem(itemName);
    }

    @Override
    public boolean hasItem(String itemName) {
        return state.hasItem(itemName);
    }

    @Override
    public int getItemCount(String itemName) {
        return state.hasItem(itemName) ? 1 : 0;
    }

    @Override
    public void modifyGold(int delta) {
        state.modifyGold(delta);
    }

    @Override
    public int getGold() {
        return state.gold();
    }

    @Override
    public void modifyStat(String attribute, int delta) {
        state.modifyStat(attribute, delta);
    }

    @Override
    public int getStat(String attribute) {
        return state.stat(attribute);
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    @Override
    public void navigateTo(int section) {
        navigationTarget = OptionalInt.of(section);
    }

    @Override
    public int currentSection() {
        return currentSection;
    }

    public OptionalInt navigationTarget() {
        return navigationTarget;
    }

    public void clearNavigationTarget() {
        navigationTarget = OptionalInt.empty();
    }

    // -------------------------------------------------------------------------
    // Output — no-op in simulation
    // -------------------------------------------------------------------------

    @Override
    public void showMessage(String message) {
        // no-op
    }

    // -------------------------------------------------------------------------
    // Dynamic choices
    // -------------------------------------------------------------------------

    @Override
    public void addChoice(String text, int targetSection) {
        dynamicChoices.add(Choice.to(text, new SectionTarget(targetSection)));
    }

    public List<Choice> dynamicChoices() {
        return List.copyOf(dynamicChoices);
    }

    // -------------------------------------------------------------------------
    // Unsupported operations — log warnings, no exception
    // -------------------------------------------------------------------------

    @Override
    public PartyMemberProxy getPartyMember(String id) {
        warnings.add("unsupported: getPartyMember");
        return PartyMemberProxy.unknown(id);
    }

    @Override
    public void addPartyMember(String id) {
        warnings.add("unsupported: addPartyMember");
    }

    @Override
    public void removePartyMember(String id) {
        warnings.add("unsupported: removePartyMember");
    }

    @Override
    public void hideChoice(String id) {
        warnings.add("unsupported: hideChoice");
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }
}
