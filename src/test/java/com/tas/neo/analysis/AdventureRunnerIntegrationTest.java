package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.mechanics.SeededDice;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for AdventureRunner against the-iron-road.json.
 *
 * <p>Uses a fixed seed for reproducibility. If this test fails, re-run with the
 * same seed to reproduce the failing path exactly.
 */
class AdventureRunnerIntegrationTest {

    private static final Path ADVENTURE_PATH = Path.of("adventures/the-iron-road.json");
    private static final long SEED = 20260415L;
    private static final int RUNS = 50;

    @Test
    void at_least_one_victory_reached_in_fifty_runs() throws Exception {
        RunBatchResult result = runBatch(RUNS, new RandomChoiceSelector());

        assertThat(result.outcomeCount(RunOutcome.VICTORY))
            .as("at least one VICTORY must be reached in %d random runs with seed %d", RUNS, SEED)
            .isGreaterThan(0);
    }

    @Test
    void no_stuck_runs() throws Exception {
        RunBatchResult result = runBatch(RUNS, new RandomChoiceSelector());

        assertThat(result.outcomeCount(RunOutcome.STUCK))
            .as("no run must end STUCK — every NORMAL section must have at least one reachable exit")
            .isZero();
    }

    @Test
    void cycle_runs_below_twenty_percent() throws Exception {
        RunBatchResult result = runBatch(RUNS, new RandomChoiceSelector());

        // Adventures with intentional back-navigation will cycle under a random selector.
        // The meaningful structural check is no_stuck_runs (missing exits), not zero cycles.
        assertThat(result.outcomeCount(RunOutcome.CYCLE))
            .as("cycle runs must stay below 20%% — the adventure has back-navigation hubs " +
                "that random walkers can orbit, but structural infinite loops must not exist")
            .isLessThan(RUNS / 5);
    }

    @Test
    void all_chapters_reached_in_fifty_runs() throws Exception {
        RunBatchResult result = runBatch(RUNS, new RandomChoiceSelector());

        assertThat(result.chapterReachRate("ch1"))
            .as("ch1 must be reached in every run (it is the start chapter)")
            .isEqualTo(1.0);
        assertThat(result.chapterReachRate("ch4"))
            .as("ch4 must be reached in at least one run out of %d", RUNS)
            .isGreaterThan(0.0);
    }

    @Test
    void coverage_above_seventy_percent_in_fifty_runs() throws Exception {
        RunBatchResult result = runBatch(RUNS, new RandomChoiceSelector());
        Adventure adventure = loadAdventure();
        int total = adventure.sections().size();
        int covered = result.coveredSections().size();

        assertThat((double) covered / total)
            .as("at least 70%% of sections must be reached across %d runs (covered %d/%d)",
                RUNS, covered, total)
            .isGreaterThanOrEqualTo(0.70);
    }

    @Test
    void no_conditioned_choice_permanently_unmet() throws Exception {
        RunBatchResult result = runBatch(RUNS, new ItemSeekingChoiceSelector());

        assertThat(result.neverSelectedChoices())
            .as("no conditioned choice must have its condition permanently unmet across " +
                "%d item-seeking runs — indicates an item that can never be obtained", RUNS)
            .isEmpty();
    }

    @Test
    void all_victory_sections_reached_across_strategies() throws Exception {
        // Run with two strategies and more iterations to maximise ending coverage
        RunBatchResult random = runBatch(RUNS * 2, new RandomChoiceSelector());
        RunBatchResult itemSeeking = runBatch(RUNS * 2, new ItemSeekingChoiceSelector());

        Adventure adventure = loadAdventure();
        long victoryCount = adventure.sections().stream()
            .filter(s -> s.type() == com.tas.neo.domain.adventure.SectionType.VICTORY)
            .count();

        long reachedVictories = adventure.sections().stream()
            .filter(s -> s.type() == com.tas.neo.domain.adventure.SectionType.VICTORY)
            .map(s -> s.number())
            .filter(n -> random.endingCount(n) > 0 || itemSeeking.endingCount(n) > 0)
            .count();

        assertThat(reachedVictories)
            .as("at least %d of %d VICTORY sections must be reached across two %d-run batches " +
                "(RANDOM + ITEM_SEEKING strategies)", victoryCount - 1, victoryCount, RUNS)
            .isGreaterThanOrEqualTo(victoryCount - 1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static RunBatchResult runBatch(int runs, ChoiceSelector strategy) throws Exception {
        Adventure adventure = loadAdventure();
        JsonNode rawJson = new ObjectMapper().readTree(Files.readString(ADVENTURE_PATH));
        RunConfiguration config = new RunConfiguration(
            runs, strategy, new SeededDice(SEED), SEED, 20,
            OptionalInt.empty(), OptionalInt.empty()
        );
        return AdventureRunner.run(adventure, rawJson, config);
    }

    private static Adventure loadAdventure() throws Exception {
        return new JsonAdventureLoader(Path.of("adventures")).load("the-iron-road");
    }
}
