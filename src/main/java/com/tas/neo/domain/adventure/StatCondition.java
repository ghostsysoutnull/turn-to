package com.tas.neo.domain.adventure;

import com.tas.neo.domain.player.AttributeType;

public record StatCondition(AttributeType attribute, ComparisonType comparison, int threshold) implements Condition {}
