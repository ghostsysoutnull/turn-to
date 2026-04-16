package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AvoidSectionsChoiceSelectorTest {

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static SimulatedGameState emptyState() {
        return new SimulatedGameState();
    }

    // -------------------------------------------------------------------------
    // Core behaviour — avoids blacklisted sections
    // -------------------------------------------------------------------------

    @Test
    void never_selects_avoided_section_when_safe_choice_exists() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(40), new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(39), choiceTo(40));
        Random random = new Random(0);

        for (int i = 0; i < 200; i++) {
            int idx = selector.select(choices, emptyState(), random);
            assertThat(choices.get(idx).target())
                .as("selector must never pick avoided §40 when §39 is available")
                .isEqualTo(new SectionTarget(39));
        }
    }

    @Test
    void filters_multiple_avoided_sections() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(4, 40), new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(4), choiceTo(40), choiceTo(5));
        Random random = new Random(0);

        for (int i = 0; i < 200; i++) {
            int idx = selector.select(choices, emptyState(), random);
            assertThat(choices.get(idx).target())
                .as("selector must always pick §5 — the only non-avoided choice")
                .isEqualTo(new SectionTarget(5));
        }
    }

    // -------------------------------------------------------------------------
    // Forced — all choices are avoided
    // -------------------------------------------------------------------------

    @Test
    void falls_through_when_all_choices_are_avoided() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(2, 3), new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(2), choiceTo(3));
        Random random = new Random(0);

        for (int i = 0; i < 50; i++) {
            int idx = selector.select(choices, emptyState(), random);
            assertThat(idx)
                .as("when all choices are avoided, selector must still return a valid index")
                .isBetween(0, choices.size() - 1);
        }
    }

    // -------------------------------------------------------------------------
    // Delegates to wrapped selector when no choices are avoided
    // -------------------------------------------------------------------------

    @Test
    void delegates_normally_when_no_choices_are_avoided() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(99), new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2));
        Random random = new Random(0);

        boolean[] seen = new boolean[2];
        for (int i = 0; i < 200; i++) {
            seen[selector.select(choices, emptyState(), random)] = true;
        }

        assertThat(seen)
            .as("when no choices are avoided, both must be selectable (delegates to wrapped selector)")
            .doesNotContain(false);
    }

    // -------------------------------------------------------------------------
    // Empty blacklist — transparent pass-through
    // -------------------------------------------------------------------------

    @Test
    void empty_blacklist_is_transparent() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(), new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(1), choiceTo(2), choiceTo(3));
        Random random = new Random(0);

        boolean[] seen = new boolean[3];
        for (int i = 0; i < 300; i++) {
            seen[selector.select(choices, emptyState(), random)] = true;
        }

        assertThat(seen)
            .as("empty blacklist must not filter anything — all choices must remain selectable")
            .doesNotContain(false);
    }

    // -------------------------------------------------------------------------
    // Composition with SurvivalChoiceSelector
    // -------------------------------------------------------------------------

    @Test
    void composes_with_survival_selector_as_inner_delegate() {
        // Survival filters INSTANT_DEATH, Avoid filters §40 — together both are excluded
        // This test verifies the composition works: we just check the avoid layer works
        // when wrapping a fixed-choice delegate
        ChoiceSelector inner = (choices, state, rng) -> 0; // always pick first
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(10), inner);
        List<Choice> choices = List.of(choiceTo(10), choiceTo(20));

        int idx = selector.select(choices, emptyState(), new Random(0));
        assertThat(choices.get(idx).target())
            .as("avoid layer must skip §10 and pick §20 even when delegate would choose §10")
            .isEqualTo(new SectionTarget(20));
    }

    // -------------------------------------------------------------------------
    // Single choice — always index 0
    // -------------------------------------------------------------------------

    @Test
    void single_non_avoided_choice_returns_index_zero() {
        ChoiceSelector selector = new AvoidSectionsChoiceSelector(Set.of(99), new RandomChoiceSelector());

        assertThat(selector.select(List.of(choiceTo(5)), emptyState(), new Random(0)))
            .as("single non-avoided choice must return index 0")
            .isZero();
    }
}
