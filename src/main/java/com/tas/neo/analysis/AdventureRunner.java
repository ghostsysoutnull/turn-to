package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.loader.JsonAdventureLoader;
import com.tas.neo.scripting.LuaScriptEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class AdventureRunner {

    /**
     * Runs the adventure the configured number of times and returns an aggregated result.
     *
     * @param adventure  loaded adventure domain object
     * @param rawJson    raw adventure JSON (used for chapter boundary detection)
     * @param config     run configuration
     */
    public static RunBatchResult run(Adventure adventure, JsonNode rawJson, RunConfiguration config) {
        LuaScriptEngine scriptEngine = new LuaScriptEngine();
        Random random = new Random(config.seed());
        List<RunResult> results = new ArrayList<>(config.runs());
        Set<NeverSelectedChoice> everAvailable = new java.util.LinkedHashSet<>();
        Set<NeverSelectedChoice> everSelected  = new java.util.LinkedHashSet<>();

        for (int i = 0; i < config.runs(); i++) {
            RunSimulator simulator = new RunSimulator(adventure, rawJson, scriptEngine, config, random);
            results.add(simulator.run());
            everAvailable.addAll(simulator.conditionedChoicesAvailable());
            everSelected.addAll(simulator.conditionedChoicesSelected());
        }

        return new RunBatchResult(results, everAvailable, everSelected);
    }

    /** CLI entry point: args[0] = path to adventure JSON file */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: AdventureRunner <path-to-adventure.json> [--runs N] [--seed N]");
            System.exit(1);
        }
        Path adventurePath = Path.of(args[0]);
        Path adventuresDir = adventurePath.getParent();
        String adventureId = adventurePath.getFileName().toString().replace(".json", "");

        int runs = 50;
        long seed = System.currentTimeMillis();
        String strategy = "random";
        Set<Integer> avoided = Set.of();
        for (int i = 1; i < args.length - 1; i++) {
            if ("--runs".equals(args[i]))     runs    = Integer.parseInt(args[i + 1]);
            if ("--seed".equals(args[i]))     seed    = Long.parseLong(args[i + 1]);
            if ("--strategy".equals(args[i])) strategy = args[i + 1];
            if ("--avoid".equals(args[i]))    avoided = Arrays.stream(args[i + 1].split(","))
                .map(String::trim).map(Integer::parseInt).collect(Collectors.toSet());
        }

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        JsonNode rawJson = new ObjectMapper().readTree(Files.readString(adventurePath));

        ChoiceSelector selector = switch (strategy) {
            case "survival"     -> new SurvivalChoiceSelector(adventure, new RandomChoiceSelector());
            case "item-seeking" -> new ItemSeekingChoiceSelector();
            default             -> new RandomChoiceSelector();
        };

        if (!avoided.isEmpty()) {
            selector = new AvoidSectionsChoiceSelector(avoided, selector);
        }

        RunConfiguration config = new RunConfiguration(
            runs, selector, new com.tas.neo.mechanics.SeededDice(seed),
            seed, 20, OptionalInt.empty(), OptionalInt.empty()
        );

        RunBatchResult result = run(adventure, rawJson, config);
        String report = RunReportGenerator.generate(adventure, rawJson, result, config);

        System.out.print(report);

        Path reportPath = adventuresDir.resolve(adventureId + "-run-report.txt");
        Files.writeString(reportPath, report);
        System.err.println("Report written to " + reportPath);
    }
}
