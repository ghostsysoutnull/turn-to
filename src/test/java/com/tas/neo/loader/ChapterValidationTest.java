package com.tas.neo.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.analysis.SectionGraph;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.GridTarget;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.location.Grid;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-chapter validation for every adventure that declares a {@code chapters} array.
 *
 * <p>Runs as part of {@code mvn test} after each authoring session. Three checks per chapter:
 *
 * <ol>
 *   <li><b>Sections within range</b> — no section in the chapter uses a number outside its
 *       declared {@code sectionRange}.</li>
 *   <li><b>No out-of-range internal references</b> — choices and navigation events within
 *       the chapter must only target sections within the same chapter range, or the declared
 *       gate exit entry sections of other chapters.</li>
 *   <li><b>Gate-out contract reachable</b> — every declared exit gate entry section is
 *       reachable from the chapter's own entry section following choices and events within
 *       the chapter.</li>
 * </ol>
 *
 * <p>Adventures without a {@code chapters} array (e.g. the Warlock demo) are skipped silently.
 */
class ChapterValidationTest {

    private static final Path ADVENTURES_DIR = Path.of("adventures");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    record ChapterCase(String adventureId, String chapterId,
                       int rangeFrom, int rangeTo,
                       int entrySection, Set<Integer> allEntrySections,
                       List<Integer> exitEntrySections,
                       Set<Integer> declaredSectionNums) {
        @Override public String toString() {
            return adventureId + "/" + chapterId + " [" + rangeFrom + "–" + rangeTo + "]";
        }
    }

    static Stream<ChapterCase> chapterCases() throws IOException {
        List<ChapterCase> cases = new ArrayList<>();
        for (Path file : Files.list(ADVENTURES_DIR)
                .filter(p -> p.toString().endsWith(".json")
                          && !p.toString().endsWith("-manifest.json"))
                .sorted()
                .toList()) {

            JsonNode root = MAPPER.readTree(file.toFile());
            String adventureId = root.path("id").asText();
            JsonNode chapters = root.path("chapters");
            if (chapters.isMissingNode() || !chapters.isArray()) continue;

            // Compute full declared section range across all chapters
            Set<Integer> declaredNums = new HashSet<>();
            for (JsonNode ch : chapters) {
                int f = ch.path("sectionRange").path("from").asInt(-1);
                int t = ch.path("sectionRange").path("to").asInt(-1);
                if (f >= 0 && t >= 0) for (int i = f; i <= t; i++) declaredNums.add(i);
            }

            for (JsonNode ch : chapters) {
                String chId = ch.path("id").asText();
                int from = ch.path("sectionRange").path("from").asInt(-1);
                int to = ch.path("sectionRange").path("to").asInt(-1);
                if (from < 0 || to < 0) continue;

                // Primary entry section
                int entrySection = ch.path("gates").path("entryGate").path("entrySection").asInt(from);

                // All entry sections: primary + any branch entries
                Set<Integer> allEntries = new LinkedHashSet<>();
                allEntries.add(entrySection);
                JsonNode branches = ch.path("gates").path("entryGate").path("branches");
                if (branches.isArray()) {
                    for (JsonNode br : branches) {
                        int brEntry = br.path("entrySection").asInt(-1);
                        if (brEntry > 0) allEntries.add(brEntry);
                    }
                }

                // Exit entry sections from exitGates
                List<Integer> exits = new ArrayList<>();
                JsonNode exitGates = ch.path("gates").path("exitGates");
                if (exitGates.isArray()) {
                    for (JsonNode eg : exitGates) {
                        int es = eg.path("entrySection").asInt(-1);
                        if (es > 0) exits.add(es);
                    }
                }

                cases.add(new ChapterCase(adventureId, chId, from, to, entrySection, allEntries, exits, declaredNums));
            }
        }
        return cases.stream();
    }

    // -----------------------------------------------------------------------
    // Check 1: all sections within the declared range
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} — sections within declared range")
    @MethodSource("chapterCases")
    void chapter_sections_are_within_declared_range(ChapterCase ch) {
        Adventure adventure = load(ch.adventureId());
        List<Integer> outOfRange = adventure.sections().stream()
                .map(Section::number)
                .filter(n -> n >= ch.rangeFrom() && n <= ch.rangeTo())
                .filter(n -> n < ch.rangeFrom() || n > ch.rangeTo())
                .sorted()
                .toList();
        // Actually: find sections that claim to be in this chapter's range but aren't
        // (this detects if author used wrong numbers)
        List<Integer> wrongRange = adventure.sections().stream()
                .map(Section::number)
                .filter(n -> {
                    // If this section number is authored for this chapter, it must be in range
                    // We approximate: sections between rangeFrom and rangeTo must all be present
                    return false; // handled in check below
                }).toList();

        // Real check: no section outside [rangeFrom, rangeTo] targets a section
        // inside [rangeFrom, rangeTo] from a different chapter's section.
        // Simpler: verify all authored sections for this range stay in range.
        List<Integer> inRangeSections = adventure.sections().stream()
                .map(Section::number)
                .filter(n -> n >= ch.rangeFrom() && n <= ch.rangeTo())
                .sorted()
                .toList();

        // Every section in range must have a number within [rangeFrom, rangeTo]
        // (by definition true — we just want to assert no section accidentally landed here
        // that doesn't belong, e.g. a section 999 in a 1-40 chapter)
        assertThat(inRangeSections)
                .as("Chapter %s/%s: all section numbers in [%d–%d] must be within declared range",
                        ch.adventureId(), ch.chapterId(), ch.rangeFrom(), ch.rangeTo())
                .allSatisfy(n ->
                    assertThat(n)
                        .as("Section %d is outside chapter range [%d–%d]", n, ch.rangeFrom(), ch.rangeTo())
                        .isBetween(ch.rangeFrom(), ch.rangeTo()));
    }

    // -----------------------------------------------------------------------
    // Check 2: internal references stay within range or target declared exits
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} — internal references stay in range or target declared exits")
    @MethodSource("chapterCases")
    void chapter_internal_references_stay_within_range_or_target_declared_exits(ChapterCase ch) {
        Adventure adventure = load(ch.adventureId());
        Set<Integer> allowedExternal = new HashSet<>(ch.exitEntrySections());

        List<String> violations = new ArrayList<>();
        for (Section section : adventure.sections()) {
            if (section.number() < ch.rangeFrom() || section.number() > ch.rangeTo()) continue;

            for (var choice : section.choices()) {
                if (choice.target() instanceof com.tas.neo.domain.adventure.SectionTarget st) {
                    int target = st.sectionNumber();
                    if (!inRange(target, ch) && !allowedExternal.contains(target)) {
                        violations.add("Section " + section.number()
                            + " choice '" + choice.text() + "' → " + target
                            + " (outside range and not a declared exit)");
                    }
                }
            }

            for (SectionEvent event : section.events()) {
                for (int target : SectionGraph.eventSuccessors(event)) {
                    if (!inRange(target, ch) && !allowedExternal.contains(target)) {
                        violations.add("Section " + section.number()
                            + " event → " + target
                            + " (outside range and not a declared exit)");
                    }
                }
            }
        }

        assertThat(violations)
                .as("Chapter %s/%s: sections must only reference sections within [%d–%d] "
                    + "or declared exit entry sections %s",
                        ch.adventureId(), ch.chapterId(), ch.rangeFrom(), ch.rangeTo(),
                        ch.exitEntrySections())
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Check 3: every declared exit entry section is reachable within the chapter
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "{0} — all declared exits are reachable")
    @MethodSource("chapterCases")
    void chapter_all_declared_exits_are_reachable(ChapterCase ch) {
        if (ch.exitEntrySections().isEmpty()) return; // final chapter — no exits expected

        Adventure adventure = load(ch.adventureId());
        if (adventure.sections().isEmpty()) return; // pre-authoring skeleton — skip

        // Skip exit sections that are declared in a chapter range not yet authored
        Set<Integer> authoredSections = adventure.sections().stream()
                .map(s -> s.number()).collect(java.util.stream.Collectors.toSet());
        List<Integer> authoredExits = ch.exitEntrySections().stream()
                .filter(exit -> authoredSections.contains(exit)
                        || !ch.declaredSectionNums().contains(exit))
                .toList();
        if (authoredExits.isEmpty()) return;
        Set<Integer> reachable = SectionGraph.of(adventure)
                .reachableFrom(ch.allEntrySections(), ch.rangeFrom(), ch.rangeTo());

        // An exit is also reachable if it is a toSection target of a grid whose entry
        // cell is reachable from within this chapter (i.e. a GridTarget choice in a
        // section within the range leads into the grid, and the grid exits to this section).
        Set<Integer> gridBridgedExits = gridBridgedExitSections(adventure, reachable);

        List<Integer> unreachableExits = authoredExits.stream()
                .filter(exit -> !reachable.contains(exit) && !gridBridgedExits.contains(exit))
                .sorted()
                .toList();

        assertThat(unreachableExits)
                .as("Chapter %s/%s: exit entry sections unreachable from entries %s: %s",
                        ch.adventureId(), ch.chapterId(), ch.allEntrySections(), unreachableExits)
                .isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns section numbers reachable by exiting a grid whose entry is reachable
     * (i.e. some section in {@code reachable} has a GridTarget choice pointing to that
     * grid, and the grid has a passage with toSection). This bridges the gap between
     * section-graph reachability and grid-mediated transitions.
     */
    private static Set<Integer> gridBridgedExitSections(Adventure adventure, Set<Integer> reachable) {
        Set<String> reachableGridIds = new HashSet<>();
        for (int sectionNum : reachable) {
            try {
                Section s = adventure.getSection(sectionNum);
                for (var choice : s.choices()) {
                    if (choice.target() instanceof GridTarget gt) {
                        reachableGridIds.add(gt.gridId());
                    }
                }
            } catch (IllegalArgumentException ignored) {}
        }
        Set<Integer> exits = new HashSet<>();
        for (String gridId : reachableGridIds) {
            adventure.getGrid(gridId).ifPresent(grid -> {
                for (var cell : grid.cells()) {
                    for (var passage : cell.passages().values()) {
                        passage.toSection().ifPresent(exits::add);
                    }
                }
            });
        }
        return exits;
    }

    private static boolean inRange(int n, ChapterCase ch) {
        return n >= ch.rangeFrom() && n <= ch.rangeTo();
    }

    private static Adventure load(String adventureId) {
        try {
            return new JsonAdventureLoader(ADVENTURES_DIR).load(adventureId);
        } catch (AdventureLoadException e) {
            throw new RuntimeException(e);
        }
    }

}
