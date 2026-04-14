package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import java.util.List;

/**
 * Test double implementing {@link GameInput} that supplies a pre-written sequence of
 * choices. Throws {@link AssertionError} when the script is exhausted unexpectedly.
 *
 * <p>Defined in {@code docs/design/06-testability.md}.
 */
public class ScriptedInput implements GameInput {

    private final int[] choices;
    private int index;

    public ScriptedInput(int... choices) {
        this.choices = choices;
        this.index = 0;
    }

    @Override
    public int readChoice(List<Choice> available) {
        if (index >= choices.length) {
            throw new AssertionError("ScriptedInput exhausted after " + choices.length + " choices");
        }
        return choices[index++];
    }

    @Override
    public boolean readYesNo(String prompt) {
        if (index >= choices.length) {
            throw new AssertionError("ScriptedInput exhausted while reading yes/no for: " + prompt);
        }
        return choices[index++] != 0;
    }

    @Override
    public void waitForEnter() {
        // no-op in scripted mode
    }
}
