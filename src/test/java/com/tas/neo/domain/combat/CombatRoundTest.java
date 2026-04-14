package com.tas.neo.domain.combat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombatRoundTest {

    @Test
    void records_all_fields() {
        CombatRound round = new CombatRound(
            3, 15, 12, 5, 4, CombatRoundOutcome.PLAYER_WOUNDS
        );

        assertThat(round.roundNumber()).isEqualTo(3);
        assertThat(round.playerAttackStrength()).isEqualTo(15);
        assertThat(round.creatureAttackStrength()).isEqualTo(12);
        assertThat(round.playerRoll()).isEqualTo(5);
        assertThat(round.creatureRoll()).isEqualTo(4);
        assertThat(round.outcome()).isEqualTo(CombatRoundOutcome.PLAYER_WOUNDS);
    }
}
