package com.tas.neo.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FileGameLogger implements GameLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String adventureId;
    private final Path sessionsDir;
    private final List<NavigationEntry> path = new ArrayList<>();
    private final List<OutputEvent> events = new ArrayList<>();
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
        path.add(entry);
    }

    @Override
    public void logEvent(OutputEvent event) {
        if (closed) return;
        events.add(event);
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
            String timestamp = LocalDateTime.now().format(TIMESTAMP);
            String base = adventureId + "-" + timestamp;
            writeTxt(sessionsDir.resolve(base + ".txt"));
            writeJson(sessionsDir.resolve(base + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeTxt(Path file) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
            w.println("=== " + adventureId.toUpperCase() + " ===");
            w.println("Started: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            w.println();
            for (NavigationEntry entry : path) {
                w.println("[" + entry.from() + "]");
                w.println("  → " + entry.via() + "  (→ " + entry.to() + ")");
                w.println();
            }
            w.println("─────────────────────────────────────");
            w.println("Result:      " + result);
            w.println("Steps:       " + path.size());
            w.println("Errors:      " + errors.size());
        }
    }

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

    /** Sets the session result string. Called by the engine on game end. */
    public void setResult(String result) {
        this.result = result;
    }
}
