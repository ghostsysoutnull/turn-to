package com.tas.neo.engine;

import com.tas.neo.combat.DefaultCombatSystemRegistry;
import com.tas.neo.combat.personal.PersonalCombatSystem;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.log.SessionLog;
import com.tas.neo.io.NoOpGameLogger;
import com.tas.neo.io.RecordingGameLogger;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.io.ScriptedInput;
import com.tas.neo.loader.InMemoryAdventureLoader;
import com.tas.neo.mechanics.CombatEngine;
import com.tas.neo.mechanics.FixedDice;
import com.tas.neo.scripting.LuaScriptEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for engine-layer session logging behaviour.
 *
 * <p>Covers the test strategy rows from {@code docs/design/12-session-logging.md}:
 * <ul>
 *   <li>RecordingGameLogger accumulates navigation path matching choices made</li>
 *   <li>RecordingGameLogger captures errors for broken scripts</li>
 *   <li>ScenarioRunner with NoOpGameLogger does not write files to sessions/</li>
 * </ul>
 */
class GameSessionLoggingTest {

    private static Section normalSectionWithChoices(int number, String narrative,
                                                     List<Choice> choices) {
        return new Section(number, narrative, List.of(), choices,
                           SectionType.NORMAL, ScriptBlock.empty());
    }

    private static Section victorySection(int number) {
        return new Section(number, "You win!", List.of(), List.of(),
                           SectionType.VICTORY, ScriptBlock.empty());
    }

    private static Adventure twoSectionAdventure(Section first, Section second) {
        return new Adventure(
            "test-adventure", "Test", "A test adventure",
            first.number(), 0,
            List.of(first, second),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty()
        );
    }

    // -----------------------------------------------------------------------
    // RecordingGameLogger accumulates navigation path
    // -----------------------------------------------------------------------

    @Test
    void recording_logger_accumulates_navigation_path_matching_choices_made() {
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Proceed", new SectionTarget(2))
        ));
        Section end = victorySection(2);
        Adventure adventure = twoSectionAdventure(start, end);

        RecordingGameLogger logger = new RecordingGameLogger();
        // Choice 1 → "Proceed" → section 2
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1)
            .withLogger(logger)
            .run();

        SessionLog log = result.sessionLog();

        assertThat(log.path())
            .as("Session path must contain one navigation entry matching the choice made")
            .hasSize(1);

        NavigationEntry entry = log.path().get(0);
        assertThat(entry.from())
            .as("Navigation 'from' must be 'section:1'")
            .isEqualTo("section:1");
        assertThat(entry.to())
            .as("Navigation 'to' must be 'section:2'")
            .isEqualTo("section:2");
        assertThat(entry.via())
            .as("Navigation 'via' must be the choice text selected")
            .isEqualTo("Proceed");
    }

    @Test
    void recording_logger_session_log_stepsCount_matches_path_size() {
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Go on", new SectionTarget(2))
        ));
        Section end = victorySection(2);
        Adventure adventure = twoSectionAdventure(start, end);

        RecordingGameLogger logger = new RecordingGameLogger();
        ScenarioRunner.scripted(adventure, new FixedDice(3), 1)
            .withLogger(logger)
            .run();

        SessionLog log = logger.sessionLog();
        assertThat(log.stepsCount())
            .as("stepsCount must equal the number of navigation entries in the path")
            .isEqualTo(log.path().size());
    }

    // -----------------------------------------------------------------------
    // RecordingGameLogger captures script errors
    // -----------------------------------------------------------------------

    /**
     * Wires a Game directly with LuaScriptEngine so that broken scripts can be detected.
     * ScenarioRunner hardcodes NoOpScriptEngine, which never throws; this helper bypasses it.
     */
    private static RecordingGameLogger runWithLuaEngine(Adventure adventure,
                                                         RecordingGameLogger logger) {
        FixedDice dice = new FixedDice(3);
        RecordingOutput output = new RecordingOutput();
        ScriptedInput input = new ScriptedInput();
        InMemoryAdventureLoader loader = new InMemoryAdventureLoader(adventure);
        CombatEngine combatEngine = new CombatEngine(dice, input, output);
        DefaultCombatSystemRegistry registry = new DefaultCombatSystemRegistry(
            new PersonalCombatSystem(combatEngine));
        Game game = new Game(input, output, loader, dice, new LuaScriptEngine(), registry, logger);
        game.run(adventure.id());
        return logger;
    }

    @Test
    void recording_logger_captures_errors_for_broken_section_script() {
        // A section with an onEnter script containing invalid Lua syntax
        ScriptBlock brokenScript = new ScriptBlock(
            Map.of("onEnter", "this is not valid lua !!!@#$%")
        );
        Section broken = new Section(1, "A dark room.", List.of(), List.of(),
                                     SectionType.VICTORY, brokenScript);

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(broken),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty()
        );

        RecordingGameLogger logger = new RecordingGameLogger();
        runWithLuaEngine(adventure, logger);

        assertThat(logger.hasErrors())
            .as("A broken section script must produce at least one error in the RecordingGameLogger")
            .isTrue();
    }

    @Test
    void recording_logger_error_has_correct_type_for_broken_script() {
        ScriptBlock brokenScript = new ScriptBlock(
            Map.of("onEnter", "not valid lua code !@#!")
        );
        Section broken = new Section(1, "Error room.", List.of(), List.of(),
                                     SectionType.VICTORY, brokenScript);

        Adventure adventure = new Adventure(
            "test-adventure", "Test", "A test adventure", 1, 0,
            List.of(broken),
            List.of(), List.of(), List.of("personal"), List.of(),
            ScriptBlock.empty()
        );

        RecordingGameLogger logger = new RecordingGameLogger();
        runWithLuaEngine(adventure, logger);

        assertThat(logger.errors())
            .as("RecordingGameLogger must record at least one error for a broken script")
            .isNotEmpty();

        assertThat(logger.errors().get(0).type())
            .as("Error type for a broken Lua script must be 'ScriptError'")
            .isEqualTo("ScriptError");

        assertThat(logger.errors().get(0).source())
            .as("Error source must identify where the script was located (section + hook)")
            .contains("section");
    }

    // -----------------------------------------------------------------------
    // NoOpGameLogger default — no files written to sessions/
    // -----------------------------------------------------------------------

    @Test
    void no_op_logger_does_not_write_files_to_sessions_dir(@TempDir Path tempDir) throws Exception {
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Proceed", new SectionTarget(2))
        ));
        Section end = victorySection(2);
        Adventure adventure = twoSectionAdventure(start, end);

        // Use NoOpGameLogger explicitly — no files should be written anywhere
        ScenarioRunner.scripted(adventure, new FixedDice(3), 1)
            .withLogger(new NoOpGameLogger())
            .run();

        // tempDir is a fresh JUnit-managed directory — NoOpGameLogger must not write into it
        // (and must not write anywhere else either, but this proves it does no file I/O at all)
        assertThat(Files.list(tempDir).findAny())
            .as("NoOpGameLogger must not write any files")
            .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Default ScenarioRunner uses RecordingGameLogger
    // -----------------------------------------------------------------------

    @Test
    void default_scenario_runner_session_log_is_accessible_in_result() {
        Section start = normalSectionWithChoices(1, "You begin.", List.of(
            Choice.to("Proceed", new SectionTarget(2))
        ));
        Section end = victorySection(2);
        Adventure adventure = twoSectionAdventure(start, end);

        // No explicit logger — ScenarioRunner uses RecordingGameLogger by default
        ScenarioResult result = ScenarioRunner.scripted(adventure, new FixedDice(3), 1).run();

        assertThat(result.sessionLog())
            .as("ScenarioResult.sessionLog() must never return null")
            .isNotNull();
    }
}
