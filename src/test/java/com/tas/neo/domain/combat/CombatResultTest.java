package com.tas.neo.domain.combat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombatResultTest {

    @Test
    void records_playerWon_roundsFought_and_staminaLost() {
        CombatResult result = new CombatResult(true, 4, 6);

        assertThat(result.playerWon()).isTrue();
        assertThat(result.roundsFought()).isEqualTo(4);
        assertThat(result.playerStaminaLost()).isEqualTo(6);
    }
}
