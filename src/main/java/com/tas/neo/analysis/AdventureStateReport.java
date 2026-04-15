package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.StateEqualsCondition;
import com.tas.neo.domain.adventure.StateNotEqualsCondition;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates a compact state variable reference for an adventure.
 *
 * <p>For each variable touched by {@code state.set/get/has/remove()} calls in any
 * script hook, or by {@link StateEqualsCondition} / {@link StateNotEqualsCondition}
 * conditions, records every section and hook where the variable is set, read, checked,
 * or removed.
 *
 * <p>Output is written to {@code adventures/<id>-state.txt}.
 *
 * <p>Primary consumers: Chapter Reviewer Agent (gate-out contract verification) and
 * Consistency Check Agent (cross-chapter state tracking).
 */
public class AdventureStateReport {

    private static final Pattern STATE_CALL =
        Pattern.compile("state\\.(set|get|has|remove)\\(['\"]([^'\"]+)['\"]");

    public static String generate(Adventure adventure) {
        // variable → operation → sorted list of "§N(hook)" references
        Map<String, Map<String, List<String>>> index = new TreeMap<>();

        for (Section section : adventure.sections()) {
            // Section-level script hooks
            section.scripts().hooks().forEach((hook, script) ->
                extractCalls(script, section.number(), hook, index));

            // CombatEvent script hooks
            for (SectionEvent event : section.events()) {
                if (event instanceof CombatEvent ce) {
                    ce.scripts().hooks().forEach((hook, script) ->
                        extractCalls(script, section.number(), "combat." + hook, index));
                }
            }

            // StateEquals / StateNotEquals conditions on choices
            for (var choice : section.choices()) {
                choice.condition().ifPresent(cond -> {
                    switch (cond) {
                        case StateEqualsCondition c ->
                            record(index, c.key(), "check", ref(section.number(), "condition"));
                        case StateNotEqualsCondition c ->
                            record(index, c.key(), "check", ref(section.number(), "condition"));
                        default -> {}
                    }
                });
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("== State Variable Report: ").append(adventure.id()).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now());
        sb.append("  |  Variables: ").append(index.size()).append("\n\n");

        if (index.isEmpty()) {
            sb.append("  (no state variables found)\n");
        } else {
            index.forEach((var, ops) -> {
                sb.append(String.format("  %-32s", var));

                List<String> sets    = ops.getOrDefault("set",    List.of());
                List<String> reads   = new ArrayList<>(ops.getOrDefault("get", List.of()));
                reads.addAll(ops.getOrDefault("has", List.of()));
                Collections.sort(reads);
                List<String> checks  = ops.getOrDefault("check",  List.of());
                List<String> removes = ops.getOrDefault("remove", List.of());

                sb.append("set:").append(fmt(sets));
                sb.append("  read:").append(fmt(reads));
                if (!checks.isEmpty())  sb.append("  check:").append(fmt(checks));
                if (!removes.isEmpty()) sb.append("  remove:").append(fmt(removes));
                sb.append("\n");
            });
        }

        return sb.toString();
    }

    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 1) {
            System.err.println("Usage: AdventureStateReport <path-to-adventure.json>");
            System.exit(1);
        }
        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        String report = generate(adventure);
        System.out.print(report);

        Path out = adventuresDir.resolve(adventureId + "-state.txt");
        Files.writeString(out, report);
        System.err.println("Report written to " + out);
    }

    // -------------------------------------------------------------------------

    private static void extractCalls(String script, int sectionNumber, String hook,
                                      Map<String, Map<String, List<String>>> index) {
        Matcher m = STATE_CALL.matcher(script);
        while (m.find()) {
            record(index, m.group(2), m.group(1), ref(sectionNumber, hook));
        }
    }

    private static void record(Map<String, Map<String, List<String>>> index,
                                String var, String op, String location) {
        index.computeIfAbsent(var, k -> new TreeMap<>())
             .computeIfAbsent(op, k -> new ArrayList<>())
             .add(location);
    }

    private static String ref(int section, String hook) {
        return "\u00a7" + section + "(" + hook + ")";
    }

    private static String fmt(List<String> items) {
        return items.isEmpty() ? "\u2014" : String.join(", ", items);
    }
}
