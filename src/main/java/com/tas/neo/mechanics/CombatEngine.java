package com.tas.neo.mechanics;

import com.tas.neo.io.GameInput;
import com.tas.neo.io.GameOutput;

public class CombatEngine {

    private final Dice dice;
    private final GameInput input;
    private final GameOutput output;

    public CombatEngine(Dice dice, GameInput input, GameOutput output) {
        this.dice = dice;
        this.input = input;
        this.output = output;
    }

    public Dice dice() { return dice; }
    public GameInput input() { return input; }
    public GameOutput output() { return output; }
}
