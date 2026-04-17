package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.ItemStack;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double implementing {@link GameOutput} that records every call as a typed
 * {@link OutputEvent}. Does not print to any stream.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class RecordingOutput implements GameOutput {

    private final List<OutputEvent> events = new ArrayList<>();

    public List<OutputEvent> events() {
        return List.copyOf(events);
    }

    public List<String> messages() {
        List<String> out = new ArrayList<>();
        for (OutputEvent event : events) {
            if (event instanceof OutputEvent.MessageShown m) {
                out.add(m.text());
            }
        }
        return out;
    }

    public boolean wasGameOverShown() {
        return events.stream().anyMatch(e -> e instanceof OutputEvent.GameOverShown);
    }

    public boolean wasVictoryShown() {
        return events.stream().anyMatch(e -> e instanceof OutputEvent.VictoryShown);
    }

    public boolean wasCleared() {
        return events.stream().anyMatch(e -> e instanceof OutputEvent.ScreenCleared);
    }

    @Override
    public void showStatus(Player player, List<PartyMember> activeMembers) {
        events.add(new OutputEvent.StatusShown(player, List.copyOf(activeMembers)));
    }

    @Override
    public void showNarrative(int sectionNumber, String text) {
        events.add(new OutputEvent.NarrativeShown(sectionNumber, text));
    }

    @Override
    public void showChoices(List<Choice> choices) {
        events.add(new OutputEvent.ChoicesShown(List.copyOf(choices)));
    }

    @Override
    public void showMessage(String message) {
        events.add(new OutputEvent.MessageShown(message));
    }

    @Override
    public void showCombatRound(CombatRound round) {
        events.add(new OutputEvent.CombatRoundShown(round));
    }

    @Override
    public void showInventory(List<ItemStack> stacks, int gold, int provisions) {
        events.add(new OutputEvent.InventoryShown(List.copyOf(stacks), gold, provisions));
    }

    @Override
    public void showGameOver(String message) {
        events.add(new OutputEvent.GameOverShown(message));
    }

    @Override
    public void showVictory(String message) {
        events.add(new OutputEvent.VictoryShown(message));
    }

    @Override
    public void clear() {
        events.add(new OutputEvent.ScreenCleared());
    }
}
