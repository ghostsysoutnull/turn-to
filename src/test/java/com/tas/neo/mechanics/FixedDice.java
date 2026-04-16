package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;

/**
 * Test double implementing {@link Dice} that returns a fixed value for every roll.
 *
 * <p>Defined in {@code docs/design/06-testability.md}. Use when a test needs a single
 * deterministic outcome regardless of how many rolls are made.
 */
public class FixedDice implements Dice {

    private final int value;

    public FixedDice(int value) {
        this.value = value;
    }

    @Override
    public int roll(int sides) {
        return value;
    }
}
