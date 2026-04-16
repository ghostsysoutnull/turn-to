package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RandomChoiceSelectorTest {

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static SimulatedGameState emptyState() {
        return new SimulatedGameState();
    }

    // -------------------------------------------------------------------------
    // Single choice
    // -------------------------------------------------------------------------

    @Test
    void single_choice_always_returns_index_zero() {
        ChoiceSelector selector = new RandomChoiceSelector();
        List<Choice> choices = List.of(choiceTo(5));

        for (int i = 0; i < 20; i++) {
            assertThat(selector.select(choices, emptyState(), new Random(i)))
                .as("with a single choice, selector must always return index 0")
                .isZero();
        }
    }

    // -------------------------------------------------------------------------
    // Multiple choices — deterministic with fixed seed
    // -------------------------------------------------------------------------

    @Test
    void fixed_seed_produces_same_sequence_on_repeated_calls() {
        ChoiceSelector selector = new RandomChoiceSelector();
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2), choiceTo(3));

        int[] run1 = new int[10];
        int[] run2 = new int[10];
        for (int i = 0; i < 10; i++) {
            run1[i] = selector.select(choices, emptyState(), new Random(42));
            run2[i] = selector.select(choices, emptyState(), new Random(42));
        }
        assertThat(run1)
            .as("same seed must produce the same selection each time")
            .isEqualTo(run2);
    }

    // -------------------------------------------------------------------------
    // Distribution — all choices reachable over many trials
    // -------------------------------------------------------------------------

    @Test
    void all_choices_selected_over_many_trials() {
        ChoiceSelector selector = new RandomChoiceSelector();
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2), choiceTo(3));
        Random random = new Random(0);
        boolean[] seen = new boolean[3];

        for (int i = 0; i < 300; i++) {
            seen[selector.select(choices, emptyState(), random)] = true;
        }

        assertThat(seen)
            .as("all three choices must be selected at least once over 300 trials")
            .doesNotContain(false);
    }

    // -------------------------------------------------------------------------
    // Return value is a valid index
    // -------------------------------------------------------------------------

    @Test
    void selected_index_is_within_bounds() {
        ChoiceSelector selector = new RandomChoiceSelector();
        List<Choice> choices = List.of(choiceTo(10), choiceTo(20));
        Random random = new Random(7);

        for (int i = 0; i < 100; i++) {
            int idx = selector.select(choices, emptyState(), random);
            assertThat(idx)
                .as("selected index must be a valid index into the choices list")
                .isBetween(0, choices.size() - 1);
        }
    }
}
