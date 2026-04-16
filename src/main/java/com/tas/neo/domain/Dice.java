package com.tas.neo.domain;

public interface Dice {
    int roll(int sides);

    default int roll2d6() {
        return roll(6) + roll(6);
    }
}
