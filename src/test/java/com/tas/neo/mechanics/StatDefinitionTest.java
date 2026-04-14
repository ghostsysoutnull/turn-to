package com.tas.neo.mechanics;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatDefinitionTest {

    @Test
    void fixed_definition_returns_declared_initial_and_max() {
        FixedStatDefinition def = new FixedStatDefinition(8, 10);
        FixedDice dice = new FixedDice(999); // ignored

        assertThat(def.resolveInitial(dice)).isEqualTo(8);
        assertThat(def.resolveMax(dice)).isEqualTo(10);
    }

    @Test
    void dice_definition_without_fixedMax_uses_rolled_initial_as_max() {
        DiceFormula formula = DiceFormula.parse("2d6+12");
        DiceStatDefinition def = new DiceStatDefinition(formula, OptionalInt.empty());
        FixedDice dice = new FixedDice(4);

        int initial = def.resolveInitial(dice);
        int max = def.resolveMax(dice);

        assertThat(initial).isEqualTo(20);
        assertThat(max)
            .as("max equals rolled initial when fixedMax is empty")
            .isEqualTo(20);
    }

    @Test
    void dice_definition_with_fixedMax_uses_that_value_as_max() {
        DiceFormula formula = DiceFormula.parse("1d6+6");
        DiceStatDefinition def = new DiceStatDefinition(formula, OptionalInt.of(24));
        FixedDice dice = new FixedDice(3);

        assertThat(def.resolveInitial(dice)).isEqualTo(9);
        assertThat(def.resolveMax(dice)).isEqualTo(24);
    }
}
