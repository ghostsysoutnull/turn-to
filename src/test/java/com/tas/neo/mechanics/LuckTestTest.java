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
 * Tests for {@link LuckTest}.
 *
 * <p>Covers design/03-combat-engine.md LuckTest section and design/06-testability.md
 * test layer table row for LuckTest.
 */
class LuckTestTest {

    private static Player playerWithLuck(int luck) {
        Map<AttributeType, Attribute> attrs = new EnumMap<>(AttributeType.class);
        attrs.put(AttributeType.SKILL, new Attribute(AttributeType.SKILL, 9, 9));
        attrs.put(AttributeType.STAMINA, new Attribute(AttributeType.STAMINA, 20, 20));
        attrs.put(AttributeType.LUCK, new Attribute(AttributeType.LUCK, luck, luck));
        return new Player(attrs, new Inventory(), 0, 0);
    }

    // Rolls 2d6 = 3 + 3 = 6; LUCK = 8 → 6 <= 8 → lucky
    @Test
    void test_returns_true_when_roll_is_less_than_or_equal_to_luck() {
        Player player = playerWithLuck(8);
        LuckTest luckTest = new LuckTest(new FixedDice(3)); // 3 + 3 = 6

        boolean result = luckTest.test(player);

        assertThat(result)
            .as("roll of 6 against LUCK 8 should be lucky")
            .isTrue();
    }

    // Rolls 2d6 = 6 + 6 = 12; LUCK = 8 → 12 > 8 → unlucky
    @Test
    void test_returns_false_when_roll_exceeds_luck() {
        Player player = playerWithLuck(8);
        LuckTest luckTest = new LuckTest(new FixedDice(6)); // 6 + 6 = 12

        boolean result = luckTest.test(player);

        assertThat(result)
            .as("roll of 12 against LUCK 8 should be unlucky")
            .isFalse();
    }

    // LUCK is always decremented by 1 on a lucky outcome
    @Test
    void luck_is_decremented_by_1_on_lucky_outcome() {
        Player player = playerWithLuck(8);
        LuckTest luckTest = new LuckTest(new FixedDice(3)); // 3 + 3 = 6, lucky

        luckTest.test(player);

        assertThat(player.getLuck())
            .as("LUCK must always be decremented by 1 after a luck test, regardless of outcome")
            .isEqualTo(7);
    }

    // LUCK is always decremented by 1 on an unlucky outcome too
    @Test
    void luck_is_decremented_by_1_on_unlucky_outcome() {
        Player player = playerWithLuck(8);
        LuckTest luckTest = new LuckTest(new FixedDice(6)); // 6 + 6 = 12, unlucky

        luckTest.test(player);

        assertThat(player.getLuck())
            .as("LUCK must always be decremented by 1 even when the luck test fails")
            .isEqualTo(7);
    }

    // Roll exactly equals LUCK — boundary: lucky
    @Test
    void test_returns_true_when_roll_exactly_equals_luck() {
        Player player = playerWithLuck(6);
        LuckTest luckTest = new LuckTest(new FixedDice(3)); // 3 + 3 = 6, equal to LUCK

        boolean result = luckTest.test(player);

        assertThat(result)
            .as("roll equal to LUCK should be lucky (2d6 <= LUCK)")
            .isTrue();
    }
}
