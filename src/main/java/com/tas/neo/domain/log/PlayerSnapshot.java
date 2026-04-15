package com.tas.neo.domain.log;

import com.tas.neo.domain.item.ItemStack;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;
import com.tas.neo.engine.GameState;

import java.util.List;
import java.util.stream.Collectors;

public record PlayerSnapshot(
    String location,
    int stamina,
    int skill,
    int luck,
    int gold,
    List<String> inventory
) {

    /** Creates a snapshot from the current game state. */
    public static PlayerSnapshot of(GameState state) {
        Player player = state.getPlayer();
        String location = locationString(state);
        int stamina = player.getStat(AttributeType.STAMINA);
        int skill = player.getStat(AttributeType.SKILL);
        int luck = player.getStat(AttributeType.LUCK);
        int gold = player.getGold();
        List<String> inventory = player.getInventory().allStacks().stream()
            .map(ItemStack::item)
            .map(i -> i.name())
            .collect(Collectors.toList());
        return new PlayerSnapshot(location, stamina, skill, luck, gold, inventory);
    }

    private static String locationString(GameState state) {
        if (state.isInGrid()) {
            return state.currentGrid()
                .map(g -> "grid:" + g.id())
                .orElse("grid:unknown");
        }
        if (state.currentSection() != null) {
            return "section:" + state.currentSection().number();
        }
        return "unknown";
    }
}
