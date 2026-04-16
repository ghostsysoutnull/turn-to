package com.tas.neo.analysis;

import com.tas.neo.domain.Dice;

import java.util.OptionalInt;

public record RunConfiguration(
        int runs,
        ChoiceSelector choiceSelector,
        Dice dice,
        long seed,
        int maxVisitsPerSection,
        OptionalInt fixedSkill,
        OptionalInt fixedStamina
) {}
