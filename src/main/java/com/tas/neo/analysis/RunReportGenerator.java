package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.item.Item;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

public class RunReportGenerator {

    public static String generate(Adventure adventure, JsonNode rawJson,
                                   RunBatchResult result, RunConfiguration config) {
        StringBuilder sb = new StringBuilder();
        int runs = config.runs();
        String id = adventure.id();

        JsonNode chapters = rawJson.path("chapters");

        sb.append("== Adventure Run Report: ").append(id).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now())
          .append("  |  Runs: ").append(runs)
          .append("  |  Strategy: ").append(config.choiceSelector().name())
          .append("  |  Dice: ").append(diceName(config.dice()))
          .append("  |  Seed: ").append(config.seed()).append("\n\n");

        // Outcomes
        sb.append("OUTCOMES\n");
        sb.append("  VICTORY:      ").append(result.outcomeCount(RunOutcome.VICTORY)).append("\n");
        sb.append("  INSTANT_DEATH:").append(result.outcomeCount(RunOutcome.INSTANT_DEATH)).append("\n");
        sb.append("  STUCK:        ").append(result.outcomeCount(RunOutcome.STUCK)).append("\n");
        sb.append("  CYCLE:        ").append(result.outcomeCount(RunOutcome.CYCLE)).append("\n");
        sb.append("  GRID_ENTRY:   ").append(result.outcomeCount(RunOutcome.GRID_ENTRY))
          .append("  (grid navigation not simulated)\n\n");

        // Coverage
        Set<Integer> covered = result.coveredSections();
        int total = adventure.sections().size();
        sb.append("COVERAGE\n");
        sb.append(String.format("  %d/%d sections (%.0f%%)\n",
            covered.size(), total, 100.0 * covered.size() / total));

        List<Integer> unreached = adventure.sections().stream()
            .map(s -> s.number())
            .filter(n -> !covered.contains(n))
            .sorted()
            .toList();
        if (!unreached.isEmpty()) {
            // Group by chapter if chapter data available
            Map<String, List<Integer>> byChapter = new LinkedHashMap<>();
            byChapter.put("(unknown)", new ArrayList<>());
            if (chapters.isArray()) {
                for (JsonNode ch : chapters) {
                    byChapter.put(ch.path("id").asText(), new ArrayList<>());
                }
            }
            for (int n : unreached) {
                String chId = "(unknown)";
                if (chapters.isArray()) {
                    for (JsonNode ch : chapters) {
                        int from = ch.path("sectionRange").path("from").asInt(-1);
                        int to   = ch.path("sectionRange").path("to").asInt(-1);
                        if (from > 0 && n >= from && n <= to) { chId = ch.path("id").asText(); break; }
                    }
                }
                byChapter.get(chId).add(n);
            }
            byChapter.forEach((chId, sections) -> {
                if (!sections.isEmpty()) {
                    sb.append(String.format("  unreached %-6s", chId));
                    sections.forEach(n -> sb.append(" §").append(n));
                    sb.append("\n");
                }
            });
        }
        sb.append("\n");

        // Run length
        RunLengthSummary len = result.runLengthSummary();
        sb.append("RUN LENGTH (excl STUCK/CYCLE)\n");
        sb.append(String.format("  avg=%.1f  min=%d  max=%d\n\n", len.average(), len.min(), len.max()));

        // Chapter reach rates
        if (chapters.isArray() && chapters.size() > 0) {
            sb.append("CHAPTER REACH RATES\n");
            for (JsonNode ch : chapters) {
                String chId = ch.path("id").asText();
                double rate = result.chapterReachRate(chId);
                sb.append(String.format("  %-6s %.0f%%\n", chId, rate * 100));
            }
            sb.append("\n");
        }

        // Endings distribution
        sb.append("ENDINGS\n");
        Map<Integer, Long> endings = new TreeMap<>();
        for (var section : adventure.sections()) {
            if (section.type() == SectionType.VICTORY || section.type() == SectionType.INSTANT_DEATH) {
                long count = result.endingCount(section.number());
                if (count > 0) {
                    endings.put(section.number(), count);
                }
            }
        }
        Map<Integer, Long> cycleRoots = result.cycleEndingSections();
        if (endings.isEmpty() && cycleRoots.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            endings.forEach((sec, count) -> {
                SectionType type = adventure.getSection(sec).type();
                sb.append(String.format("  §%-4d %-12s %d/%d\n",
                    sec, type, count, runs));
            });
            cycleRoots.forEach((sec, count) ->
                sb.append(String.format("  §%-4d %-12s %d/%d\n", sec, "CYCLE", count, runs)));
        }
        sb.append("\n");

        // Item flow at chapter boundaries
        if (chapters.isArray() && chapters.size() > 0) {
            appendItemFlow(sb, adventure, chapters, result);
        }

        // Gold and state distribution at chapter boundaries
        if (chapters.isArray() && chapters.size() > 0) {
            appendGoldAndStateDistribution(sb, chapters, result);
        }

        // Gating
        appendGating(sb, adventure, result);

        // ISSUES
        sb.append("ISSUES\n");
        long stuck = result.outcomeCount(RunOutcome.STUCK);
        long cycle = result.outcomeCount(RunOutcome.CYCLE);
        long victory = result.outcomeCount(RunOutcome.VICTORY);
        long gridEntry = result.outcomeCount(RunOutcome.GRID_ENTRY);
        boolean hasIssues = false;
        if (stuck > 0) {
            sb.append("  ✗ STUCK runs: ").append(stuck).append("\n");
            hasIssues = true;
        }
        if (cycle > 0) {
            sb.append("  ✗ CYCLE runs: ").append(cycle).append("\n");
            hasIssues = true;
        }
        if (victory == 0 && gridEntry == 0) {
            sb.append("  ✗ No VICTORY reached in ").append(runs).append(" runs\n");
            hasIssues = true;
        }
        List<NeverSelectedChoice> neverSelected = result.neverSelectedChoices();
        if (!neverSelected.isEmpty()) {
            sb.append("  ⚠ Conditioned choices never selected (condition may be unachievable):\n");
            neverSelected.forEach(c -> sb.append(String.format(
                "      §%-4d  \"%s\"\n", c.section(), c.choiceText())));
            hasIssues = true;
        }
        Set<String> warnings = result.warnings();
        if (!warnings.isEmpty()) {
            warnings.forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
            hasIssues = true;
        }
        if (!hasIssues) {
            sb.append("  none ✓\n");
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // ITEM FLOW AT CHAPTER BOUNDARIES
    // -------------------------------------------------------------------------

    private static void appendItemFlow(StringBuilder sb, Adventure adventure,
                                        JsonNode chapters, RunBatchResult result) {
        List<String> chapterIds = chapterIdList(chapters);
        List<String> itemNames = adventure.items().stream().map(Item::name).toList();
        if (itemNames.isEmpty()) return;

        // Only include items with at least one nonzero carry rate
        List<String> activeItems = itemNames.stream()
            .filter(name -> chapterIds.stream()
                .anyMatch(chId -> result.itemCarryRate(chId, name) > 0.0))
            .toList();
        if (activeItems.isEmpty()) return;

        // Determine column and name widths
        int nameWidth = activeItems.stream().mapToInt(String::length).max().orElse(10);
        nameWidth = Math.max(nameWidth, 10);
        int colWidth = 7;

        sb.append("ITEM FLOW AT CHAPTER BOUNDARIES")
          .append("  (% of runs carrying item when entering chapter)\n");
        sb.append("  — = not carried in any run at this boundary\n");

        // Header row
        sb.append(String.format("  %-" + nameWidth + "s", ""));
        for (String chId : chapterIds) {
            sb.append(String.format("%" + colWidth + "s", chId));
        }
        sb.append("\n");

        // Item rows
        for (String name : activeItems) {
            sb.append(String.format("  %-" + nameWidth + "s", name));
            for (String chId : chapterIds) {
                double rate = result.itemCarryRate(chId, name);
                String cell = rate > 0.0 ? String.format("%.0f%%", rate * 100) : "—";
                sb.append(String.format("%" + colWidth + "s", cell));
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    // -------------------------------------------------------------------------
    // GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES
    // -------------------------------------------------------------------------

    private static void appendGoldAndStateDistribution(StringBuilder sb, JsonNode chapters,
                                                         RunBatchResult result) {
        List<String> chapterIds = chapterIdList(chapters);
        boolean anyOutput = false;

        for (String chId : chapterIds) {
            StateSummary gold = result.goldDistribution(chId);
            if (gold.average().isPresent()) {
                if (!anyOutput) {
                    sb.append("GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES\n");
                    anyOutput = true;
                }
                sb.append(String.format("  gold at %s entry: avg %.0f  range %d–%d\n",
                    chId, gold.average().getAsDouble(), gold.min(), gold.max()));
            }

            for (String key : result.stateVariableKeys(chId)) {
                StateSummary state = result.stateDistribution(chId, key);
                if (!anyOutput) {
                    sb.append("GOLD AND STATE DISTRIBUTION AT CHAPTER BOUNDARIES\n");
                    anyOutput = true;
                }
                OptionalDouble avg = state.average();
                if (avg.isPresent()) {
                    sb.append(String.format("  %s at %s entry: avg %.0f  range %d–%d\n",
                        key, chId, avg.getAsDouble(), state.min(), state.max()));
                } else if (!state.valueCounts().isEmpty()) {
                    int total = state.valueCounts().values().stream()
                        .mapToInt(Integer::intValue).sum();
                    StringBuilder line = new StringBuilder();
                    line.append(String.format("  %s at %s entry:", key, chId));
                    state.valueCounts().forEach((val, count) ->
                        line.append(String.format("  %s:%.0f%%", val,
                            100.0 * count / total)));
                    sb.append(line).append("\n");
                }
            }
        }
        if (anyOutput) sb.append("\n");
    }

    // -------------------------------------------------------------------------
    // GATING
    // -------------------------------------------------------------------------

    private static void appendGating(StringBuilder sb, Adventure adventure,
                                      RunBatchResult result) {
        // All conditioned choices from the adventure definition
        Set<NeverSelectedChoice> allConditioned = new LinkedHashSet<>();
        for (var section : adventure.sections()) {
            for (var choice : section.choices()) {
                if (choice.condition().isPresent()) {
                    allConditioned.add(new NeverSelectedChoice(section.number(), choice.text()));
                }
            }
        }

        Set<NeverSelectedChoice> everAvailable = result.conditionedChoicesEverAvailable();
        List<NeverSelectedChoice> neverMet = allConditioned.stream()
            .filter(c -> !everAvailable.contains(c))
            .sorted(Comparator.comparingInt(NeverSelectedChoice::section))
            .toList();

        sb.append("GATING\n");
        if (neverMet.isEmpty()) {
            sb.append("  Choices with condition never met in any run: none ✓\n");
        } else {
            sb.append("  Choices with condition never met in any run:\n");
            for (NeverSelectedChoice c : neverMet) {
                sb.append(String.format("    §%-4d \"%s\"\n", c.section(), c.choiceText()));
            }
        }
        sb.append("\n");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String diceName(com.tas.neo.domain.Dice dice) {
        return switch (dice.getClass().getSimpleName()) {
            case "SeededDice", "RandomDice" -> "RANDOM";
            case "FixedDice" -> "FIXED";
            default -> dice.getClass().getSimpleName();
        };
    }

    private static List<String> chapterIdList(JsonNode chapters) {
        List<String> ids = new ArrayList<>();
        if (chapters.isArray()) {
            for (JsonNode ch : chapters) {
                ids.add(ch.path("id").asText());
            }
        }
        return ids;
    }
}
