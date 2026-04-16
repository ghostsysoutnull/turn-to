package com.tas.neo.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public class RunBatchResult {

    private final List<RunResult> runs;
    private final Set<NeverSelectedChoice> everAvailable;
    private final Set<NeverSelectedChoice> everSelected;

    public static RunBatchResult merge(List<RunBatchResult> batches) {
        List<RunResult> allRuns = new ArrayList<>();
        Set<NeverSelectedChoice> allAvailable = new LinkedHashSet<>();
        Set<NeverSelectedChoice> allSelected  = new LinkedHashSet<>();
        for (RunBatchResult b : batches) {
            allRuns.addAll(b.runs);
            allAvailable.addAll(b.everAvailable);
            allSelected.addAll(b.everSelected);
        }
        return new RunBatchResult(allRuns, allAvailable, allSelected);
    }

    public RunBatchResult(List<RunResult> runs) {
        this(runs, Set.of(), Set.of());
    }

    public RunBatchResult(List<RunResult> runs,
                          Set<NeverSelectedChoice> everAvailable,
                          Set<NeverSelectedChoice> everSelected) {
        this.runs         = List.copyOf(runs);
        this.everAvailable = Set.copyOf(everAvailable);
        this.everSelected  = Set.copyOf(everSelected);
    }

    public long outcomeCount(RunOutcome outcome) {
        return runs.stream().filter(r -> r.outcome() == outcome).count();
    }

    public long endingCount(int section) {
        return runs.stream().filter(r -> r.endingSection() == section).count();
    }

    public Set<Integer> coveredSections() {
        Set<Integer> covered = new LinkedHashSet<>();
        for (RunResult run : runs) {
            covered.addAll(run.sectionsVisited());
        }
        return covered;
    }

    public double chapterReachRate(String chapterId) {
        if (runs.isEmpty()) return 0.0;
        long reached = runs.stream()
            .filter(r -> r.chapterSnapshots().stream()
                .anyMatch(s -> s.chapterId().equals(chapterId)))
            .count();
        return (double) reached / runs.size();
    }

    public double itemCarryRate(String chapterId, String itemName) {
        List<RunResult> withChapter = runs.stream()
            .filter(r -> r.chapterSnapshots().stream()
                .anyMatch(s -> s.chapterId().equals(chapterId)))
            .toList();
        if (withChapter.isEmpty()) return 0.0;
        long carrying = withChapter.stream()
            .filter(r -> r.chapterSnapshots().stream()
                .filter(s -> s.chapterId().equals(chapterId))
                .anyMatch(s -> s.inventory().contains(itemName)))
            .count();
        return (double) carrying / withChapter.size();
    }

    public StateSummary goldDistribution(String chapterId) {
        List<Integer> values = runs.stream()
            .flatMap(r -> r.chapterSnapshots().stream())
            .filter(s -> s.chapterId().equals(chapterId))
            .map(ChapterSnapshot::gold)
            .toList();
        return numericSummary(values);
    }

    public StateSummary stateDistribution(String chapterId, String key) {
        List<Object> rawValues = runs.stream()
            .flatMap(r -> r.chapterSnapshots().stream())
            .filter(s -> s.chapterId().equals(chapterId))
            .map(s -> s.stateVariables().get(key))
            .filter(v -> v != null)
            .toList();

        // Determine if numeric or boolean/categorical
        boolean allNumeric = !rawValues.isEmpty()
            && rawValues.stream().allMatch(v -> v instanceof Number);

        if (allNumeric) {
            List<Integer> numbers = rawValues.stream()
                .map(v -> ((Number) v).intValue())
                .toList();
            return numericSummary(numbers);
        }

        // Categorical/boolean — count values
        Map<Object, Integer> counts = new HashMap<>();
        for (Object v : rawValues) {
            counts.merge(v, 1, Integer::sum);
        }
        return new StateSummary(OptionalDouble.empty(), 0, 0, counts);
    }

    public RunLengthSummary runLengthSummary() {
        List<Integer> lengths = runs.stream()
            .filter(r -> r.outcome() != RunOutcome.STUCK && r.outcome() != RunOutcome.CYCLE)
            .map(r -> r.sectionsVisited().size())
            .toList();
        if (lengths.isEmpty()) {
            return new RunLengthSummary(0.0, 0, 0);
        }
        double avg = lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int min = lengths.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = lengths.stream().mapToInt(Integer::intValue).max().orElse(0);
        return new RunLengthSummary(avg, min, max);
    }

    public List<NeverSelectedChoice> neverSelectedChoices() {
        return everAvailable.stream()
            .filter(c -> !everSelected.contains(c))
            .toList();
    }

    public Set<String> warnings() {
        Set<String> all = new LinkedHashSet<>();
        for (RunResult run : runs) {
            all.addAll(run.warnings());
        }
        return all;
    }

    private StateSummary numericSummary(List<Integer> values) {
        if (values.isEmpty()) {
            return new StateSummary(OptionalDouble.empty(), 0, 0, Map.of());
        }
        double avg = values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int min = values.stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);
        return new StateSummary(OptionalDouble.of(avg), min, max, Map.of());
    }
}
