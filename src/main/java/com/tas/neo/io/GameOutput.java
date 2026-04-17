package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.ItemStack;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;
import java.util.List;

public interface GameOutput {
    void showStatus(int sectionNumber, Player player, List<PartyMember> activeMembers);
    void showNarrative(String text);
    void showChoices(List<Choice> choices);
    void showMessage(String message);
    void showCombatRound(CombatRound round);
    void showInventory(List<ItemStack> stacks, int gold, int provisions);
    void showGameOver(String message);
    void showVictory(String message);
    void clear();
    void showLuckTest(int roll, int luck, boolean passed);
    void showSkillTest(int roll, int skill, boolean passed);
}
