package com.tas.neo.domain.adventure;

/** Target for engine-injected system choices (inventory, quit, eat). */
public record SystemChoiceTarget(String action) implements ChoiceTarget {}
