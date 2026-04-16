package com.tas.neo.mechanics;

import com.tas.neo.domain.DiceFormula;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiceFormulaTest {

    @Test
    void parse_simple_NdS_expression() {
        DiceFormula f = DiceFormula.parse("2d6");

        assertThat(f.diceCount()).isEqualTo(2);
        assertThat(f.diceSides()).isEqualTo(6);
        assertThat(f.modifier()).isZero();
    }

    @Test
    void parse_with_positive_modifier() {
        DiceFormula f = DiceFormula.parse("2d6+12");

        assertThat(f.diceCount()).isEqualTo(2);
        assertThat(f.diceSides()).isEqualTo(6);
        assertThat(f.modifier()).isEqualTo(12);
    }

    @Test
    void parse_with_negative_modifier() {
        DiceFormula f = DiceFormula.parse("1d6-1");

        assertThat(f.diceCount()).isEqualTo(1);
        assertThat(f.diceSides()).isEqualTo(6);
        assertThat(f.modifier()).isEqualTo(-1);
    }

    @Test
    void parse_single_die_with_modifier() {
        DiceFormula f = DiceFormula.parse("1d6+6");

        assertThat(f.diceCount()).isEqualTo(1);
        assertThat(f.modifier()).isEqualTo(6);
    }

    @Test
    void parse_rejects_empty_expression() {
        assertThatThrownBy(() -> DiceFormula.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_malformed_expression() {
        assertThatThrownBy(() -> DiceFormula.parse("2x6")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_missing_side_count() {
        assertThatThrownBy(() -> DiceFormula.parse("2d")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roll_sums_dice_rolls_plus_modifier() {
        DiceFormula f = DiceFormula.parse("2d6+12");
        FixedDice dice = new FixedDice(4);

        int total = f.roll(dice);

        assertThat(total).isEqualTo(4 + 4 + 12);
    }

    @Test
    void roll_of_NdS_without_modifier() {
        DiceFormula f = DiceFormula.parse("3d6");
        FixedDice dice = new FixedDice(5);

        int total = f.roll(dice);

        assertThat(total).isEqualTo(15);
    }

    @Test
    void roll_with_negative_modifier_can_reduce_total() {
        DiceFormula f = DiceFormula.parse("1d6-1");
        FixedDice dice = new FixedDice(6);

        int total = f.roll(dice);

        assertThat(total).isEqualTo(5);
    }
}
