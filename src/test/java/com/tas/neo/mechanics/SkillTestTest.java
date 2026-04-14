package com.tas.neo.mechanics;

import com.tas.neo.domain.player.Attribute;
import com.tas.neo.domain.player.AttributeType;
import com.tas.neo.domain.item.Inventory;
import com.tas.neo.domain.player.Player;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SkillTest}.
 *
 * <p>Covers design/03-combat-engine.md SkillTest section and design/06-testability.md
 * test layer table row for SkillTest.
 */
class SkillTestTest {

    private static Player playerWithSkill(int skill) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, skill, skill));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, 20, 20));
        attrs.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, 8, 8));
        return new Player(attrs, new Inventory(), 0, 0);
    }

    // Rolls 2d6 = 3 + 3 = 6; SKILL = 9 → 6 <= 9 → pass
    @Test
    void test_returns_true_when_roll_is_less_than_or_equal_to_skill() {
        Player player = playerWithSkill(9);
        SkillTest skillTest = new SkillTest(new FixedDice(3)); // 3 + 3 = 6

        boolean result = skillTest.test(player);

        assertThat(result)
            .as("roll of 6 against SKILL 9 should pass")
            .isTrue();
    }

    // Rolls 2d6 = 6 + 6 = 12; SKILL = 9 → 12 > 9 → fail
    @Test
    void test_returns_false_when_roll_exceeds_skill() {
        Player player = playerWithSkill(9);
        SkillTest skillTest = new SkillTest(new FixedDice(6)); // 6 + 6 = 12

        boolean result = skillTest.test(player);

        assertThat(result)
            .as("roll of 12 against SKILL 9 should fail")
            .isFalse();
    }

    // SKILL must not be decremented — unlike LUCK
    @Test
    void skill_is_not_decremented_after_a_passing_test() {
        Player player = playerWithSkill(9);
        SkillTest skillTest = new SkillTest(new FixedDice(3)); // pass

        skillTest.test(player);

        assertThat(player.getSkill())
            .as("SKILL must not be modified by a skill test")
            .isEqualTo(9);
    }

    @Test
    void skill_is_not_decremented_after_a_failing_test() {
        Player player = playerWithSkill(9);
        SkillTest skillTest = new SkillTest(new FixedDice(6)); // fail

        skillTest.test(player);

        assertThat(player.getSkill())
            .as("SKILL must not be modified even when the skill test fails")
            .isEqualTo(9);
    }

    // Roll exactly equals SKILL — boundary: pass
    @Test
    void test_returns_true_when_roll_exactly_equals_skill() {
        Player player = playerWithSkill(6);
        SkillTest skillTest = new SkillTest(new FixedDice(3)); // 3 + 3 = 6, equal to SKILL

        boolean result = skillTest.test(player);

        assertThat(result)
            .as("roll equal to SKILL should pass (2d6 <= SKILL)")
            .isTrue();
    }
}
