package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;

import java.util.List;
import java.util.Random;

public interface ChoiceSelector {
    /**
     * Selects a choice from the given list and returns its index.
     *
     * @param choices non-empty list of available choices (all conditions already satisfied)
     * @param state   current simulated game state
     * @param random  seeded random for reproducibility
     * @return index into choices (0-based)
     */
    int select(List<Choice> choices, SimulatedGameState state, Random random);
}
