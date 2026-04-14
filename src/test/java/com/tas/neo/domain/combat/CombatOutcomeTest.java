package com.tas.neo.domain.combat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CombatOutcomeTest {

    @Test
    void victory_outcome_with_navigation_target() {
        CombatOutcome outcome = new CombatOutcome(CombatOutcomeType.VICTORY, Optional.of(100));

        assertThat(outcome.type()).isEqualTo(CombatOutcomeType.VICTORY);
        assertThat(outcome.navigateTo()).contains(100);
    }

    @Test
    void delegated_outcome_has_empty_navigation() {
        CombatOutcome outcome = new CombatOutcome(CombatOutcomeType.DELEGATED, Optional.empty());

        assertThat(outcome.type()).isEqualTo(CombatOutcomeType.DELEGATED);
        assertThat(outcome.navigateTo()).isEmpty();
    }

    @Test
    void combatOutcomeType_defines_all_four_values() {
        assertThat(CombatOutcomeType.values())
            .containsExactlyInAnyOrder(
                CombatOutcomeType.VICTORY,
                CombatOutcomeType.DEFEAT,
                CombatOutcomeType.FLED,
                CombatOutcomeType.DELEGATED
            );
    }
}
