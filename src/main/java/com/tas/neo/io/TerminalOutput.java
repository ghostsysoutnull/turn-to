package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.combat.CombatRound;
import com.tas.neo.domain.item.ItemStack;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.Player;

import java.io.PrintStream;
import java.util.List;

public class TerminalOutput implements GameOutput {

    private final PrintStream out;

    public TerminalOutput(PrintStream out) {
        this.out = out;
    }

    @Override
    public void showStatus(Player player, List<PartyMember> activeMembers) {
        out.println("SKILL: " + player.getSkill()
            + "  STAMINA: " + player.getStamina()
            + "  LUCK: " + player.getLuck());
    }

    @Override
    public void showNarrative(String text) {
        out.println(text);
    }

    @Override
    public void showChoices(List<Choice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            out.println((i + 1) + ". " + choices.get(i).text());
        }
    }

    @Override
    public void showMessage(String message) {
        out.println(message);
    }

    @Override
    public void showCombatRound(CombatRound round) {
        out.println("Combat round: " + round);
    }

    @Override
    public void showInventory(List<ItemStack> stacks, int gold, int provisions) {
        out.println("INVENTORY");
        out.println("─────────────────────────────────────");
        for (ItemStack stack : stacks) {
            out.println("  " + stack.displayName());
        }
        out.println("─────────────────────────────────────");
        out.println("  Gold: " + gold + "    Provisions: " + provisions);
    }

    @Override
    public void showGameOver(String message) {
        out.println(message);
    }

    @Override
    public void showVictory(String message) {
        out.println(message);
    }

    @Override
    public void clear() {
        out.println();
    }
}
