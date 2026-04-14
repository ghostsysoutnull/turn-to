package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import java.util.List;

public interface GameInput {
    int readChoice(List<Choice> choices);
    boolean readYesNo(String prompt);
    void waitForEnter();

    /**
     * Offers the player an optional luck test during combat.
     * Returns false (decline) if no input is available — allows ScriptedInput
     * with no remaining values to silently opt out rather than throw.
     */
    default boolean askTestLuck() {
        try {
            return readYesNo("Test your luck?");
        } catch (AssertionError ignored) {
            return false;
        }
    }
}
