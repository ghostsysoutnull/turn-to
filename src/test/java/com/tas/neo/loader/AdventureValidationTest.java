package com.tas.neo.loader;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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
        Set<Integer> reachable = reachableSections(adventure);
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

    /**
     * BFS from startSection. Follows choice targetSections and all navigation
     * event target sections (NAVIGATE, LUCK_TEST success/fail, SKILL_TEST success/fail,
     * COMBAT success/failure). Also follows ctx.navigateTo() calls in onEnter scripts
     * by simple integer extraction — not full Lua parsing, so scripts that navigate
     * conditionally based on state may produce false orphan reports.
     */
    private static Set<Integer> reachableSections(Adventure adventure) {
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(adventure.startSection());

        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (!visited.add(current)) continue;

            Section section;
            try {
                section = adventure.getSection(current);
            } catch (IllegalArgumentException e) {
                continue; // dangling ref — already caught by loader
            }

            // Choices
            for (var choice : section.choices()) {
                if (choice.target() instanceof com.tas.neo.domain.adventure.SectionTarget st) {
                    enqueue(queue, visited, st.sectionNumber());
                }
            }

            // Navigation events
            for (SectionEvent event : section.events()) {
                collectEventTargets(event).forEach(n -> enqueue(queue, visited, n));
            }

            // onEnter script: extract literal navigateTo(N) calls
            section.scripts().get("onEnter").ifPresent(script ->
                extractNavigateToCalls(script).forEach(n -> enqueue(queue, visited, n))
            );
        }

        return visited;
    }

    private static List<Integer> collectEventTargets(SectionEvent event) {
        List<Integer> targets = new ArrayList<>();
        switch (event) {
            case NavigateEvent e -> targets.add(e.targetSection());
            case LuckTestEvent e -> { targets.add(e.successSection()); targets.add(e.failSection()); }
            case SkillTestEvent e -> { targets.add(e.successSection()); targets.add(e.failSection()); }
            case CombatEvent e -> {
                if (e.successSection() > 0) targets.add(e.successSection());
                if (e.failureSection() > 0) targets.add(e.failureSection());
                // Also scan scripts embedded in the combat event (e.g. onCombatEnd)
                e.scripts().hooks().values()
                        .forEach(script -> targets.addAll(extractNavigateToCalls(script)));
            }
            default -> { /* StatChange, GoldChange, ItemEvent — no navigation */ }
        }
        return targets;
    }

    private static List<Integer> extractNavigateToCalls(String script) {
        List<Integer> targets = new ArrayList<>();
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("navigateTo\\((\\d+)\\)").matcher(script);
        while (m.find()) {
            targets.add(Integer.parseInt(m.group(1)));
        }
        return targets;
    }

    private static void enqueue(Deque<Integer> queue, Set<Integer> visited, int section) {
        if (!visited.contains(section)) queue.add(section);
    }
}
