package com.tas.neo.engine;

import com.tas.neo.domain.log.SessionLog;
import com.tas.neo.io.RecordingOutput;
import com.tas.neo.scripting.AdventureScriptState;
import java.util.Optional;

/**
 * The result of a {@link ScenarioRunner} run.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public record ScenarioResult(
    GameState finalState,
    RecordingOutput output,
    SessionLog sessionLog,
    boolean victory,
    boolean gameOver,
    AdventureScriptState scriptState
) {

    /** Returns the final section number, or -1 if the player is in a grid at end. */
    public int finalSection() {
        if (finalState.isInGrid()) {
            return -1;
        }
        if (finalState.currentSection() == null) {
            return -1;
        }
        return finalState.currentSection().number();
    }

    public boolean isInGrid() {
        return finalState.isInGrid();
    }

    public Optional<String> finalGridId() {
        return finalState.currentGrid().map(g -> g.id());
    }
}
