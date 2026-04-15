package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.ComparisonType;
import com.tas.neo.domain.adventure.Condition;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.LacksItemCondition;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.StatCondition;
import com.tas.neo.domain.adventure.StateEqualsCondition;
import com.tas.neo.domain.adventure.StateNotEqualsCondition;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.GoldChangeEvent;
import com.tas.neo.domain.adventure.event.ItemEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;
import com.tas.neo.domain.adventure.event.StatChangeEvent;
import com.tas.neo.domain.adventure.event.ItemAction;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;



/**
 * Generates a compact narrative digest for a single chapter of an adventure.
 *
 * <p>Each section is rendered as 3–5 lines: section number, type, and navigation
 * targets; a first-sentence excerpt of the narrative; events and script hooks in
 * terse notation; and any gated choices. This compresses a 45-section chapter from
 * ~800 lines of raw JSON to ~180 lines, while preserving all mechanically relevant
 * information.
 *
 * <p>Output is written to {@code adventures/<id>-digest-<chapterId>.txt}.
 *
 * <p>Primary consumer: Chapter Reviewer Agent (narrative pass).
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureSectionDigest \
 *   -Dexec.args="adventures/&lt;id&gt;.json &lt;chapterId&gt;"
 * </pre>
 */
public class AdventureSectionDigest {

    public static String generate(Adventure adventure, JsonNode rawJson, String chapterId) {
        JsonNode chapter = findChapter(rawJson, chapterId);
        if (chapter == null) {
            return "Chapter '" + chapterId + "' not found in " + adventure.id() + "\n";
        }

        int from = chapter.path("sectionRange").path("from").asInt(-1);
        int to   = chapter.path("sectionRange").path("to").asInt(-1);
        if (from < 0 || to < 0) {
            return "Chapter '" + chapterId + "' has no valid sectionRange\n";
        }

        int entry = chapter.path("gates").path("entryGate").path("entrySection").asInt(from);

        StringBuilder sb = new StringBuilder();
        sb.append("== Section Digest: ").append(adventure.id())
          .append(" / ").append(chapterId).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now());
        sb.append("  |  Range: §").append(from).append("–§").append(to);
        sb.append("  |  Entry: §").append(entry).append("\n\n");

        adventure.sections().stream()
            .filter(s -> s.number() >= from && s.number() <= to)
            .forEach(s -> appendSection(sb, s));

        return sb.toString();
    }

    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 2) {
            System.err.println("Usage: AdventureSectionDigest <path-to-adventure.json> <chapterId>");
            System.exit(1);
        }
        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");
        String chapterId   = args[1];

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        JsonNode rawJson    = new ObjectMapper().readTree(adventureFile.toFile());

        String digest = generate(adventure, rawJson, chapterId);
        System.out.print(digest);

        Path out = adventuresDir.resolve(adventureId + "-digest-" + chapterId + ".txt");
        Files.writeString(out, digest);
        System.err.println("Digest written to " + out);
    }

    // -------------------------------------------------------------------------

    private static JsonNode findChapter(JsonNode rawJson, String chapterId) {
        JsonNode chapters = rawJson.path("chapters");
        if (!chapters.isArray()) return null;
        for (JsonNode ch : chapters) {
            if (chapterId.equals(ch.path("id").asText())) return ch;
        }
        return null;
    }

    private static void appendSection(StringBuilder sb, Section s) {
        // Header: [§N] TYPE  →§A,§B
        List<Integer> targets = choiceTargets(s);
        String targetsStr = targets.isEmpty() ? ""
            : "  \u2192" + targets.stream().map(n -> "\u00a7" + n).collect(Collectors.joining(","));
        sb.append(String.format("[%c%d] %-14s%s%n", '\u00a7', s.number(), s.type(), targetsStr));

        // First sentence of narrative
        sb.append("  ").append(firstSentence(s.narrative())).append("\n");

        // Events
        if (!s.events().isEmpty()) {
            String evts = s.events().stream().map(AdventureSectionDigest::fmtEvent)
                           .collect(Collectors.joining("  "));
            sb.append("  events: ").append(evts).append("\n");
        }

        // Gated choices
        List<String> gated = new ArrayList<>();
        for (var choice : s.choices()) {
            choice.condition().ifPresent(cond -> {
                String target = choice.target() instanceof SectionTarget st
                    ? "\u00a7" + st.sectionNumber() : "?";
                gated.add("[" + fmtCond(cond) + "]\u2192" + target + ":\"" + choice.text() + "\"");
            });
        }
        if (!gated.isEmpty()) {
            sb.append("  conds:  ").append(String.join("  ", gated)).append("\n");
        }

        // Script hooks (names only — content is in state report)
        List<String> hooks = new ArrayList<>(s.scripts().hooks().keySet());
        if (!hooks.isEmpty()) {
            sb.append("  hooks:  ").append(String.join(", ", hooks)).append("\n");
        }

        sb.append("\n");
    }

    private static List<Integer> choiceTargets(Section s) {
        return s.choices().stream()
            .filter(c -> c.target() instanceof SectionTarget)
            .map(c -> ((SectionTarget) c.target()).sectionNumber())
            .distinct()
            .toList();
    }

    private static String firstSentence(String narrative) {
        if (narrative == null || narrative.isBlank()) return "(no narrative)";
        String flat = narrative.replace('\n', ' ').stripLeading();
        int end = flat.length();
        for (int i = 20; i < flat.length(); i++) {
            char c = flat.charAt(i);
            if (c == '.' || c == '!' || c == '?') { end = i + 1; break; }
        }
        String sentence = flat.substring(0, Math.min(end, 140)).trim();
        return sentence.length() < flat.trim().length() ? sentence + "..." : sentence;
    }

    private static String fmtEvent(SectionEvent event) {
        return switch (event) {
            case ItemEvent e -> (e.action() == ItemAction.GAIN ? "+" : "-") + e.itemName()
                               + (e.quantity() != 1 ? "\u00d7" + e.quantity() : "");
            case StatChangeEvent e -> e.attribute() + (e.delta() >= 0 ? "+" : "") + e.delta();
            case GoldChangeEvent e -> "GOLD" + (e.delta() >= 0 ? "+" : "") + e.delta();
            case NavigateEvent e -> "\u2192\u00a7" + e.targetSection();
            case LuckTestEvent e -> "LUCK\u2192\u00a7" + e.successSection() + "/\u00a7" + e.failSection();
            case SkillTestEvent e -> "SKILL_TEST\u2192\u00a7" + e.successSection() + "/\u00a7" + e.failSection();
            case CombatEvent e -> "COMBAT"
                + (e.successSection() > 0 ? "\u2192\u00a7" + e.successSection() : "")
                + (e.failureSection() > 0 ? "/\u00a7" + e.failureSection() : "");
            default -> event.getClass().getSimpleName();
        };
    }

    private static String fmtCond(Condition cond) {
        return switch (cond) {
            case HasItemCondition c -> "HAS:" + c.itemName();
            case LacksItemCondition c -> "LACKS:" + c.itemName();
            case StatCondition c -> c.attribute()
                + (c.comparison() == ComparisonType.AT_LEAST ? ">=" : "<=") + c.threshold();
            case GoldCondition c -> "GOLD>=" + c.minimum();
            case StateEqualsCondition c -> "STATE:" + c.key() + "=" + c.value();
            case StateNotEqualsCondition c -> "STATE:" + c.key() + "\u2260" + c.value();
            default -> cond.getClass().getSimpleName();
        };
    }
}
