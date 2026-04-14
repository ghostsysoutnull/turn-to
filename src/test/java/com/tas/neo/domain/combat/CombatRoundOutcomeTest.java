package com.tas.neo.domain.combat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombatRoundOutcomeTest {

    @Test
    void defines_player_wounds_creature_wounds_and_draw() {
        assertThat(CombatRoundOutcome.values())
            .containsExactlyInAnyOrder(
                CombatRoundOutcome.PLAYER_WOUNDS,
                CombatRoundOutcome.CREATURE_WOUNDS,
                CombatRoundOutcome.DRAW
            );
    }
}
