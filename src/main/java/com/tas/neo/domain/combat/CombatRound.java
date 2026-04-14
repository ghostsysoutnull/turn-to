package com.tas.neo.domain.combat;

public record CombatRound(
    int roundNumber,
    int playerAttackStrength,
    int creatureAttackStrength,
    int playerRoll,
    int creatureRoll,
    CombatRoundOutcome outcome
) {}
