package com.tas.neo.domain;

public sealed interface StatDefinition
    permits FixedStatDefinition, DiceStatDefinition {}
