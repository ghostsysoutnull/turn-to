package com.tas.neo.analysis;

public enum RunOutcome {
    VICTORY,
    INSTANT_DEATH,
    STUCK,
    CYCLE,
    /** Run terminated because the selected choice leads to a grid. Grid navigation is not simulated. */
    GRID_ENTRY
}
