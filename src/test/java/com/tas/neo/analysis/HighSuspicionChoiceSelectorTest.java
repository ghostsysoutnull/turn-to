package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HighSuspicionChoiceSelectorTest {

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    // -------------------------------------------------------------------------
    // HIGH_SUSPICION — prefers suspicion-raising choices
    // -------------------------------------------------------------------------

    @Test
    void high_suspicion_prefers_suspicion_raising_choice() {
        // Section 2 raises suspicion; section 1 does not
        Set<Integer> suspicionSections = Set.of(2);
        ChoiceSelector selector = new HighSuspicionChoiceSelector(suspicionSections);
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2));
        Random random = new Random(0);

        int suspicionCount = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (selector.select(choices, new SimulatedGameState(), random) == 1) suspicionCount++;
        }

        assertThat(suspicionCount)
            .as("HIGH_SUSPICION selector must prefer the suspicion-raising choice " +
                "(weight 3 vs 1) — expected >70%%, got %d/%d", suspicionCount, trials)
            .isGreaterThan(trials * 2 / 3);
    }

    @Test
    void high_suspicion_with_no_suspicion_raising_choices_selects_uniformly() {
        Set<Integer> suspicionSections = Set.of(99); // neither choice raises suspicion
        ChoiceSelector selector = new HighSuspicionChoiceSelector(suspicionSections);
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2));
        Random random = new Random(0);

        int choice0Count = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (selector.select(choices, new SimulatedGameState(), random) == 0) choice0Count++;
        }

        assertThat(choice0Count)
            .as("with no suspicion-raising choices, selection must be roughly uniform")
            .isBetween(350, 650);
    }

    // -------------------------------------------------------------------------
    // LOW_SUSPICION — inverse preference
    // -------------------------------------------------------------------------

    @Test
    void low_suspicion_avoids_suspicion_raising_choice() {
        // Section 2 raises suspicion; section 1 does not
        Set<Integer> suspicionSections = Set.of(2);
        ChoiceSelector selector = new LowSuspicionChoiceSelector(suspicionSections);
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2));
        Random random = new Random(0);

        int nonSuspicionCount = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (selector.select(choices, new SimulatedGameState(), random) == 0) nonSuspicionCount++;
        }

        assertThat(nonSuspicionCount)
            .as("LOW_SUSPICION selector must prefer the non-suspicion-raising choice " +
                "(weight 3 vs 1) — expected >70%%, got %d/%d", nonSuspicionCount, trials)
            .isGreaterThan(trials * 2 / 3);
    }

    // -------------------------------------------------------------------------
    // Single choice — always returns 0
    // -------------------------------------------------------------------------

    @Test
    void single_choice_always_returns_index_zero() {
        ChoiceSelector selector = new HighSuspicionChoiceSelector(Set.of(5));
        assertThat(selector.select(List.of(choiceTo(5)), new SimulatedGameState(), new Random(0)))
            .as("with a single choice, selector must always return index 0")
            .isZero();
    }
}
