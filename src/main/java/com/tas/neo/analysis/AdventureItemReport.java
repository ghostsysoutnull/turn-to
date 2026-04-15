package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.LacksItemCondition;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Generates a full item lifecycle report for an adventure.
 *
 * <p>For each item defined in the adventure, lists every section where the item is
 * gained, lost, required (HAS_ITEM condition), or forbidden (LACKS_ITEM condition).
 * This extends the unreferenced-item check in {@link AdventureReportGenerator} into
 * a complete per-item view.
 *
 * <p>Output is written to {@code adventures/<id>-items.txt}.
 *
 * <p>Primary consumer: Consistency Check Agent (item lifecycle verification across
 * chapters, gate-out contract item guarantees).
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureItemReport \
 *   -Dexec.args="adventures/&lt;id&gt;.json"
 * </pre>
 */
public class AdventureItemReport {

    public static String generate(Adventure adventure) {
        // item name → operation → list of section references
        TreeMap<String, ItemLifecycle> index = new TreeMap<>();

        // Seed from manifest so items with no events still appear
        adventure.items().forEach(item ->
            index.put(item.name(), new ItemLifecycle()));

        for (Section section : adventure.sections()) {
            for (var event : section.events()) {
                if (event instanceof ItemEvent ie) {
                    ItemLifecycle lc = index.computeIfAbsent(ie.itemName(), k -> new ItemLifecycle());
                    if (ie.action() == ItemAction.GAIN) lc.gained.add(section.number());
                    else                                lc.lost.add(section.number());
                }
            }
            for (var choice : section.choices()) {
                choice.condition().ifPresent(cond -> {
                    switch (cond) {
                        case HasItemCondition c -> index
                            .computeIfAbsent(c.itemName(), k -> new ItemLifecycle())
                            .required.add(section.number());
                        case LacksItemCondition c -> index
                            .computeIfAbsent(c.itemName(), k -> new ItemLifecycle())
                            .forbidden.add(section.number());
                        default -> {}
                    }
                });
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("== Item Lifecycle Report: ").append(adventure.id()).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now());
        sb.append("  |  Items: ").append(adventure.items().size()).append("\n\n");

        index.forEach((name, lc) -> {
            sb.append(String.format("  %-30s", name));
            sb.append("GAIN:").append(fmt(lc.gained));
            sb.append("  LOSS:").append(fmt(lc.lost));
            if (!lc.required.isEmpty())  sb.append("  REQUIRED:").append(fmt(lc.required));
            if (!lc.forbidden.isEmpty()) sb.append("  FORBIDDEN:").append(fmt(lc.forbidden));
            if (lc.gained.isEmpty() && lc.lost.isEmpty()
                    && lc.required.isEmpty() && lc.forbidden.isEmpty()) {
                sb.append(" \u26a0 unreferenced");
            }
            sb.append("\n");
        });

        return sb.toString();
    }

    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 1) {
            System.err.println("Usage: AdventureItemReport <path-to-adventure.json>");
            System.exit(1);
        }
        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        String report = generate(adventure);
        System.out.print(report);

        Path out = adventuresDir.resolve(adventureId + "-items.txt");
        Files.writeString(out, report);
        System.err.println("Report written to " + out);
    }

    // -------------------------------------------------------------------------

    private static String fmt(List<Integer> sections) {
        if (sections.isEmpty()) return "\u2014";
        return sections.stream().map(n -> "\u00a7" + n).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static class ItemLifecycle {
        final List<Integer> gained   = new ArrayList<>();
        final List<Integer> lost     = new ArrayList<>();
        final List<Integer> required = new ArrayList<>();
        final List<Integer> forbidden = new ArrayList<>();
    }
}
