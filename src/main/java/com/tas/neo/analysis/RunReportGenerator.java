package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.SectionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RunReportGenerator {

    public static String generate(Adventure adventure, JsonNode rawJson,
                                   RunBatchResult result, RunConfiguration config) {
        StringBuilder sb = new StringBuilder();
        int runs = config.runs();
        String id = adventure.id();

        sb.append("== Adventure Run Report: ").append(id).append(" ==\n");
        sb.append("Runs: ").append(runs)
          .append("  Seed: ").append(config.seed())
          .append("  Strategy: ").append(config.choiceSelector().getClass().getSimpleName())
          .append("  MaxVisits: ").append(config.maxVisitsPerSection()).append("\n\n");

        // Outcomes
        sb.append("OUTCOMES\n");
        sb.append("  VICTORY:      ").append(result.outcomeCount(RunOutcome.VICTORY)).append("\n");
        sb.append("  INSTANT_DEATH:").append(result.outcomeCount(RunOutcome.INSTANT_DEATH)).append("\n");
        sb.append("  STUCK:        ").append(result.outcomeCount(RunOutcome.STUCK)).append("\n");
        sb.append("  CYCLE:        ").append(result.outcomeCount(RunOutcome.CYCLE)).append("\n\n");

        // Coverage
        Set<Integer> covered = result.coveredSections();
        int total = adventure.sections().size();
        sb.append("COVERAGE\n");
        sb.append(String.format("  %d/%d sections (%.0f%%)\n\n",
            covered.size(), total, 100.0 * covered.size() / total));

        // Run length
        RunLengthSummary len = result.runLengthSummary();
        sb.append("RUN LENGTH (excl STUCK/CYCLE)\n");
        sb.append(String.format("  avg=%.1f  min=%d  max=%d\n\n", len.average(), len.min(), len.max()));

        // Chapter reach rates
        JsonNode chapters = rawJson.path("chapters");
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
        if (endings.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            endings.forEach((sec, count) -> {
                SectionType type = adventure.getSection(sec).type();
                sb.append(String.format("  §%-4d %-12s %d/%d\n",
                    sec, type, count, runs));
            });
        }
        sb.append("\n");

        // ISSUES
        sb.append("ISSUES\n");
        long stuck = result.outcomeCount(RunOutcome.STUCK);
        long cycle = result.outcomeCount(RunOutcome.CYCLE);
        long victory = result.outcomeCount(RunOutcome.VICTORY);
        boolean hasIssues = false;
        if (stuck > 0) {
            sb.append("  ✗ STUCK runs: ").append(stuck).append("\n");
            hasIssues = true;
        }
        if (cycle > 0) {
            sb.append("  ✗ CYCLE runs: ").append(cycle).append("\n");
            hasIssues = true;
        }
        if (victory == 0) {
            sb.append("  ✗ No VICTORY reached in ").append(runs).append(" runs\n");
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
}
