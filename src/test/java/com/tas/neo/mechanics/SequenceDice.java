package com.tas.neo.mechanics;

import com.tas.neo.domain.Dice;

/**
 * Test double implementing {@link Dice} that returns values from a predefined sequence
 * in order. Throws {@link AssertionError} when the sequence is exhausted — that
 * signals a test did not control enough rolls.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class SequenceDice implements Dice {

    private final int[] values;
    private int index;

    public SequenceDice(int... values) {
        this.values = values;
        this.index = 0;
    }

    @Override
    public int roll(int sides) {
        if (index >= values.length) {
            throw new AssertionError("SequenceDice exhausted after " + values.length + " rolls");
        }
        return values[index++];
    }
}
