package com.tas.neo.analysis;

import java.util.Map;
import java.util.OptionalDouble;

public record StateSummary(OptionalDouble average, int min, int max, Map<Object, Integer> valueCounts) {}
