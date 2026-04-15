package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionType;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Multi-mode inspection tool for adventure files.
 *
 * <p>Replaces ad-hoc Python scripts for querying adventure JSON. Each mode
 * addresses a specific inspection need identified from common authoring workflow
 * patterns:
 *
 * <ul>
 *   <li><b>sections</b>     — raw JSON of one or more sections by number
 *   <li><b>--refs N</b>     — all sections that link to section N (inbound edges)
 *   <li><b>--has-event</b>  — sections containing a specific event type
 *   <li><b>--has-condition</b> — sections containing a specific choice condition type
 *   <li><b>--dead-ends</b>  — NORMAL sections with no successors
 *   <li><b>--state VAR</b>  — sections that set, read, or check a state variable
 *   <li><b>--gate CH</b>    — raw JSON gate contracts for a chapter
 * </ul>
 *
 * <p>Run from the command line:
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureSectionInspector \
 *   -Dexec.args="adventures/&lt;id&gt;.json 88"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json 91 92 93"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json 91-97"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --refs 74"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --has-event ITEM_GAIN"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --has-event ITEM_GAIN --item 'Bribe Purse'"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --has-condition GoldCondition"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --dead-ends"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --dead-ends ch2"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --state suspicion"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --gate ch3"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --gate ch3 in"
 * mvn exec:java ... -Dexec.args="adventures/&lt;id&gt;.json --gate ch3 out"
 * </pre>
 */
public class AdventureSectionInspector {

    private static final ObjectWriter JSON_WRITER =
        new ObjectMapper().writerWithDefaultPrettyPrinter();

    private static final Pattern STATE_CALL =
        Pattern.compile("state\\.(set|get|has|remove)\\(['\"]([^'\"]+)['\"]");

    // -------------------------------------------------------------------------
    // Public API — one method per mode
    // -------------------------------------------------------------------------

    /**
     * Returns the raw JSON object for each requested section number, formatted
     * for readability. Unknown section numbers are reported as "(not found)".
     */
    public static String inspectSections(JsonNode rawJson, List<Integer> numbers) {
        StringBuilder sb = new StringBuilder();
        sb.append("== Sections: ").append(numbers).append(" ==\n\n");

        for (int number : numbers) {
            sb.append("=== §").append(number).append(" ===\n");
            JsonNode found = findSectionJson(rawJson, number);
            if (found == null) {
                sb.append("  (not found)\n");
            } else {
                try {
                    sb.append(JSON_WRITER.writeValueAsString(found)).append("\n");
                } catch (IOException e) {
                    sb.append("  (serialisation error: ").append(e.getMessage()).append(")\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns all sections that have a direct navigation edge to {@code target} —
     * choices, events, and {@code onEnter} script {@code navigateTo()} calls.
     */
    public static String inspectRefs(Adventure adventure, SectionGraph graph, int target) {
        Set<Integer> preds = graph.predecessors(target);

        StringBuilder sb = new StringBuilder();
        sb.append("== Inbound references to §").append(target).append(" ==\n\n");

        if (preds.isEmpty()) {
            sb.append("  none\n");
            return sb.toString();
        }

        for (int predNum : preds) {
            Section s = adventure.getSection(predNum);
            sb.append(String.format("  §%-4d  [%s]%n", predNum, s.type()));
        }
        return sb.toString();
    }

    /**
     * Returns all sections containing an event of the given type. {@code eventType}
     * is matched case-insensitively against the canonical names: ITEM_GAIN, ITEM_LOSS,
     * COMBAT, LUCK_TEST, SKILL_TEST, NAVIGATE, GOLD_CHANGE, STAT_CHANGE.
     *
     * @param itemFilter optional item name filter; only applies to ITEM_GAIN / ITEM_LOSS
     */
    public static String inspectByEvent(Adventure adventure, String eventType, String itemFilter) {
        Predicate<SectionEvent> matcher = eventMatcher(eventType, itemFilter);
        List<Section> matches = adventure.sections().stream()
            .filter(s -> s.events().stream().anyMatch(matcher))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("== Sections with event ").append(eventType.toUpperCase());
        if (itemFilter != null) sb.append(" [item=").append(itemFilter).append("]");
        sb.append(" ==\n\n");

        if (matches.isEmpty()) {
            sb.append("  none\n");
            return sb.toString();
        }

        for (Section s : matches) {
            String eventSummary = s.events().stream()
                .filter(matcher)
                .map(e -> eventSummary(e, itemFilter))
                .collect(Collectors.joining(", "));
            sb.append(String.format("  §%-4d  [%s]  %s%n", s.number(), s.type(), eventSummary));
        }
        return sb.toString();
    }

    /**
     * Returns all sections containing a choice with a condition whose class name
     * matches {@code conditionType} (case-insensitive, "Condition" suffix optional).
     * Example: "GoldCondition", "HasItemCondition", "HasItem".
     */
    public static String inspectByCondition(Adventure adventure, String conditionType) {
        String normalised = conditionType.replace("_", "").toLowerCase();

        List<Section> matches = adventure.sections().stream()
            .filter(s -> s.choices().stream()
                .anyMatch(c -> c.condition().isPresent() &&
                    conditionNameMatches(c.condition().get(), normalised)))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("== Sections with condition ").append(conditionType).append(" ==\n\n");

        if (matches.isEmpty()) {
            sb.append("  none\n");
            return sb.toString();
        }

        for (Section s : matches) {
            sb.append(String.format("  §%-4d  [%s]%n", s.number(), s.type()));
        }
        return sb.toString();
    }

    /**
     * Returns all NORMAL sections with no successors — sections the player can
     * enter but cannot leave. VICTORY and INSTANT_DEATH sections are terminal by
     * design and are excluded.
     *
     * <p>An optional chapter-id filter restricts the output to a declared chapter range
     * (read from {@code rawJson}). Pass {@code null} to inspect the whole adventure.
     */
    public static String inspectDeadEnds(Adventure adventure, SectionGraph graph) {
        List<Integer> deadEnds = adventure.sections().stream()
            .filter(s -> s.type() == SectionType.NORMAL)
            .filter(s -> graph.successors(s.number()).isEmpty())
            .map(Section::number)
            .sorted()
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("== Dead-end NORMAL sections ==\n\n");

        if (deadEnds.isEmpty()) {
            sb.append("  none \u2713\n");
        } else {
            for (int n : deadEnds) {
                sb.append(String.format("  §%-4d  NORMAL  (no successors)%n", n));
            }
        }
        return sb.toString();
    }

    /**
     * Returns all sections containing a {@code state.set/get/has/remove(varName)}
     * call in any script hook. Mirrors the section-level view in
     * {@link AdventureStateReport} but filters to a single named variable.
     */
    public static String inspectState(Adventure adventure, String varName) {
        Pattern pattern = Pattern.compile(
            "state\\.(set|get|has|remove)\\(['\"]" + Pattern.quote(varName) + "['\"]");

        List<String> lines = new ArrayList<>();
        for (Section s : adventure.sections()) {
            s.scripts().hooks().forEach((hook, script) -> {
                Matcher m = pattern.matcher(script);
                while (m.find()) {
                    lines.add(String.format("  §%-4d  %-12s  %s", s.number(), hook, m.group(1)));
                }
            });
            for (SectionEvent event : s.events()) {
                if (event instanceof CombatEvent ce) {
                    ce.scripts().hooks().forEach((hook, script) -> {
                        Matcher m = pattern.matcher(script);
                        while (m.find()) {
                            lines.add(String.format("  §%-4d  combat.%-6s  %s",
                                s.number(), hook, m.group(1)));
                        }
                    });
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("== Sections touching state variable '").append(varName).append("' ==\n\n");
        if (lines.isEmpty()) {
            sb.append("  none\n");
        } else {
            lines.forEach(l -> sb.append(l).append("\n"));
        }
        return sb.toString();
    }

    /**
     * Returns the raw JSON gate contracts for the named chapter. If {@code direction}
     * is {@code "in"}, returns only the entryGate. If {@code "out"}, returns only
     * exitGates. If {@code null}, returns the full gates object.
     */
    public static String inspectGate(JsonNode rawJson, String chapterId, String direction) {
        JsonNode chapter = findChapterJson(rawJson, chapterId);

        StringBuilder sb = new StringBuilder();

        if (chapter == null) {
            sb.append("== Gate: ").append(chapterId).append(" — not found ==\n");
            return sb.toString();
        }

        JsonNode gates = chapter.path("gates");

        if ("in".equalsIgnoreCase(direction)) {
            sb.append("== Gate in: ").append(chapterId).append(" ==\n\n");
            appendJson(sb, gates.path("entryGate"));
        } else if ("out".equalsIgnoreCase(direction)) {
            sb.append("== Gate out: ").append(chapterId).append(" ==\n\n");
            appendJson(sb, gates.path("exitGates"));
        } else {
            sb.append("== Gate contracts: ").append(chapterId).append(" ==\n\n");
            appendJson(sb, gates);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // CLI entry point
    // -------------------------------------------------------------------------

    /**
     * CLI entry point. First argument is always the adventure JSON file path.
     * Subsequent arguments specify the mode and parameters — see class-level Javadoc.
     */
    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 2) {
            System.err.println("Usage: AdventureSectionInspector <adventure.json> <mode> [params...]");
            System.err.println("Modes: <N> [N...] | <N-M> | --refs N | --has-event TYPE [--item NAME]");
            System.err.println("       --has-condition TYPE | --dead-ends [chId] | --state VAR | --gate chId [in|out]");
            System.exit(1);
        }

        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");

        JsonNode rawJson = new ObjectMapper().readTree(adventureFile.toFile());
        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        SectionGraph graph = SectionGraph.of(adventure);

        String output = dispatch(args, adventure, graph, rawJson);
        System.out.print(output);
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    private static String dispatch(String[] args, Adventure adventure,
                                   SectionGraph graph, JsonNode rawJson) {
        String mode = args[1];

        if (mode.equals("--refs")) {
            int target = Integer.parseInt(args[2]);
            return inspectRefs(adventure, graph, target);
        }

        if (mode.equals("--has-event")) {
            String eventType = args[2];
            String itemFilter = findFlag(args, "--item", 3);
            return inspectByEvent(adventure, eventType, itemFilter);
        }

        if (mode.equals("--has-condition")) {
            return inspectByCondition(adventure, args[2]);
        }

        if (mode.equals("--dead-ends")) {
            return inspectDeadEnds(adventure, graph);
        }

        if (mode.equals("--state")) {
            return inspectState(adventure, args[2]);
        }

        if (mode.equals("--gate")) {
            String chapterId = args[2];
            String direction = args.length > 3 ? args[3] : null;
            return inspectGate(rawJson, chapterId, direction);
        }

        // Default: section numbers (individual, list, or range)
        List<Integer> numbers = parseSectionNumbers(args, 1);
        return inspectSections(rawJson, numbers);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static JsonNode findSectionJson(JsonNode rawJson, int number) {
        for (JsonNode s : rawJson.path("sections")) {
            if (s.path("number").asInt(-1) == number) return s;
        }
        return null;
    }

    private static JsonNode findChapterJson(JsonNode rawJson, String chapterId) {
        for (JsonNode ch : rawJson.path("chapters")) {
            if (chapterId.equals(ch.path("id").asText())) return ch;
        }
        return null;
    }

    private static List<Integer> parseSectionNumbers(String[] args, int startIndex) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = startIndex; i < args.length; i++) {
            String token = args[i];
            if (token.contains("-") && !token.startsWith("-")) {
                // range: "91-97"
                String[] parts = token.split("-", 2);
                int from = Integer.parseInt(parts[0]);
                int to   = Integer.parseInt(parts[1]);
                for (int n = from; n <= to; n++) numbers.add(n);
            } else if (!token.startsWith("-")) {
                numbers.add(Integer.parseInt(token));
            }
        }
        return numbers;
    }

    private static String findFlag(String[] args, String flag, int startIndex) {
        for (int i = startIndex; i < args.length - 1; i++) {
            if (args[i].equals(flag)) return args[i + 1];
        }
        return null;
    }

    private static Predicate<SectionEvent> eventMatcher(String eventType, String itemFilter) {
        return switch (eventType.toUpperCase()) {
            case "ITEM_GAIN" -> e -> e instanceof ItemEvent ie
                && ie.action() == ItemAction.GAIN
                && (itemFilter == null || itemFilter.equalsIgnoreCase(ie.itemName()));
            case "ITEM_LOSS" -> e -> e instanceof ItemEvent ie
                && ie.action() == ItemAction.LOSS
                && (itemFilter == null || itemFilter.equalsIgnoreCase(ie.itemName()));
            case "COMBAT"      -> e -> e instanceof CombatEvent;
            case "LUCK_TEST"   -> e -> e instanceof LuckTestEvent;
            case "SKILL_TEST"  -> e -> e instanceof SkillTestEvent;
            case "NAVIGATE"    -> e -> e instanceof NavigateEvent;
            case "GOLD_CHANGE" -> e -> e instanceof GoldChangeEvent;
            case "STAT_CHANGE" -> e -> e instanceof StatChangeEvent;
            default            -> e -> false;
        };
    }

    private static boolean conditionNameMatches(
            com.tas.neo.domain.adventure.Condition cond, String normalised) {
        String className = cond.getClass().getSimpleName(); // e.g. "HasItemCondition"
        String classNorm = className.replace("Condition", "").toLowerCase(); // "hasitem"
        String classLower = className.toLowerCase();                          // "hasitemcondition"
        return classNorm.equals(normalised) || classLower.equals(normalised);
    }

    private static String eventSummary(SectionEvent event, String itemFilter) {
        return switch (event) {
            case ItemEvent e     -> e.action() + ": " + e.itemName() + " (qty:" + e.quantity() + ")";
            case CombatEvent e   -> "COMBAT →§" + e.successSection() + "/§" + e.failureSection();
            case LuckTestEvent e -> "LUCK_TEST →§" + e.successSection() + "/§" + e.failSection();
            case SkillTestEvent e -> "SKILL_TEST →§" + e.successSection() + "/§" + e.failSection();
            case NavigateEvent e -> "NAVIGATE →§" + e.targetSection();
            case GoldChangeEvent e -> "GOLD_CHANGE " + e.delta();
            case StatChangeEvent e -> "STAT_CHANGE " + e.attribute() + " " + e.delta();
            default              -> event.getClass().getSimpleName();
        };
    }

    private static void appendJson(StringBuilder sb, JsonNode node) {
        try {
            sb.append(JSON_WRITER.writeValueAsString(node)).append("\n");
        } catch (IOException e) {
            sb.append("(serialisation error: ").append(e.getMessage()).append(")\n");
        }
    }

    // STATE_CALL pattern kept local — AdventureStateReport has its own copy since
    // they serve different output shapes and neither warrants a shared utility at
    // current scale (2 uses < the smell-3 threshold).
    @SuppressWarnings("unused")
    private static final Pattern STATE_CALL_REF = STATE_CALL;
}
