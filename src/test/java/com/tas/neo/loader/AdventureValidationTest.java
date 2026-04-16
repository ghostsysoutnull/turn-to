package com.tas.neo.loader;

import com.tas.neo.analysis.SectionGraph;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Validates every adventure JSON file in the {@code adventures/} directory.
 *
 * <p>Runs automatically as part of {@code mvn test}, replacing the need for ad-hoc
 * validation scripts. Each adventure is a separate parameterized test case so failures
 * identify the specific adventure and check that failed.
 *
 * <p>Checks performed:
 * <ol>
 *   <li><b>Loads without error</b> — JsonAdventureLoader structural validation (dangling
 *       choice targets, invalid event targets, dead-end sections, item name mismatches).</li>
 *   <li><b>At least one VICTORY section</b> — the adventure must have a winning path.</li>
 *   <li><b>All sections reachable</b> — BFS from startSection following choices and
 *       navigation events; sections that are never reachable represent authored content
 *       that players can never see.</li>
 * </ol>
 */
class AdventureValidationTest {

    private static final Path ADVENTURES_DIR = Path.of("adventures");

    static Stream<String> adventureIds() throws IOException {
        return Files.list(ADVENTURES_DIR)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".json") && !name.endsWith("-manifest.json"))
                .map(name -> name.replace(".json", ""))
                .sorted();
    }

    // -----------------------------------------------------------------------
    // Check 1: loads without error
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} loads without error")
    @MethodSource("adventureIds")
    void adventure_loads_without_error(String adventureId) {
        JsonAdventureLoader loader = new JsonAdventureLoader(ADVENTURES_DIR);
        assertThatCode(() -> loader.load(adventureId))
                .as("Adventure '%s' must load without AdventureLoadException", adventureId)
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Check 2: at least one VICTORY section
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} has at least one VICTORY section")
    @MethodSource("adventureIds")
    void adventure_has_at_least_one_victory_section(String adventureId) {
        Adventure adventure = load(adventureId);
        if (adventure.sections().isEmpty()) return; // pre-authoring skeleton — skip
        long victoryCount = adventure.sections().stream()
                .filter(s -> s.type() == SectionType.VICTORY)
                .count();
        assertThat(victoryCount)
                .as("Adventure '%s' must have at least one VICTORY section so players can win",
                        adventureId)
                .isGreaterThan(0);
    }

    // -----------------------------------------------------------------------
    // Check 3: all sections reachable from startSection
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} has no orphaned sections")
    @MethodSource("adventureIds")
    void adventure_has_no_orphaned_sections(String adventureId) {
        Adventure adventure = load(adventureId);
        Set<Integer> reachable = SectionGraph.of(adventure).reachableFrom(adventure.startSection());
        Set<Integer> all = adventure.sections().stream()
                .map(Section::number)
                .collect(Collectors.toSet());

        List<Integer> orphaned = all.stream()
                .filter(n -> !reachable.contains(n))
                .sorted()
                .toList();

        assertThat(orphaned)
                .as("Adventure '%s' has orphaned sections (never reachable from section %d): %s",
                        adventureId, adventure.startSection(), orphaned)
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Adventure load(String adventureId) {
        try {
            return new JsonAdventureLoader(ADVENTURES_DIR).load(adventureId);
        } catch (AdventureLoadException e) {
            throw new RuntimeException("Failed to load adventure '" + adventureId + "': " + e.getMessage(), e);
        }
    }

}
