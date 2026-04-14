package com.tas.neo.domain.combat;

import java.util.Optional;

public record CombatOutcome(CombatOutcomeType type, Optional<Integer> navigateTo) {}
