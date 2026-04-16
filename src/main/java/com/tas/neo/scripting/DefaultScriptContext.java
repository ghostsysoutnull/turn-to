package com.tas.neo.scripting;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.item.Item;
import com.tas.neo.domain.item.ItemCategory;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.GameState;
import com.tas.neo.io.GameOutput;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class DefaultScriptContext implements ScriptContext {

    private enum Mode { SECTION, CHOICES, CELL }

    private final Player player;
    private final GameState state;
    private final GameOutput output;
    private final List<Choice> choices;
    private final int sectionNumber;
    private final Mode mode;

    private DefaultScriptContext(Player player, GameState state, GameOutput output,
                                  List<Choice> choices, int sectionNumber, Mode mode) {
        this.player = player;
        this.state = state;
        this.output = output;
        this.choices = choices;
        this.sectionNumber = sectionNumber;
        this.mode = mode;
    }

    public static DefaultScriptContext forSection(Player player, GameState state, GameOutput output,
                                                   List<Choice> choices, int sectionNumber) {
        return new DefaultScriptContext(player, state, output, choices, sectionNumber, Mode.SECTION);
    }

    public static DefaultScriptContext forChoices(Player player, GameState state, GameOutput output,
                                                   List<Choice> choices, int sectionNumber) {
        return new DefaultScriptContext(player, state, output, choices, sectionNumber, Mode.CHOICES);
    }

    public static DefaultScriptContext forCell(Player player, GameState state, GameOutput output,
                                               List<Choice> choices) {
        return new DefaultScriptContext(player, state, output, choices, -1, Mode.CELL);
    }

    @Override
    public void modifyStat(String attribute, int delta) {
        player.modifyAttribute(AttributeType.valueOf(attribute), delta);
    }

    @Override
    public int getStat(String attribute) {
        return player.getStat(AttributeType.valueOf(attribute));
    }

    @Override
    public void modifyGold(int delta) {
        player.modifyGold(delta);
    }

    @Override
    public int getGold() {
        return player.getGold();
    }

    @Override
    public void addItem(String itemName) {
        addItem(itemName, 1);
    }

    @Override
    public void addItem(String itemName, int quantity) {
        Item item = new Item(itemName, "", ItemCategory.USABLE, true, ScriptBlock.empty());
        player.getInventory().add(item, quantity);
    }

    @Override
    public void removeItem(String itemName) {
        removeItem(itemName, 1);
    }

    @Override
    public void removeItem(String itemName, int quantity) {
        player.getInventory().remove(itemName, quantity);
    }

    @Override
    public boolean hasItem(String itemName) {
        return player.getInventory().has(itemName);
    }

    @Override
    public int getItemCount(String itemName) {
        return player.getInventory().count(itemName);
    }

    @Override
    public PartyMemberProxy getPartyMember(String id) {
        PartyMember member = state.getPartyMember(id);
        return member != null ? PartyMemberProxy.of(member) : PartyMemberProxy.unknown(id);
    }

    @Override
    public boolean isPartyMemberActive(String id) {
        return getPartyMember(id).isActive();
    }

    @Override
    public void addPartyMember(String id) {
        PartyMember member = state.getPartyMember(id);
        if (member == null || member.state() == MemberState.ACTIVE) return;
        member.setState(MemberState.ACTIVE);
    }

    @Override
    public void removePartyMember(String id) {
        PartyMember member = state.getPartyMember(id);
        if (member == null || member.state() != MemberState.ACTIVE) return;
        member.setState(MemberState.REMOVED);
    }

    @Override
    public void navigateTo(int section) {
        if (mode == Mode.CHOICES) {
            throw new UnsupportedOperationException("navigateTo is not permitted in onChoices context");
        }
    }

    @Override
    public int currentSection() {
        return sectionNumber;
    }

    @Override
    public void showMessage(String message) {
        output.showMessage(message);
    }

    @Override
    public void addChoice(String text, int targetSection) {
        if (mode != Mode.CHOICES) {
            throw new UnsupportedOperationException("addChoice is not permitted in " + mode + " context");
        }
        choices.add(new Choice(text, new SectionTarget(targetSection),
                               Optional.empty(), Optional.empty()));
    }

    @Override
    public void hideChoice(String id) {
        Iterator<Choice> it = choices.iterator();
        while (it.hasNext()) {
            Choice c = it.next();
            if (c.id().isPresent() && c.id().get().equals(id)) {
                it.remove();
                return;
            }
        }
    }
}
