package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class SurvivalChoiceSelectorTest {

    private static Choice choiceTo(int target) {
        return Choice.to("go", new SectionTarget(target));
    }

    private static Section normal(int number) {
        return new Section(number, "§" + number, List.of(), List.of(), SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section instantDeath(int number) {
        return new Section(number, "§" + number, List.of(), List.of(), SectionType.INSTANT_DEATH, ScriptBlock.empty());
    }

    private static Adventure adventureWith(Section... sections) {
        return new Adventure(
            "test", "Test", "", sections[0].number(), 0,
            List.of(sections), List.of(), List.of(), List.of(), List.of(),
            ScriptBlock.empty()
        );
    }

    // -------------------------------------------------------------------------
    // Core behaviour — avoids INSTANT_DEATH targets
    // -------------------------------------------------------------------------

    @Test
    void never_selects_instant_death_when_safe_choice_exists() {
        Adventure adventure = adventureWith(normal(1), normal(2), instantDeath(3));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(2), choiceTo(3));
        Random random = new Random(0);

        for (int i = 0; i < 200; i++) {
            int idx = selector.select(choices, new SimulatedGameState(), random);
            assertThat(choices.get(idx).target())
                .as("selector must never pick INSTANT_DEATH target §3 when §2 is available")
                .isEqualTo(new SectionTarget(2));
        }
    }

    @Test
    void filters_multiple_instant_death_targets_leaving_single_safe_choice() {
        Adventure adventure = adventureWith(normal(1), normal(5), instantDeath(6), instantDeath(7));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(5), choiceTo(6), choiceTo(7));
        Random random = new Random(0);

        for (int i = 0; i < 100; i++) {
            int idx = selector.select(choices, new SimulatedGameState(), random);
            assertThat(choices.get(idx).target())
                .as("selector must always pick §5 — the only non-INSTANT_DEATH choice")
                .isEqualTo(new SectionTarget(5));
        }
    }

    // -------------------------------------------------------------------------
    // Forced death — all choices lead to INSTANT_DEATH
    // -------------------------------------------------------------------------

    @Test
    void accepts_instant_death_when_all_choices_are_lethal() {
        Adventure adventure = adventureWith(normal(1), instantDeath(2), instantDeath(3));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(2), choiceTo(3));
        Random random = new Random(0);

        // Must not throw and must return a valid index
        for (int i = 0; i < 50; i++) {
            int idx = selector.select(choices, new SimulatedGameState(), random);
            assertThat(idx)
                .as("when all choices are INSTANT_DEATH, selector must still return a valid index")
                .isBetween(0, choices.size() - 1);
        }
    }

    // -------------------------------------------------------------------------
    // Delegates to wrapped selector for safe choices
    // -------------------------------------------------------------------------

    @Test
    void delegates_to_wrapped_selector_when_all_choices_are_safe() {
        Adventure adventure = adventureWith(normal(1), normal(2), normal(3));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(2), choiceTo(3));
        Random random = new Random(0);

        boolean[] seen = new boolean[2];
        for (int i = 0; i < 200; i++) {
            seen[selector.select(choices, new SimulatedGameState(), random)] = true;
        }

        assertThat(seen)
            .as("when all choices are safe, both must be selectable (delegates to RandomChoiceSelector)")
            .doesNotContain(false);
    }

    // -------------------------------------------------------------------------
    // Single choice — always index 0
    // -------------------------------------------------------------------------

    @Test
    void single_safe_choice_returns_index_zero() {
        Adventure adventure = adventureWith(normal(1), normal(2));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());

        for (int i = 0; i < 20; i++) {
            assertThat(selector.select(List.of(choiceTo(2)), new SimulatedGameState(), new Random(i)))
                .as("single safe choice must always return index 0")
                .isZero();
        }
    }

    @Test
    void single_instant_death_choice_returns_index_zero() {
        Adventure adventure = adventureWith(normal(1), instantDeath(2));
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());

        assertThat(selector.select(List.of(choiceTo(2)), new SimulatedGameState(), new Random(0)))
            .as("forced single INSTANT_DEATH choice must return index 0")
            .isZero();
    }

    // -------------------------------------------------------------------------
    // VICTORY sections are not filtered
    // -------------------------------------------------------------------------

    @Test
    void victory_sections_are_not_filtered() {
        Adventure adventure = adventureWith(
            normal(1),
            new Section(2, "§2", List.of(), List.of(), SectionType.VICTORY, ScriptBlock.empty())
        );
        ChoiceSelector selector = new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
        List<Choice> choices = List.of(choiceTo(2));

        assertThat(selector.select(choices, new SimulatedGameState(), new Random(0)))
            .as("VICTORY sections must not be filtered out")
            .isZero();
    }
}
