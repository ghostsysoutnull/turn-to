package com.tas.neo.domain;

public record FixedStatDefinition(int initial, int max) implements StatDefinition {

    public int resolveInitial(Dice dice) {
        return initial;
    }

    public int resolveMax(Dice dice) {
        return max;
    }
}
