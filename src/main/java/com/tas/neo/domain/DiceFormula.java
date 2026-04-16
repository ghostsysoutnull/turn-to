package com.tas.neo.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DiceFormula(int diceCount, int diceSides, int modifier) {

    private static final Pattern PATTERN =
        Pattern.compile("^(\\d+)d(\\d+)([+-]\\d+)?$");

    public static DiceFormula parse(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("Dice expression must not be empty");
        }
        Matcher m = PATTERN.matcher(expression.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid dice expression: " + expression);
        }
        int diceCount = Integer.parseInt(m.group(1));
        int diceSides = Integer.parseInt(m.group(2));
        int modifier = 0;
        if (m.group(3) != null) {
            modifier = Integer.parseInt(m.group(3));
        }
        return new DiceFormula(diceCount, diceSides, modifier);
    }

    public int roll(Dice dice) {
        int total = 0;
        for (int i = 0; i < diceCount; i++) {
            total += dice.roll(diceSides);
        }
        return total + modifier;
    }
}
