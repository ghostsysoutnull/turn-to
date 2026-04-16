package com.tas.neo.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a human-readable text log and a machine-readable JSON log to the
 * {@code sessions/} directory at the end of each adventure run.
 *
 * <p>Navigation entries and output events are recorded in a single ordered list
 * to preserve their interleaving. The text log renderer groups events between
 * consecutive navigation entries to associate narrative text and results with
 * the correct location step.
 *
 * <p>The special {@code "START"} navigation entry is used to mark the initial
 * section before the player makes any choice. It is excluded from the JSON
 * path array and step count.
 */
public class FileGameLogger implements GameLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String adventureId;
    private final Path sessionsDir;
    private final LocalDateTime startTime = LocalDateTime.now();

    /** Single ordered stream of NavigationEntry and OutputEvent, preserving interleave order. */
    private final List<Object> log = new ArrayList<>();
    /** Navigation-only slice kept for JSON path array and step count. */
    private final List<NavigationEntry> path = new ArrayList<>();
    private final List<GameError> errors = new ArrayList<>();

    private String result = "ABANDONED";
    private boolean closed = false;

    public FileGameLogger(String adventureId, Path sessionsDir) {
        this.adventureId = adventureId;
        this.sessionsDir = sessionsDir;
    }

    @Override
    public void logNavigation(NavigationEntry entry) {
        if (closed) return;
        log.add(entry);
        if (!entry.from().equals("START")) {
            path.add(entry);
        }
    }

    @Override
    public void logEvent(OutputEvent event) {
        if (closed) return;
        log.add(event);
    }

    @Override
    public void logError(GameError error) {
        if (closed) return;
        errors.add(error);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            Files.createDirectories(sessionsDir);
            String timestamp = startTime.format(TIMESTAMP);
            String base = adventureId + "-" + timestamp;
            writeTxt(sessionsDir.resolve(base + ".txt"));
            writeJson(sessionsDir.resolve(base + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sets the session result string. Called by the engine on game end. */
    public void setResult(String result) {
        this.result = result;
    }

    // -------------------------------------------------------------------------
    // Text log
    // -------------------------------------------------------------------------

    private void writeTxt(Path file) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.print(formatTextLog());
        }
    }

    /** Package-private for testing. */
    String formatTextLog() {
        StringWriter sw = new StringWriter();
        PrintWriter w = new PrintWriter(sw);

        w.println("=== " + adventureId.toUpperCase() + " ===");
        w.println("Started: " + startTime.format(DISPLAY));
        w.println();

        // Split log into steps: one per NavigationEntry, collecting events until the next one.
        // Each step is rendered as: [Location] narrative \n  events \n  → departure
        List<NavigationEntry> navEntries = new ArrayList<>();
        List<List<OutputEvent>> eventsByStep = new ArrayList<>();

        List<OutputEvent> currentEvents = new ArrayList<>();
        for (Object item : log) {
            if (item instanceof NavigationEntry entry) {
                navEntries.add(entry);
                currentEvents = new ArrayList<>();
                eventsByStep.add(currentEvents);
            } else if (item instanceof OutputEvent event) {
                currentEvents.add(event);
            }
        }

        for (int i = 0; i < navEntries.size(); i++) {
            NavigationEntry nav = navEntries.get(i);
            List<OutputEvent> stepEvents = eventsByStep.get(i);

            // Render location header with narrative on the same line (if present)
            String locationLabel = formatLocationLabel(nav.to());
            String narrative = extractNarrative(stepEvents);

            if (narrative != null) {
                w.println("[" + locationLabel + "] " + narrative);
            } else {
                w.println("[" + locationLabel + "]");
            }

            // Render in-step events
            for (OutputEvent event : stepEvents) {
                switch (event) {
                    case OutputEvent.VictoryShown e ->
                        w.println("  VICTORY — " + e.message());
                    case OutputEvent.GameOverShown e ->
                        w.println("  GAME OVER — " + e.message());
                    case OutputEvent.ItemGained e ->
                        w.println("  ITEM_GAIN: " + e.itemName());
                    case OutputEvent.ItemLost e ->
                        w.println("  ITEM_LOSS: " + e.itemName());
                    case OutputEvent.StatChanged e -> {
                        String sign = e.delta() >= 0 ? "+" : "";
                        w.println("  STAT_CHANGE: " + e.attribute() + " " + sign + e.delta()
                            + " (now " + e.newValue() + ")");
                    }
                    case OutputEvent.CombatResolved e -> {
                        String result = e.playerWon() ? "PLAYER VICTORY" : "PLAYER DEFEAT";
                        w.println("  COMBAT: " + e.opponentName()
                            + " (SKILL " + e.opponentSkill() + ", STAMINA " + e.opponentStamina() + ")"
                            + " — " + result
                            + " (" + e.rounds() + " rounds, -" + e.staminaLost() + " STAMINA)");
                    }
                    default -> {} // other events (status, choices, etc.) not shown in log
                }
            }

            // Render departure: taken from the next navigation entry
            if (i + 1 < navEntries.size()) {
                NavigationEntry departure = navEntries.get(i + 1);
                String dest = formatDestination(departure.to());
                w.println("  → " + departure.via() + "  (→ " + dest + ")");
            }

            w.println();
        }

        w.println("─────────────────────────────────────");
        w.println("Result:      " + result);
        w.println("Steps:       " + path.size());
        w.println("Errors:      " + errors.size());

        return sw.toString();
    }

    /**
     * Formats a location string for display.
     * <ul>
     *   <li>{@code "section:42"} → {@code "Section 42"}</li>
     *   <li>{@code "grid:vault-dungeon:1,0,0"} → {@code "Grid: vault-dungeon (1,0,0)"}</li>
     *   <li>{@code "START"} → {@code "Start"}</li>
     * </ul>
     */
    private static String formatLocationLabel(String location) {
        if (location.startsWith("section:")) {
            return "Section " + location.substring("section:".length());
        }
        if (location.startsWith("grid:")) {
            String rest = location.substring("grid:".length()); // "id:x,y,z"
            int colon = rest.lastIndexOf(':');
            if (colon >= 0) {
                return "Grid: " + rest.substring(0, colon) + " (" + rest.substring(colon + 1) + ")";
            }
            return "Grid: " + rest;
        }
        return location;
    }

    /**
     * Formats a destination location for the departure line.
     * <ul>
     *   <li>{@code "section:2"} → {@code "Section 2"}</li>
     *   <li>{@code "grid:vault-dungeon:1,0,0"} → {@code "(1,0,0)"}</li>
     * </ul>
     */
    private static String formatDestination(String location) {
        if (location.startsWith("section:")) {
            return "Section " + location.substring("section:".length());
        }
        if (location.startsWith("grid:")) {
            String rest = location.substring("grid:".length());
            int colon = rest.lastIndexOf(':');
            if (colon >= 0) {
                return "(" + rest.substring(colon + 1) + ")";
            }
        }
        return location;
    }

    /** Returns the text of the first {@link OutputEvent.NarrativeShown} in the step, or null. */
    private static String extractNarrative(List<OutputEvent> events) {
        for (OutputEvent event : events) {
            if (event instanceof OutputEvent.NarrativeShown n) {
                return n.text();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // JSON log
    // -------------------------------------------------------------------------

    private void writeJson(Path file) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("adventureId", adventureId);
        root.put("result", result);
        root.put("stepsCount", path.size());

        ArrayNode pathArray = root.putArray("path");
        for (NavigationEntry entry : path) {
            ObjectNode node = pathArray.addObject();
            node.put("from", entry.from());
            node.put("to", entry.to());
            node.put("via", entry.via());
        }

        root.putArray("events");

        ArrayNode errorsArray = root.putArray("errors");
        for (GameError error : errors) {
            ObjectNode node = errorsArray.addObject();
            node.put("source", error.source());
            node.put("type", error.type());
            node.put("message", error.message());
        }

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
    }
}
