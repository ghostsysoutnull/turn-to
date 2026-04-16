package com.tas.neo.domain.combat;

import java.util.Optional;

public record CombatOutcome(CombatOutcomeType type, Optional<Integer> navigateTo,
                            Optional<CombatResult> combatResult) {

    /** Convenience constructor for outcomes without a combat summary (scripted, delegated). */
    public CombatOutcome(CombatOutcomeType type, Optional<Integer> navigateTo) {
        this(type, navigateTo, Optional.empty());
    }
}
