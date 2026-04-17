package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.ItemStack;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import java.util.List;

/**
 * Structured event hierarchy emitted by the engine and recorded by {@link GameLogger}.
 */
public sealed interface OutputEvent
    permits OutputEvent.NarrativeShown,
            OutputEvent.MessageShown,
            OutputEvent.CombatRoundShown,
            OutputEvent.ChoicesShown,
            OutputEvent.VictoryShown,
            OutputEvent.GameOverShown,
            OutputEvent.StatusShown,
            OutputEvent.InventoryShown,
            OutputEvent.ScreenCleared,
            OutputEvent.ItemGained,
            OutputEvent.ItemLost,
            OutputEvent.StatChanged,
            OutputEvent.CombatResolved {

    record NarrativeShown(int sectionNumber, String text)           implements OutputEvent {}
    record MessageShown(String text)                                implements OutputEvent {}
    record CombatRoundShown(CombatRound round)                      implements OutputEvent {}
    record ChoicesShown(List<Choice> choices)                       implements OutputEvent {}
    record VictoryShown(String message)                             implements OutputEvent {}
    record GameOverShown(String message)                            implements OutputEvent {}
    record StatusShown(Player player, List<PartyMember> activeMembers) implements OutputEvent {}
    record InventoryShown(List<ItemStack> stacks, int gold, int provisions) implements OutputEvent {}
    record ScreenCleared()                                          implements OutputEvent {}
    record ItemGained(String itemName)                              implements OutputEvent {}
    record ItemLost(String itemName)                                implements OutputEvent {}
    record StatChanged(String attribute, int delta, int newValue)   implements OutputEvent {}
    record CombatResolved(String opponentName, int opponentSkill, int opponentStamina,
                          boolean playerWon, int rounds, int staminaLost) implements OutputEvent {}
}
