package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.GridTarget;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SystemChoiceTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathFollowingInputTest {

    // -----------------------------------------------------------------------
    // resolveChoice — core static method, tested in isolation
    // -----------------------------------------------------------------------

    @Test
    void resolveChoice_returns_one_based_index_of_matching_section_target() {
        List<Choice> choices = List.of(
            Choice.to("Go north", new SectionTarget(10)),
            Choice.to("Go south", new SectionTarget(20))
        );

        assertThat(PathFollowingInput.resolveChoice(choices, 10)).isEqualTo(1);
        assertThat(PathFollowingInput.resolveChoice(choices, 20)).isEqualTo(2);
    }

    @Test
    void resolveChoice_skips_system_choices_when_searching_for_section() {
        List<Choice> choices = List.of(
            Choice.to("Eat a provision", new SystemChoiceTarget("eat")),
            Choice.to("Check inventory", new SystemChoiceTarget("inventory")),
            Choice.to("Head north",      new SectionTarget(5)),
            Choice.to("Quit",            new SystemChoiceTarget("quit"))
        );

        assertThat(PathFollowingInput.resolveChoice(choices, 5))
            .as("section choice is at index 3 (1-based), past two system choices")
            .isEqualTo(3);
    }

    @Test
    void resolveChoice_skips_grid_targets_when_searching_for_section() {
        List<Choice> choices = List.of(
            Choice.to("Enter the dungeon", new GridTarget("dungeon", "1,1,0")),
            Choice.to("Continue on road",  new SectionTarget(7))
        );

        assertThat(PathFollowingInput.resolveChoice(choices, 7)).isEqualTo(2);
    }

    @Test
    void resolveChoice_throws_with_diagnostics_when_no_choice_leads_to_target() {
        List<Choice> choices = List.of(
            Choice.to("Go left",  new SectionTarget(10)),
            Choice.to("Go right", new SectionTarget(20))
        );

        assertThatThrownBy(() -> PathFollowingInput.resolveChoice(choices, 99))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("99");
    }

    @Test
    void resolveChoice_throws_on_empty_choice_list() {
        assertThatThrownBy(() -> PathFollowingInput.resolveChoice(List.of(), 5))
            .isInstanceOf(IllegalStateException.class);
    }

    // -----------------------------------------------------------------------
    // PathFollowingInput — full behaviour
    // -----------------------------------------------------------------------

    @Test
    void readChoice_advances_through_path_in_order() {
        List<Choice> step1 = List.of(
            Choice.to("A", new SectionTarget(2)),
            Choice.to("B", new SectionTarget(3))
        );
        List<Choice> step2 = List.of(
            Choice.to("X", new SectionTarget(4)),
            Choice.to("Y", new SectionTarget(5))
        );

        PathFollowingInput input = new PathFollowingInput(List.of(3, 5));

        assertThat(input.readChoice(step1)).as("first call: navigate to §3").isEqualTo(2);
        assertThat(input.readChoice(step2)).as("second call: navigate to §5").isEqualTo(2);
    }

    @Test
    void readChoice_throws_when_path_exhausted() {
        PathFollowingInput input = new PathFollowingInput(List.of(2));
        input.readChoice(List.of(Choice.to("Go", new SectionTarget(2))));

        assertThatThrownBy(() -> input.readChoice(List.of(Choice.to("Go", new SectionTarget(3)))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exhausted");
    }

    @Test
    void readYesNo_always_declines() {
        PathFollowingInput input = new PathFollowingInput(List.of());
        assertThat(input.readYesNo("Test your luck?")).isFalse();
    }

    @Test
    void waitForEnter_does_not_throw() {
        PathFollowingInput input = new PathFollowingInput(List.of());
        input.waitForEnter();
    }

    // -----------------------------------------------------------------------
    // PlaybackScenario parsing
    // -----------------------------------------------------------------------

    @Test
    void scenario_parses_comma_separated_path() {
        PlaybackScenario scenario = PlaybackScenario.of("the-iron-road", "2,4,7");
        assertThat(scenario.adventureId()).isEqualTo("the-iron-road");
        assertThat(scenario.path()).containsExactly(2, 4, 7);
    }

    @Test
    void scenario_path_is_immutable() {
        PlaybackScenario scenario = PlaybackScenario.of("test", "1,2,3");
        assertThatThrownBy(() -> scenario.path().add(99))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
