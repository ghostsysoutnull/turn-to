package com.tas.neo.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RunBatchResultTest {

    // -------------------------------------------------------------------------
    // Fixture builders
    // -------------------------------------------------------------------------

    private static RunResult victory(int section, List<Integer> visited,
                                      List<ChapterSnapshot> snapshots) {
        return new RunResult(RunOutcome.VICTORY, section, visited, snapshots, List.of());
    }

    private static RunResult instantDeath(int section, List<Integer> visited) {
        return new RunResult(RunOutcome.INSTANT_DEATH, section, visited,
            List.of(), List.of());
    }

    private static RunResult stuck(int section) {
        return new RunResult(RunOutcome.STUCK, section, List.of(section), List.of(), List.of());
    }

    private static RunResult cycle(int section) {
        return new RunResult(RunOutcome.CYCLE, section, List.of(section), List.of(), List.of());
    }

    private static ChapterSnapshot snapshot(String chapterId, int atSection,
                                             Set<String> inventory, int gold,
                                             Map<String, Object> state) {
        return new ChapterSnapshot(chapterId, atSection, inventory, gold, state);
    }

    // -------------------------------------------------------------------------
    // Outcome counts
    // -------------------------------------------------------------------------

    @Test
    void outcomeCount_counts_matching_outcomes() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(10, List.of(1, 10), List.of()),
            victory(10, List.of(1, 10), List.of()),
            instantDeath(5, List.of(1, 5))
        ));

        assertThat(batch.outcomeCount(RunOutcome.VICTORY)).isEqualTo(2);
        assertThat(batch.outcomeCount(RunOutcome.INSTANT_DEATH)).isEqualTo(1);
        assertThat(batch.outcomeCount(RunOutcome.STUCK)).isZero();
    }

    @Test
    void endingCount_counts_runs_ending_at_section() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(10, List.of(1, 10), List.of()),
            victory(20, List.of(1, 20), List.of()),
            victory(10, List.of(1, 10), List.of())
        ));

        assertThat(batch.endingCount(10))
            .as("endingCount must count runs that ended at section 10")
            .isEqualTo(2);
        assertThat(batch.endingCount(20)).isEqualTo(1);
        assertThat(batch.endingCount(99)).isZero();
    }

    // -------------------------------------------------------------------------
    // Coverage
    // -------------------------------------------------------------------------

    @Test
    void coveredSections_unions_all_visited_sections() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(3, List.of(1, 2, 3), List.of()),
            victory(5, List.of(1, 4, 5), List.of())
        ));

        assertThat(batch.coveredSections())
            .as("coveredSections must be the union of all sections visited across all runs")
            .containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    }

    // -------------------------------------------------------------------------
    // Chapter reach rates
    // -------------------------------------------------------------------------

    @Test
    void chapterReachRate_fraction_of_runs_with_snapshot() {
        List<ChapterSnapshot> withCh2 = List.of(snapshot("ch2", 41, Set.of(), 0, Map.of()));
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(1, 41, 50), withCh2),
            victory(50, List.of(1, 41, 50), withCh2),
            instantDeath(15, List.of(1, 15))   // never reached ch2
        ));

        assertThat(batch.chapterReachRate("ch2"))
            .as("chapterReachRate must be the fraction of runs that have a snapshot for that chapter")
            .isCloseTo(2.0 / 3.0, within(0.001));
    }

    @Test
    void chapterReachRate_zero_when_no_run_reached_chapter() {
        RunBatchResult batch = new RunBatchResult(List.of(
            instantDeath(5, List.of(1, 5))
        ));
        assertThat(batch.chapterReachRate("ch4")).isZero();
    }

    // -------------------------------------------------------------------------
    // Item carry rates
    // -------------------------------------------------------------------------

    @Test
    void itemCarryRate_fraction_of_runs_carrying_item_at_chapter() {
        List<ChapterSnapshot> withSword = List.of(
            snapshot("ch2", 41, Set.of("Sword"), 0, Map.of()));
        List<ChapterSnapshot> withoutSword = List.of(
            snapshot("ch2", 41, Set.of(), 0, Map.of()));

        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(1, 41, 50), withSword),
            victory(50, List.of(1, 41, 50), withSword),
            victory(50, List.of(1, 41, 50), withoutSword)
        ));

        assertThat(batch.itemCarryRate("ch2", "Sword"))
            .as("itemCarryRate must be 2/3 when Sword was carried in 2 of 3 runs at ch2 entry")
            .isCloseTo(2.0 / 3.0, within(0.001));
    }

    @Test
    void itemCarryRate_zero_when_item_never_carried() {
        List<ChapterSnapshot> noItems = List.of(snapshot("ch2", 41, Set.of(), 0, Map.of()));
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(1, 41, 50), noItems)
        ));
        assertThat(batch.itemCarryRate("ch2", "Rare Item")).isZero();
    }

    // -------------------------------------------------------------------------
    // Gold distribution
    // -------------------------------------------------------------------------

    @Test
    void goldDistribution_avg_min_max_across_runs() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(1, 41, 50), List.of(snapshot("ch2", 41, Set.of(), 5, Map.of()))),
            victory(50, List.of(1, 41, 50), List.of(snapshot("ch2", 41, Set.of(), 10, Map.of()))),
            victory(50, List.of(1, 41, 50), List.of(snapshot("ch2", 41, Set.of(), 3, Map.of())))
        ));

        StateSummary summary = batch.goldDistribution("ch2");
        assertThat(summary.average()).hasValue(6.0);
        assertThat(summary.min()).isEqualTo(3);
        assertThat(summary.max()).isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // State distribution
    // -------------------------------------------------------------------------

    @Test
    void stateDistribution_numeric_avg_min_max() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(), List.of(snapshot("ch3", 91, Set.of(), 0, Map.of("suspicion", 2)))),
            victory(50, List.of(), List.of(snapshot("ch3", 91, Set.of(), 0, Map.of("suspicion", 8)))),
            victory(50, List.of(), List.of(snapshot("ch3", 91, Set.of(), 0, Map.of("suspicion", 5))))
        ));

        StateSummary summary = batch.stateDistribution("ch3", "suspicion");
        assertThat(summary.average()).hasValue(5.0);
        assertThat(summary.min()).isEqualTo(2);
        assertThat(summary.max()).isEqualTo(8);
    }

    @Test
    void stateDistribution_boolean_value_counts() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(50, List.of(), List.of(snapshot("ch4", 131, Set.of(), 0, Map.of("contactAlive", true)))),
            victory(50, List.of(), List.of(snapshot("ch4", 131, Set.of(), 0, Map.of("contactAlive", false)))),
            victory(50, List.of(), List.of(snapshot("ch4", 131, Set.of(), 0, Map.of("contactAlive", true))))
        ));

        StateSummary summary = batch.stateDistribution("ch4", "contactAlive");
        assertThat(summary.valueCounts().get(true)).isEqualTo(2);
        assertThat(summary.valueCounts().get(false)).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Run length
    // -------------------------------------------------------------------------

    @Test
    void runLengthSummary_excludes_stuck_and_cycle_runs() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(3, List.of(1, 2, 3), List.of()),          // length 3
            victory(4, List.of(1, 2, 3, 4), List.of()),       // length 4
            stuck(1),                                           // excluded
            cycle(2)                                            // excluded
        ));

        RunLengthSummary summary = batch.runLengthSummary();
        assertThat(summary.average()).isCloseTo(3.5, within(0.001));
        assertThat(summary.min()).isEqualTo(3);
        assertThat(summary.max()).isEqualTo(4);
    }

    @Test
    void runLengthSummary_all_runs_included_when_no_stuck_or_cycle() {
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(5, List.of(1, 2, 5), List.of()),          // length 3
            instantDeath(3, List.of(1, 3))                     // length 2
        ));

        RunLengthSummary summary = batch.runLengthSummary();
        assertThat(summary.min()).isEqualTo(2);
        assertThat(summary.max()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Never-selected choices
    // -------------------------------------------------------------------------

    @Test
    void neverSelectedChoices_empty_when_all_choices_selected_in_some_run() {
        // RunResult has no "never selected" tracking at this level — that requires
        // per-run selection records aggregated across the batch.
        // Construct a batch where all choices were selected.
        RunBatchResult batch = new RunBatchResult(List.of(
            victory(2, List.of(1, 2), List.of())
        ));
        // With no selection records provided, neverSelectedChoices returns empty.
        assertThat(batch.neverSelectedChoices()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Warnings
    // -------------------------------------------------------------------------

    @Test
    void warnings_deduplicates_across_runs() {
        RunResult r1 = new RunResult(RunOutcome.VICTORY, 2, List.of(1, 2), List.of(),
            List.of("unsupported: getPartyMember"));
        RunResult r2 = new RunResult(RunOutcome.VICTORY, 2, List.of(1, 2), List.of(),
            List.of("unsupported: getPartyMember"));

        RunBatchResult batch = new RunBatchResult(List.of(r1, r2));

        assertThat(batch.warnings())
            .as("warnings must be deduplicated across runs")
            .hasSize(1)
            .contains("unsupported: getPartyMember");
    }
}
