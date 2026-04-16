package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.player.Player;

public class LuckTest {

    private final Dice dice;

    public LuckTest(Dice dice) {
        this.dice = dice;
    }

    /** Rolls 2d6 and returns true if the roll is less than or equal to the player's current LUCK.
     *  LUCK is always decremented by 1 regardless of outcome. */
    public boolean test(Player player) {
        int roll = dice.roll2d6();
        boolean lucky = roll <= player.getLuck();
        player.modifyAttribute(AttributeType.LUCK, -1);
        return lucky;
    }
}
