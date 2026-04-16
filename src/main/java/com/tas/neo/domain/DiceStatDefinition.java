package com.tas.neo.domain;

import java.util.OptionalInt;

public record DiceStatDefinition(DiceFormula formula, OptionalInt fixedMax) implements StatDefinition {

    /** Rolls the formula and caches the result so resolveMax returns the same value. */
    public int resolveInitial(Dice dice) {
        return formula.roll(dice);
    }

    /**
     * Returns fixedMax when present; otherwise returns the same roll as resolveInitial.
     * Callers must use the value returned by resolveInitial as the max when fixedMax is absent.
     */
    public int resolveMax(Dice dice) {
        if (fixedMax.isPresent()) {
            return fixedMax.getAsInt();
        }
        return formula.roll(dice);
    }
}
