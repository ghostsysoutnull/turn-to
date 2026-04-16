package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;

import java.util.List;
import java.util.Random;

public class RandomChoiceSelector implements ChoiceSelector {

    @Override
    public int select(List<Choice> choices, SimulatedGameState state, Random random) {
        if (choices.size() == 1) return 0;
        return random.nextInt(choices.size());
    }
}
