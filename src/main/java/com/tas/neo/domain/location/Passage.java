package com.tas.neo.domain.location;

import com.tas.neo.domain.adventure.Condition;
import java.util.Optional;

public record Passage(
    Optional<String> label,
    Optional<Condition> condition,
    Optional<Integer> toSection
) {}
