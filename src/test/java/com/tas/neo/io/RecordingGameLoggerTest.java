package com.tas.neo.io;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.log.PlayerSnapshot;
import com.tas.neo.domain.log.SessionLog;
import com.tas.neo.domain.party.PartyMemberDefinition;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RecordingGameLogger}.
 *
 * <p>Covers two rows from the test strategy table in
 * {@code docs/design/12-session-logging.md}:
 * <ul>
 *   <li>RecordingGameLogger accumulates: sessionLog().path() matches navigation entries logged</li>
 *   <li>RecordingGameLogger captures errors: hasErrors() == true, error source and type correct</li>
 * </ul>
 */
class RecordingGameLoggerTest {

    // -------------------------------------------------------------------------
    // Direct accumulation tests (no ScenarioRunner needed)
    // -------------------------------------------------------------------------

    @Test
    void sessionLog_path_contains_all_logged_navigation_entries() {
        RecordingGameLogger logger = new RecordingGameLogger();
        NavigationEntry first  = new NavigationEntry("section:1", "section:2", "Enter the mountain");
        NavigationEntry second = new NavigationEntry("section:2", "section:3", "Search the body");

        logger.logNavigation(first);
        logger.logNavigation(second);

        SessionLog log = logger.sessionLog();
        assertThat(log.path())
            .as("sessionLog().path() must contain both logged navigation entries in order")
            .containsExactly(first, second);
    }

    @Test
    void sessionLog_path_is_empty_when_no_navigation_logged() {
        RecordingGameLogger logger = new RecordingGameLogger();

        assertThat(logger.sessionLog().path())
            .as("sessionLog().path() must be empty when no navigation was logged")
            .isEmpty();
    }

    @Test
    void sessionLog_stepsCount_equals_number_of_navigation_entries() {
        RecordingGameLogger logger = new RecordingGameLogger();
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Go"));
        logger.logNavigation(new NavigationEntry("section:2", "section:3", "Go again"));

        assertThat(logger.sessionLog().stepsCount())
            .as("sessionLog().stepsCount() must equal the number of logged navigation entries")
            .isEqualTo(2);
    }

    @Test
    void sessionLog_path_matches_section_location_strings() {
        RecordingGameLogger logger = new RecordingGameLogger();
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Go"));

        NavigationEntry recorded = logger.sessionLog().path().get(0);
        assertThat(recorded.from())
            .as("NavigationEntry.from must be 'section:1' as logged")
            .isEqualTo("section:1");
        assertThat(recorded.to())
            .as("NavigationEntry.to must be 'section:2' as logged")
            .isEqualTo("section:2");
        assertThat(recorded.via())
            .as("NavigationEntry.via must be 'Go' as logged")
            .isEqualTo("Go");
    }

    @Test
    void sessionLog_path_matches_grid_location_strings() {
        RecordingGameLogger logger = new RecordingGameLogger();
        logger.logNavigation(new NavigationEntry(
            "grid:dungeon-level-1:0,0,0",
            "grid:dungeon-level-1:1,0,0",
            "Go east"
        ));

        NavigationEntry recorded = logger.sessionLog().path().get(0);
        assertThat(recorded.from())
            .as("NavigationEntry.from must use grid location format for grid cells")
            .isEqualTo("grid:dungeon-level-1:0,0,0");
        assertThat(recorded.to())
            .as("NavigationEntry.to must use grid location format for grid cells")
            .isEqualTo("grid:dungeon-level-1:1,0,0");
    }

    // -------------------------------------------------------------------------
    // Error accumulation tests
    // -------------------------------------------------------------------------

    @Test
    void hasErrors_returns_false_when_no_errors_logged() {
        RecordingGameLogger logger = new RecordingGameLogger();

        assertThat(logger.hasErrors())
            .as("hasErrors must return false when no errors have been logged")
            .isFalse();
    }

    @Test
    void hasErrors_returns_true_after_logError_called() {
        RecordingGameLogger logger = new RecordingGameLogger();
        PlayerSnapshot snapshot = new PlayerSnapshot("section:42", 8, 9, 5, 3, List.of("Iron Key"));
        GameError error = new GameError("section-42-onEnter", "ScriptError",
                                        "attempt to index nil", snapshot);

        logger.logError(error);

        assertThat(logger.hasErrors())
            .as("hasErrors must return true after at least one error is logged")
            .isTrue();
    }

    @Test
    void errors_list_contains_logged_error_with_correct_source_and_type() {
        RecordingGameLogger logger = new RecordingGameLogger();
        PlayerSnapshot snapshot = new PlayerSnapshot("section:42", 8, 9, 5, 3, List.of());
        GameError error = new GameError("section-42-onEnter", "ScriptError",
                                        "nil error", snapshot);

        logger.logError(error);

        assertThat(logger.errors())
            .as("errors() must contain the logged error")
            .hasSize(1);
        GameError recorded = logger.errors().get(0);
        assertThat(recorded.source())
            .as("error source must match what was logged")
            .isEqualTo("section-42-onEnter");
        assertThat(recorded.type())
            .as("error type must match what was logged")
            .isEqualTo("ScriptError");
    }

    @Test
    void errors_list_contains_navigation_error_type() {
        RecordingGameLogger logger = new RecordingGameLogger();
        PlayerSnapshot snapshot = new PlayerSnapshot("section:5", 10, 10, 10, 0, List.of());
        GameError error = new GameError("section-5-choices", "NavigationError",
                                        "no such section: 999", snapshot);

        logger.logError(error);

        assertThat(logger.errors().get(0).type())
            .as("errors() must record NavigationError type correctly")
            .isEqualTo("NavigationError");
    }

    // -------------------------------------------------------------------------
    // After close: no further accumulation
    // -------------------------------------------------------------------------

    @Test
    void entries_logged_after_close_are_not_accumulated() {
        RecordingGameLogger logger = new RecordingGameLogger();
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Go"));
        logger.close();

        // Any calls after close must be no-ops per the GameLogger contract.
        logger.logNavigation(new NavigationEntry("section:2", "section:3", "Go again"));

        assertThat(logger.sessionLog().path())
            .as("entries logged after close() must not appear in sessionLog().path()")
            .hasSize(1);
    }

    // -------------------------------------------------------------------------
    // ScenarioRunner integration: path matches choices made
    // -------------------------------------------------------------------------

    @Test
    void scenarioRunner_with_recording_logger_path_matches_navigation_steps() {
        // A simple 3-section chain: 1 → 2 → 3 (VICTORY)
        // ScenarioRunner in scripted mode with choice 1 at each step.
        Section s1 = new Section(1, "Start.", List.of(),
            List.of(Choice.to("Continue", new SectionTarget(2))),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "Middle.", List.of(),
            List.of(Choice.to("Finish", new SectionTarget(3))),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s3 = new Section(3, "You win!", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());

        Adventure adventure = new Adventure(
            "test-logging", "Test Logging", "A logging test.",
            1, 0,
            List.of(s1, s2, s3),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScriptBlock.empty(), Map.of()
        );

        RecordingGameLogger logger = new RecordingGameLogger();
        ScenarioResult result = ScenarioRunner
            .scripted(adventure, new FixedDice(3), 1, 1)
            .withLogger(logger)
            .run();

        assertThat(result.victory())
            .as("scenario must reach victory to ensure navigation was logged")
            .isTrue();

        List<NavigationEntry> path = logger.sessionLog().path();
        assertThat(path)
            .as("path must contain exactly 2 navigation entries for a 3-section chain")
            .hasSize(2);
        assertThat(path.get(0).from())
            .as("first navigation entry must depart from section:1")
            .isEqualTo("section:1");
        assertThat(path.get(0).to())
            .as("first navigation entry must arrive at section:2")
            .isEqualTo("section:2");
        assertThat(path.get(1).from())
            .as("second navigation entry must depart from section:2")
            .isEqualTo("section:2");
        assertThat(path.get(1).to())
            .as("second navigation entry must arrive at section:3")
            .isEqualTo("section:3");
    }
}
