package com.tas.neo.mechanics;

import java.util.Random;

public class SeededDice implements Dice {

    private final Random random;

    public SeededDice(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int roll(int sides) {
        return random.nextInt(sides) + 1;
    }
}
