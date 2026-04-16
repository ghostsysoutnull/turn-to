package com.tas.neo.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.engine.ScenarioResult;
import com.tas.neo.engine.ScenarioRunner;
import com.tas.neo.mechanics.FixedDice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileGameLogger}.
 *
 * <p>Covers the FileGameLogger rows from the test strategy table in
 * {@code docs/design/12-session-logging.md}:
 * <ul>
 *   <li>Both .txt and .json log files are created in the sessions directory on close()</li>
 *   <li>JSON parses correctly and contains expected fields (adventureId, result, path, etc.)</li>
 * </ul>
 *
 * <p>Uses JUnit 5's {@code @TempDir} to avoid any disk state leaking between tests.
 * No files are written to {@code sessions/} during this test.
 */
class FileGameLoggerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Adventure simpleVictoryAdventure(String id) {
        Section s1 = new Section(1, "Start.", List.of(),
            List.of(Choice.to("Continue", new SectionTarget(2))),
            SectionType.NORMAL, ScriptBlock.empty());
        Section s2 = new Section(2, "You win!", List.of(), List.of(),
            SectionType.VICTORY, ScriptBlock.empty());
        return new Adventure(
            id, "Test Adventure", "A test.",
            1, 0,
            List.of(s1, s2),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScriptBlock.empty()
        );
    }

    // -------------------------------------------------------------------------
    // Both files are created on close()
    // -------------------------------------------------------------------------

    @Test
    void close_creates_txt_log_file_in_sessions_dir(@TempDir Path sessionsDir) throws IOException {
        String adventureId = "the-warlock-of-firetop-mountain";
        FileGameLogger logger = new FileGameLogger(adventureId, sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Enter the mountain"));
        logger.close();

        boolean hasTxtFile = Files.list(sessionsDir)
            .anyMatch(p -> p.toString().endsWith(".txt"));
        assertThat(hasTxtFile)
            .as("FileGameLogger.close() must create a .txt log file in the sessions directory")
            .isTrue();
    }

    @Test
    void close_creates_json_log_file_in_sessions_dir(@TempDir Path sessionsDir) throws IOException {
        String adventureId = "the-warlock-of-firetop-mountain";
        FileGameLogger logger = new FileGameLogger(adventureId, sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Enter the mountain"));
        logger.close();

        boolean hasJsonFile = Files.list(sessionsDir)
            .anyMatch(p -> p.toString().endsWith(".json"));
        assertThat(hasJsonFile)
            .as("FileGameLogger.close() must create a .json log file in the sessions directory")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // JSON content: adventureId field
    // -------------------------------------------------------------------------

    @Test
    void json_log_contains_adventureId_field(@TempDir Path sessionsDir) throws IOException {
        String adventureId = "test-adventure-id";
        FileGameLogger logger = new FileGameLogger(adventureId, sessionsDir);
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.has("adventureId"))
            .as("JSON log must contain 'adventureId' field")
            .isTrue();
        assertThat(root.get("adventureId").asText())
            .as("JSON log 'adventureId' must match the adventure ID passed to the constructor")
            .isEqualTo(adventureId);
    }

    // -------------------------------------------------------------------------
    // JSON content: result field
    // -------------------------------------------------------------------------

    @Test
    void json_log_contains_result_field(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.has("result"))
            .as("JSON log must contain 'result' field")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // JSON content: stepsCount field
    // -------------------------------------------------------------------------

    @Test
    void json_log_contains_stepsCount_field(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Go"));
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.has("stepsCount"))
            .as("JSON log must contain 'stepsCount' field")
            .isTrue();
        assertThat(root.get("stepsCount").asInt())
            .as("JSON log 'stepsCount' must reflect logged navigation count")
            .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // JSON content: path array
    // -------------------------------------------------------------------------

    @Test
    void json_log_path_array_contains_logged_navigation_entries(@TempDir Path sessionsDir)
            throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Enter the mountain"));
        logger.logNavigation(new NavigationEntry("section:2", "section:3", "Search the body"));
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.has("path"))
            .as("JSON log must contain 'path' array")
            .isTrue();
        JsonNode path = root.get("path");
        assertThat(path.isArray())
            .as("JSON log 'path' must be an array")
            .isTrue();
        assertThat(path.size())
            .as("JSON log 'path' must contain 2 entries matching logged navigations")
            .isEqualTo(2);
    }

    @Test
    void json_log_path_entry_has_from_to_via_fields(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Entered the mountain"));
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        JsonNode entry = root.get("path").get(0);
        assertThat(entry.get("from").asText())
            .as("JSON path entry must have 'from' field equal to 'section:1'")
            .isEqualTo("section:1");
        assertThat(entry.get("to").asText())
            .as("JSON path entry must have 'to' field equal to 'section:2'")
            .isEqualTo("section:2");
        assertThat(entry.get("via").asText())
            .as("JSON path entry must have 'via' field equal to the choice text")
            .isEqualTo("Entered the mountain");
    }

    @Test
    void json_log_path_entry_has_grid_location_strings(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry(
            "grid:dungeon-level-1:0,0,0",
            "grid:dungeon-level-1:1,0,0",
            "Go east"
        ));
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        JsonNode entry = root.get("path").get(0);
        assertThat(entry.get("from").asText())
            .as("JSON path entry must preserve grid location format in 'from'")
            .isEqualTo("grid:dungeon-level-1:0,0,0");
        assertThat(entry.get("to").asText())
            .as("JSON path entry must preserve grid location format in 'to'")
            .isEqualTo("grid:dungeon-level-1:1,0,0");
    }

    // -------------------------------------------------------------------------
    // JSON content: errors array
    // -------------------------------------------------------------------------

    @Test
    void json_log_contains_errors_array(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.close();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.has("errors"))
            .as("JSON log must contain 'errors' array")
            .isTrue();
        assertThat(root.get("errors").isArray())
            .as("JSON log 'errors' must be an array")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // ScenarioRunner integration: files written after full run
    // -------------------------------------------------------------------------

    @Test
    void scenarioRunner_with_file_logger_creates_both_files(@TempDir Path sessionsDir)
            throws IOException {
        Adventure adventure = simpleVictoryAdventure("warlock-test");
        FileGameLogger logger = new FileGameLogger("warlock-test", sessionsDir);

        ScenarioResult result = ScenarioRunner
            .scripted(adventure, new FixedDice(3), 1)
            .withLogger(logger)
            .run();

        assertThat(result.victory())
            .as("scenario must reach victory so close() is called and files are written")
            .isTrue();

        long txtCount = Files.list(sessionsDir)
            .filter(p -> p.toString().endsWith(".txt"))
            .count();
        long jsonCount = Files.list(sessionsDir)
            .filter(p -> p.toString().endsWith(".json"))
            .count();

        assertThat(txtCount)
            .as("exactly one .txt file must exist in sessions dir after game end")
            .isEqualTo(1);
        assertThat(jsonCount)
            .as("exactly one .json file must exist in sessions dir after game end")
            .isEqualTo(1);
    }

    @Test
    void scenarioRunner_json_log_parses_correctly_and_adventureId_matches(@TempDir Path sessionsDir)
            throws IOException {
        Adventure adventure = simpleVictoryAdventure("firetop-mountain");
        FileGameLogger logger = new FileGameLogger("firetop-mountain", sessionsDir);

        ScenarioRunner
            .scripted(adventure, new FixedDice(3), 1)
            .withLogger(logger)
            .run();

        JsonNode root = readJsonLog(sessionsDir);
        assertThat(root.get("adventureId").asText())
            .as("JSON log adventureId must equal 'firetop-mountain'")
            .isEqualTo("firetop-mountain");
    }

    // -------------------------------------------------------------------------
    // Text log: section narrative appears in location header
    // -------------------------------------------------------------------------

    @Test
    void txt_log_section_header_includes_narrative(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logEvent(new OutputEvent.NarrativeShown("You stand before the entrance."));
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Enter the mountain"));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must include section number and narrative text in the location header")
            .contains("[Section 1] You stand before the entrance.");
    }

    @Test
    void txt_log_includes_departure_choice_line(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logEvent(new OutputEvent.NarrativeShown("You stand before the entrance."));
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Enter the mountain"));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must include departure choice and destination as an indented line")
            .contains("  → Enter the mountain  (→ Section 2)");
    }

    @Test
    void txt_log_grid_location_header_includes_narrative(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "grid:vault-dungeon:1,0,0", "Enter the vault"));
        logger.logEvent(new OutputEvent.NarrativeShown("Cold stone walls surround you."));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must include grid id and coordinates in the location header")
            .contains("[Grid: vault-dungeon (1,0,0)] Cold stone walls surround you.");
    }

    @Test
    void txt_log_victory_section_shows_result_text(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Finish"));
        logger.logEvent(new OutputEvent.VictoryShown("You have won!"));
        logger.setResult("VICTORY");
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must include VictoryShown text at the terminal section")
            .contains("VICTORY — You have won!");
    }

    @Test
    void txt_log_footer_contains_result_and_steps(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logNavigation(new NavigationEntry("section:1", "section:2", "Go"));
        logger.setResult("VICTORY");
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log footer must contain the result")
            .contains("Result:      VICTORY");
        assertThat(txt)
            .as("text log footer must contain the step count (navigations excluding START)")
            .contains("Steps:");
    }

    // -------------------------------------------------------------------------
    // Text log: item and stat events rendered in-step
    // -------------------------------------------------------------------------

    @Test
    void txt_log_shows_item_gain_line_in_step(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logEvent(new OutputEvent.ItemGained("Guard's Pass"));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must show ITEM_GAIN line for an ItemGained event")
            .contains("  ITEM_GAIN: Guard's Pass");
    }

    @Test
    void txt_log_shows_item_loss_line_in_step(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logEvent(new OutputEvent.ItemLost("Torch"));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must show ITEM_LOSS line for an ItemLost event")
            .contains("  ITEM_LOSS: Torch");
    }

    @Test
    void txt_log_shows_stat_change_line_in_step(@TempDir Path sessionsDir) throws IOException {
        FileGameLogger logger = new FileGameLogger("test-adv", sessionsDir);
        logger.logNavigation(new NavigationEntry("START", "section:1", "START"));
        logger.logEvent(new OutputEvent.StatChanged("STAMINA", -4, 14));
        logger.close();

        String txt = readTxtLog(sessionsDir);
        assertThat(txt)
            .as("text log must show STAT_CHANGE line with delta and new value for a StatChanged event")
            .contains("  STAT_CHANGE: STAMINA -4 (now 14)");
    }

    // -------------------------------------------------------------------------
    // Helper: read and parse the single .json file from the temp dir
    // -------------------------------------------------------------------------

    private JsonNode readJsonLog(Path sessionsDir) throws IOException {
        Path jsonFile = Files.list(sessionsDir)
            .filter(p -> p.toString().endsWith(".json"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No .json file found in " + sessionsDir));
        return MAPPER.readTree(jsonFile.toFile());
    }

    private String readTxtLog(Path sessionsDir) throws IOException {
        Path txtFile = Files.list(sessionsDir)
            .filter(p -> p.toString().endsWith(".txt"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No .txt file found in " + sessionsDir));
        return Files.readString(txtFile);
    }
}
