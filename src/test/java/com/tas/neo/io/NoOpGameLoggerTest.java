package com.tas.neo.io;

import com.tas.neo.domain.log.GameError;
import com.tas.neo.domain.log.NavigationEntry;
import com.tas.neo.domain.log.PlayerSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link NoOpGameLogger}.
 *
 * <p>Covers the NoOpGameLogger row from the test strategy table in
 * {@code docs/design/12-session-logging.md}: all calls succeed silently.
 */
class NoOpGameLoggerTest {

    private final NoOpGameLogger logger = new NoOpGameLogger();

    @Test
    void logNavigation_succeeds_silently_for_section_entry() {
        NavigationEntry entry = new NavigationEntry("section:1", "section:2", "Enter the mountain");

        assertThatCode(() -> logger.logNavigation(entry))
            .as("logNavigation must not throw for a section NavigationEntry")
            .doesNotThrowAnyException();
    }

    @Test
    void logNavigation_succeeds_silently_for_grid_entry() {
        NavigationEntry entry = new NavigationEntry(
            "grid:dungeon-level-1:0,0,0",
            "grid:dungeon-level-1:1,0,0",
            "Go east"
        );

        assertThatCode(() -> logger.logNavigation(entry))
            .as("logNavigation must not throw for a grid NavigationEntry")
            .doesNotThrowAnyException();
    }

    @Test
    void logEvent_succeeds_silently() {
        OutputEvent event = new OutputEvent.NarrativeShown(1, "You stand in a dark cave.");

        assertThatCode(() -> logger.logEvent(event))
            .as("logEvent must not throw for any OutputEvent")
            .doesNotThrowAnyException();
    }

    @Test
    void logError_succeeds_silently() {
        PlayerSnapshot snapshot = new PlayerSnapshot("section:42", 8, 9, 5, 3,
                                                      List.of("Iron Key"));
        GameError error = new GameError("section-42-onEnter", "ScriptError",
                                        "attempt to index nil", snapshot);

        assertThatCode(() -> logger.logError(error))
            .as("logError must not throw for a GameError")
            .doesNotThrowAnyException();
    }

    @Test
    void close_succeeds_silently() {
        assertThatCode(() -> logger.close())
            .as("close must not throw")
            .doesNotThrowAnyException();
    }

    @Test
    void all_calls_after_close_succeed_silently() {
        logger.close();

        NavigationEntry entry = new NavigationEntry("section:1", "section:2", "Go");
        OutputEvent event = new OutputEvent.MessageShown("Hello");
        PlayerSnapshot snapshot = new PlayerSnapshot("section:1", 10, 10, 10, 0, List.of());
        GameError error = new GameError("src", "NavigationError", "msg", snapshot);

        assertThatCode(() -> {
            logger.logNavigation(entry);
            logger.logEvent(event);
            logger.logError(error);
            logger.close();
        }).as("all calls after close must not throw")
          .doesNotThrowAnyException();
    }
}
