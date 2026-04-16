package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;
import java.util.Random;

public class RandomDice implements Dice {

    private final Random random = new Random();

    @Override
    public int roll(int sides) {
        return random.nextInt(sides) + 1;
    }
}
