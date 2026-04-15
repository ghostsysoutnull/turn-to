package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.item.Item;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Generates a compact text report summarising an adventure's graph properties.
 *
 * <p>The report is designed to be read by the Chapter Reviewer Agent and Adventure
 * Reviewer Agent in place of the full adventure JSON, reducing token consumption.
 * It covers reachability, section type distribution, item usage, and per-chapter
 * gate compliance.
 *
 * <p>Run from the command line after each authoring session:
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureReportGenerator \
 *   -Dexec.args="adventures/&lt;id&gt;.json"
 * </pre>
 *
 * <p>Output is written to {@code adventures/<id>-report.txt} and printed to stdout.
 */
public class AdventureReportGenerator {

    /**
     * Generates a report string for the given adventure.
     *
     * @param adventure the loaded domain object
     * @param rawJson   the raw JSON root node (needed for the chapters array, which is
     *                  authoring-only metadata not stored on {@link Adventure})
     */
    public static String generate(Adventure adventure, JsonNode rawJson) {
        SectionGraph graph = SectionGraph.of(adventure);
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, adventure);
        appendReachability(sb, adventure, graph);
        appendSectionTypes(sb, adventure);
        appendItems(sb, adventure);
        appendChapters(sb, adventure, graph, rawJson);
        appendIssues(sb, adventure, graph, rawJson);

        return sb.toString();
    }

    /**
     * CLI entry point. Expects one argument: path to the adventure JSON file.
     * Writes the report to {@code adventures/<id>-report.txt} alongside the source file.
     */
    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 1) {
            System.err.println("Usage: AdventureReportGenerator <path-to-adventure.json>");
            System.exit(1);
        }
        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        JsonNode rawJson = new ObjectMapper().readTree(adventureFile.toFile());

        String report = generate(adventure, rawJson);
        System.out.print(report);

        Path reportFile = adventuresDir.resolve(adventureId + "-report.txt");
        Files.writeString(reportFile, report);
        System.err.println("Report written to " + reportFile);
    }

    // -------------------------------------------------------------------------
    // Sections
    // -------------------------------------------------------------------------

    private static void appendHeader(StringBuilder sb, Adventure adventure) {
        sb.append("== Adventure Report: ").append(adventure.id()).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now());
        sb.append("  |  Title: ").append(adventure.title());
        sb.append("  |  Sections: ").append(adventure.sections().size());
        sb.append("  |  Items: ").append(adventure.items().size());
        sb.append("\n\n");
    }

    private static void appendReachability(StringBuilder sb, Adventure adventure,
                                            SectionGraph graph) {
        Set<Integer> allSections = graph.allSectionNumbers();
        Set<Integer> reachable = graph.reachableFrom(adventure.startSection());
        Set<Integer> orphaned = new TreeSet<>(allSections);
        orphaned.removeAll(reachable);

        sb.append("REACHABILITY\n");
        if (orphaned.isEmpty()) {
            sb.append("  All ").append(allSections.size())
              .append(" sections reachable from start (").append(adventure.startSection())
              .append(") \u2713\n");
        } else {
            sb.append("  Reachable: ").append(reachable.size())
              .append("/").append(allSections.size()).append(" \u2717\n");
            sb.append("  Orphaned: ").append(orphaned).append("\n");
        }
        sb.append("\n");
    }

    private static void appendSectionTypes(StringBuilder sb, Adventure adventure) {
        Map<SectionType, Long> counts = adventure.sections().stream()
            .collect(Collectors.groupingBy(Section::type, Collectors.counting()));

        sb.append("SECTION TYPES\n  ");
        boolean first = true;
        for (SectionType type : SectionType.values()) {
            long count = counts.getOrDefault(type, 0L);
            if (count == 0) continue;
            if (!first) sb.append("  ");
            sb.append(type).append(": ").append(count);
            first = false;
        }
        sb.append("\n\n");
    }

    private static void appendItems(StringBuilder sb, Adventure adventure) {
        if (adventure.items().isEmpty()) {
            sb.append("ITEMS  (none defined)\n\n");
            return;
        }

        Set<String> referenced = adventure.sections().stream()
            .flatMap(s -> s.events().stream())
            .filter(e -> e instanceof ItemEvent)
            .map(e -> ((ItemEvent) e).itemName())
            .collect(Collectors.toSet());

        List<String> unreferenced = adventure.items().stream()
            .map(Item::name)
            .filter(name -> !referenced.contains(name))
            .sorted()
            .toList();

        sb.append("ITEMS  (").append(adventure.items().size()).append(" defined)\n");
        if (unreferenced.isEmpty()) {
            sb.append("  All items referenced in sections \u2713\n");
        } else {
            sb.append("  Unreferenced: ").append(unreferenced).append(" \u26a0\n");
        }
        sb.append("\n");
    }

    private static void appendChapters(StringBuilder sb, Adventure adventure,
                                        SectionGraph graph, JsonNode rawJson) {
        JsonNode chapters = rawJson.path("chapters");
        if (chapters.isMissingNode() || !chapters.isArray() || chapters.isEmpty()) return;

        sb.append("CHAPTERS  (").append(chapters.size()).append(")\n");

        for (JsonNode ch : chapters) {
            String id = ch.path("id").asText("?");
            int from = ch.path("sectionRange").path("from").asInt(-1);
            int to   = ch.path("sectionRange").path("to").asInt(-1);
            if (from < 0 || to < 0) continue;

            int entry = ch.path("gates").path("entryGate").path("entrySection").asInt(from);

            Set<Integer> allEntries = new LinkedHashSet<>();
            allEntries.add(entry);
            JsonNode entryBranches = ch.path("gates").path("entryGate").path("branches");
            if (entryBranches.isArray()) {
                for (JsonNode br : entryBranches) {
                    int brEntry = br.path("entrySection").asInt(-1);
                    if (brEntry > 0) allEntries.add(brEntry);
                }
            }

            List<Integer> exits = new ArrayList<>();
            JsonNode exitGates = ch.path("gates").path("exitGates");
            if (exitGates.isArray()) {
                for (JsonNode eg : exitGates) {
                    int es = eg.path("entrySection").asInt(-1);
                    if (es > 0) exits.add(es);
                }
            }

            Set<Integer> reachable = graph.reachableFrom(allEntries, from, to);
            long inRangeCount = reachable.stream().filter(n -> n >= from && n <= to).count();
            int rangeSize = to - from + 1;

            boolean allReachable = inRangeCount == rangeSize;
            boolean exitsReachable = exits.isEmpty() || exits.stream().allMatch(reachable::contains);

            sb.append(String.format("  %-6s [%4d–%4d]  entry:%-4d  exits:[%s]  coverage:%d/%d %s  exits-reachable:%s%n",
                id,
                from, to,
                entry,
                exits.stream().map(String::valueOf).collect(Collectors.joining(" ")),
                inRangeCount, rangeSize,
                allReachable ? "\u2713" : "\u2717",
                exits.isEmpty() ? "n/a (final)" : (exitsReachable ? "\u2713" : "\u2717")));
        }
        sb.append("\n");
    }

    private static void appendIssues(StringBuilder sb, Adventure adventure,
                                      SectionGraph graph, JsonNode rawJson) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Orphaned sections
        Set<Integer> orphaned = new TreeSet<>(graph.allSectionNumbers());
        orphaned.removeAll(graph.reachableFrom(adventure.startSection()));
        if (!orphaned.isEmpty()) {
            errors.add("ORPHANED  sections " + orphaned
                + " — never reachable from start section " + adventure.startSection());
        }

        // Unreferenced items
        Set<String> referenced = adventure.sections().stream()
            .flatMap(s -> s.events().stream())
            .filter(e -> e instanceof ItemEvent)
            .map(e -> ((ItemEvent) e).itemName())
            .collect(Collectors.toSet());
        List<String> unreferenced = adventure.items().stream()
            .map(Item::name)
            .filter(name -> !referenced.contains(name))
            .sorted()
            .toList();
        if (!unreferenced.isEmpty()) {
            warnings.add("ITEM      " + unreferenced + " defined but never referenced in a section event");
        }

        // Chapter-level issues
        JsonNode chapters = rawJson.path("chapters");
        if (!chapters.isMissingNode() && chapters.isArray()) {
            for (JsonNode ch : chapters) {
                String id = ch.path("id").asText("?");
                int from = ch.path("sectionRange").path("from").asInt(-1);
                int to   = ch.path("sectionRange").path("to").asInt(-1);
                if (from < 0 || to < 0) continue;

                int entry = ch.path("gates").path("entryGate").path("entrySection").asInt(from);
                Set<Integer> allEntries = new LinkedHashSet<>();
                allEntries.add(entry);
                JsonNode issueBranches = ch.path("gates").path("entryGate").path("branches");
                if (issueBranches.isArray()) {
                    for (JsonNode br : issueBranches) {
                        int brEntry = br.path("entrySection").asInt(-1);
                        if (brEntry > 0) allEntries.add(brEntry);
                    }
                }
                Set<Integer> reachable = graph.reachableFrom(allEntries, from, to);

                // Coverage
                for (Section s : adventure.sections()) {
                    if (s.number() >= from && s.number() <= to && !reachable.contains(s.number())) {
                        errors.add("CH-" + id.toUpperCase() + "  section " + s.number()
                            + " not reachable from chapter entry " + entry);
                    }
                }

                // Exit reachability
                JsonNode exitGates = ch.path("gates").path("exitGates");
                if (exitGates.isArray()) {
                    for (JsonNode eg : exitGates) {
                        int es = eg.path("entrySection").asInt(-1);
                        if (es > 0 && !reachable.contains(es)) {
                            errors.add("CH-" + id.toUpperCase() + "  exit section " + es
                                + " not reachable from chapter entry " + entry);
                        }
                    }
                }
            }
        }

        sb.append("ISSUES\n");
        if (errors.isEmpty() && warnings.isEmpty()) {
            sb.append("  none \u2713\n");
        } else {
            errors.forEach(e -> sb.append("  \u2717 ").append(e).append("\n"));
            warnings.forEach(w -> sb.append("  \u26a0 ").append(w).append("\n"));
        }
    }
}
