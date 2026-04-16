package com.tas.neo.io;

import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.log.SessionLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double implementing {@link GameLogger} that accumulates all calls in memory
 * and exposes them as a {@link SessionLog}. Used in tests that assert on log content
 * or error entries.
 *
 * <p>Defined in {@code docs/design/06-testability.md} and
 * {@code docs/design/12-session-logging.md}.
 */
public class RecordingGameLogger implements GameLogger {

    private final List<NavigationEntry> path = new ArrayList<>();
    private final List<OutputEvent> events = new ArrayList<>();
    private final List<GameError> errors = new ArrayList<>();
    private String adventureId = "";
    private String result = "ABANDONED";
    private boolean closed = false;

    public SessionLog sessionLog() {
        return new SessionLog(
            adventureId,
            List.copyOf(path),
            List.copyOf(events),
            List.copyOf(errors),
            result,
            path.size()
        );
    }

    public List<GameError> errors() {
        return List.copyOf(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @Override
    public void logNavigation(NavigationEntry entry) {
        if (closed) return;
        if (!entry.from().equals("START")) {
            path.add(entry);
        }
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
        this.closed = true;
    }
}
