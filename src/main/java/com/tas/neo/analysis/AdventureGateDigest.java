package com.tas.neo.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.loader.AdventureLoadException;
import com.tas.neo.loader.JsonAdventureLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Generates a compact gate contract digest for an adventure.
 *
 * <p>Prints every chapter's entry and exit gate contracts in a single view — the
 * full in/out chain without requiring agents to read individual chapter briefs.
 * Guaranteed items, possible items, forbidden items, and assumed state variables
 * are all shown.
 *
 * <p>Output is written to {@code adventures/<id>-gates.txt}.
 *
 * <p>Primary consumer: Consistency Check Agent (chain integrity verification) and
 * Adventure Architect Agent (scaffold review).
 *
 * <pre>
 * mvn exec:java -Dexec.mainClass=com.tas.neo.analysis.AdventureGateDigest \
 *   -Dexec.args="adventures/&lt;id&gt;.json"
 * </pre>
 */
public class AdventureGateDigest {

    public static String generate(Adventure adventure, JsonNode rawJson) {
        JsonNode chapters = rawJson.path("chapters");

        StringBuilder sb = new StringBuilder();
        sb.append("== Gate Contract Digest: ").append(adventure.id()).append(" ==\n");
        sb.append("Generated: ").append(LocalDate.now());

        if (chapters.isMissingNode() || !chapters.isArray() || chapters.isEmpty()) {
            sb.append("\n\n  (no chapters defined)\n");
            return sb.toString();
        }

        sb.append("  |  Chapters: ").append(chapters.size()).append("\n");

        for (JsonNode ch : chapters) {
            String id   = ch.path("id").asText("?");
            int from    = ch.path("sectionRange").path("from").asInt(-1);
            int to      = ch.path("sectionRange").path("to").asInt(-1);
            String range = from >= 0 && to >= 0
                ? "[\u00a7" + from + "\u2013\u00a7" + to + "]"
                : "[? – ?]";

            sb.append("\n").append(id.toUpperCase()).append("  ").append(range).append("\n");

            JsonNode gates    = ch.path("gates");
            JsonNode entryGate = gates.path("entryGate");
            JsonNode exitGates = gates.path("exitGates");

            // ---- IN ----
            if (entryGate.isNull() || entryGate.isMissingNode()) {
                sb.append("  IN   (adventure start \u2014 no entry contract)\n");
            } else {
                int entrySection = entryGate.path("entrySection").asInt(-1);
                sb.append("  IN   \u00a7").append(entrySection).append("\n");
                JsonNode in = entryGate.path("in");
                appendContractList(sb, "       required:    ", in.path("required"));
                appendContractList(sb, "       possible:    ", in.path("possible"));
                appendContractList(sb, "       state:       ", in.path("assumedState"));

                // Branches (multi-entry)
                JsonNode branches = entryGate.path("branches");
                if (branches.isArray()) {
                    for (JsonNode br : branches) {
                        int brEntry = br.path("entrySection").asInt(-1);
                        String label = br.path("label").asText("");
                        sb.append("       branch \u00a7").append(brEntry);
                        if (!label.isEmpty()) sb.append(" (").append(label).append(")");
                        sb.append("\n");
                    }
                }
            }

            // ---- OUT ----
            if (!exitGates.isArray() || exitGates.isEmpty()) {
                sb.append("  OUT  (final chapter \u2014 no exit contract)\n");
            } else {
                for (JsonNode eg : exitGates) {
                    int exitSection = eg.path("entrySection").asInt(-1);
                    String label    = eg.path("label").asText("");
                    sb.append("  OUT  \u00a7").append(exitSection);
                    if (!label.isEmpty()) sb.append(" (").append(label).append(")");
                    sb.append("\n");

                    JsonNode out = eg.path("out");
                    appendContractList(sb, "       guaranteed:  ", out.path("guaranteed"));
                    appendContractList(sb, "       possible:    ", out.path("possible"));
                    appendContractList(sb, "       forbidden:   ", out.path("forbidden"));
                    appendContractList(sb, "       state:       ", out.path("assumedState"));
                }
            }
        }

        return sb.toString();
    }

    public static void main(String[] args) throws AdventureLoadException, IOException {
        if (args.length < 1) {
            System.err.println("Usage: AdventureGateDigest <path-to-adventure.json>");
            System.exit(1);
        }
        Path adventureFile = Path.of(args[0]);
        Path adventuresDir = adventureFile.getParent();
        String adventureId = adventureFile.getFileName().toString().replace(".json", "");

        Adventure adventure = new JsonAdventureLoader(adventuresDir).load(adventureId);
        JsonNode rawJson    = new ObjectMapper().readTree(adventureFile.toFile());

        String digest = generate(adventure, rawJson);
        System.out.print(digest);

        Path out = adventuresDir.resolve(adventureId + "-gates.txt");
        Files.writeString(out, digest);
        System.err.println("Digest written to " + out);
    }

    // -------------------------------------------------------------------------

    private static void appendContractList(StringBuilder sb, String label, JsonNode array) {
        if (array.isArray() && !array.isEmpty()) {
            sb.append(label).append(array.get(0).asText()).append("\n");
            for (int i = 1; i < array.size(); i++) {
                sb.append(" ".repeat(label.length()))
                  .append(array.get(i).asText()).append("\n");
            }
        }
    }
}
