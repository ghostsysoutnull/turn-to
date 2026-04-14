package com.tas.neo.io;

import com.tas.neo.domain.adventure.Choice;
import java.util.List;

public interface GameInput {
    int readChoice(List<Choice> choices);
    boolean readYesNo(String prompt);
    void waitForEnter();
}
