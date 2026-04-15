package com.tas.neo.mechanics;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.log.SessionLog;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SeededDice}.
 *
 * <p>Covers two rows from the test strategy table in
 * {@code docs/design/12-session-logging.md}:
 * <ul>
 *   <li>SeededDice reproducibility: two ScenarioRunner.random() runs with the same seed
 *       produce identical navigation paths</li>
 *   <li>Random run terminates: ScenarioRunner.random() with SeededDice reaches a terminal state</li>
 * </ul>
 */
class SeededDiceTest {

    /**
     * A branching adventure where random choice selection will take different paths.
     * Section 1 branches to section 2 or 3; both lead to a VICTORY.
     */
    private static Adventure branchingAdventure() {
        Section s1 = new Section(1, "You are at a fork.", List.of(),
            List.of(
                Choice.to("Go left",  new SectionTarget(2)),
                Choice.to("Go right", new SectionTarget(3))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "Left path leads to victory.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        Section s3 = new Section(3, "Right path leads to victory.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure(
            "branching-test", "Branching Test", "A branching test adventure.",
            1, 0,
            List.of(s1, s2, s3),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScriptBlock.empty()
        );
    }

    /**
     * A longer branching adventure to give the random runner more decisions to make,
     * ensuring the navigation path is non-trivial and reproducibility is meaningful.
     */
    private static Adventure deepBranchingAdventure() {
        Section s1 = new Section(1, "Start.", List.of(),
            List.of(
                Choice.to("Path A", new SectionTarget(2)),
                Choice.to("Path B", new SectionTarget(3))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "Path A continues.", List.of(),
            List.of(
                Choice.to("Left",  new SectionTarget(4)),
                Choice.to("Right", new SectionTarget(5))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s3 = new Section(3, "Path B continues.", List.of(),
            List.of(
                Choice.to("Left",  new SectionTarget(6)),
                Choice.to("Right", new SectionTarget(7))
            ),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s4 = new Section(4, "Victory via A-left.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        Section s5 = new Section(5, "Victory via A-right.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        Section s6 = new Section(6, "Victory via B-left.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        Section s7 = new Section(7, "Victory via B-right.", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure(
            "deep-branching-test", "Deep Branching Test", "A deep branching test.",
            1, 0,
            List.of(s1, s2, s3, s4, s5, s6, s7),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScriptBlock.empty()
        );
    }

    // -------------------------------------------------------------------------
    // Reproducibility: same seed → same path
    // -------------------------------------------------------------------------

    @Test
    void two_runs_with_same_seed_produce_identical_navigation_paths() {
        Adventure adventure = deepBranchingAdventure();
        long seed = 42L;

        ScenarioResult run1 = ScenarioRunner
            .random(adventure, new SeededDice(seed))
            .run();
        ScenarioResult run2 = ScenarioRunner
            .random(adventure, new SeededDice(seed))
            .run();

        List<NavigationEntry> path1 = run1.sessionLog().path();
        List<NavigationEntry> path2 = run2.sessionLog().path();

        assertThat(path1)
            .as("two ScenarioRunner.random() runs with the same seed must produce identical paths")
            .isEqualTo(path2);
    }

    @Test
    void two_runs_with_different_seeds_may_differ_in_path() {
        // This is a probabilistic test: with a 2-way branch at each step over 2 levels,
        // runs with different seeds are likely (though not guaranteed) to take different paths.
        // We assert that the test infrastructure runs without errors; the identity assertion
        // for same-seed is the definitive reproducibility check above.
        Adventure adventure = deepBranchingAdventure();

        ScenarioResult run1 = ScenarioRunner
            .random(adventure, new SeededDice(100L))
            .run();
        ScenarioResult run2 = ScenarioRunner
            .random(adventure, new SeededDice(999L))
            .run();

        // Both runs must reach a terminal state regardless of path taken.
        assertThat(run1.victory() || run1.gameOver())
            .as("first run must reach a terminal state")
            .isTrue();
        assertThat(run2.victory() || run2.gameOver())
            .as("second run must reach a terminal state")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // Random run terminates: SeededDice + ScenarioRunner.random() → terminal state
    // -------------------------------------------------------------------------

    @Test
    void random_run_with_seeded_dice_reaches_terminal_state() {
        Adventure adventure = branchingAdventure();

        ScenarioResult result = ScenarioRunner
            .random(adventure, new SeededDice(12345L))
            .run();

        assertThat(result.victory() || result.gameOver())
            .as("ScenarioRunner.random() with SeededDice must reach a terminal state")
            .isTrue();
    }

    @Test
    void seeded_dice_produces_values_in_valid_range_for_sides() {
        SeededDice dice = new SeededDice(7L);

        for (int i = 0; i < 100; i++) {
            int roll = dice.roll(6);
            assertThat(roll)
                .as("SeededDice.roll(6) must return a value in [1, 6]")
                .isBetween(1, 6);
        }
    }

    @Test
    void seeded_dice_same_seed_produces_same_sequence_of_rolls() {
        SeededDice d1 = new SeededDice(99L);
        SeededDice d2 = new SeededDice(99L);

        int[] rolls1 = new int[10];
        int[] rolls2 = new int[10];
        for (int i = 0; i < 10; i++) {
            rolls1[i] = d1.roll(6);
            rolls2[i] = d2.roll(6);
        }

        assertThat(rolls1)
            .as("SeededDice with the same seed must produce the same roll sequence")
            .isEqualTo(rolls2);
    }
}
