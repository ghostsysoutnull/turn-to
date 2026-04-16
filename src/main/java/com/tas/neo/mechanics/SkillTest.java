package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;
import com.tas.neo.domain.player.Player;

public class SkillTest {

    private final Dice dice;

    public SkillTest(Dice dice) {
        this.dice = dice;
    }

    /** Rolls 2d6 and returns true if the roll is less than or equal to the player's current SKILL.
     *  SKILL is not modified by a skill test. */
    public boolean test(Player player) {
        int roll = dice.roll2d6();
        return roll <= player.getSkill();
    }
}
