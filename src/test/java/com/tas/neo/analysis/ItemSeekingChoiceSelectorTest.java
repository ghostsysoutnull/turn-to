package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSeekingChoiceSelectorTest {

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static Choice conditionedChoiceTo(int target, String item) {
        return Choice.to("take", new SectionTarget(target), new HasItemCondition(item));
    }

    // -------------------------------------------------------------------------
    // Item-seeking bias
    // -------------------------------------------------------------------------

    @Test
    void item_gated_choice_selected_more_often_than_unconditioned() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Sword");  // satisfies the HAS_ITEM condition

        ChoiceSelector selector = new ItemSeekingChoiceSelector();
        // choice 0: unconditioned; choice 1: HAS_ITEM:Sword (satisfied)
        List<Choice> choices = List.of(choiceTo(1), conditionedChoiceTo(2, "Sword"));
        Random random = new Random(0);

        int itemSeekingCount = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (selector.select(choices, state, random) == 1) itemSeekingCount++;
        }

        assertThat(itemSeekingCount)
            .as("item-gated choice (weight 2) must be selected more often than unconditioned " +
                "choice (weight 1) — expected ~67%%, got %d/%d", itemSeekingCount, trials)
            .isGreaterThan(trials / 2);
    }

    @Test
    void unsatisfied_item_condition_not_weighted_higher() {
        SimulatedGameState state = new SimulatedGameState();
        // item NOT in inventory — condition unsatisfied, should not get extra weight

        ChoiceSelector selector = new ItemSeekingChoiceSelector();
        List<Choice> choices = List.of(choiceTo(1), conditionedChoiceTo(2, "Sword"));
        Random random = new Random(0);

        int choice1Count = 0;
        int trials = 1000;
        for (int i = 0; i < trials; i++) {
            if (selector.select(choices, state, random) == 0) choice1Count++;
        }

        // With equal weighting, both should be ~50%.
        // If unsatisfied condition incorrectly gets extra weight, choice1Count would be < 40%.
        assertThat(choice1Count)
            .as("unsatisfied HAS_ITEM condition must not receive extra weight; " +
                "both choices should be selected roughly equally")
            .isBetween(350, 650);
    }

    // -------------------------------------------------------------------------
    // Single choice — always returns 0
    // -------------------------------------------------------------------------

    @Test
    void single_choice_always_returns_index_zero() {
        ChoiceSelector selector = new ItemSeekingChoiceSelector();
        List<Choice> choices = List.of(conditionedChoiceTo(5, "Key"));
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Key");

        assertThat(selector.select(choices, state, new Random(0)))
            .as("with a single choice, selector must always return index 0")
            .isZero();
    }
}
