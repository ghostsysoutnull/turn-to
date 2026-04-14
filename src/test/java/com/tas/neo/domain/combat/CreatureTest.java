package com.tas.neo.domain.combat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreatureTest {

    @Test
    void records_name_skill_and_stamina() {
        Creature goblin = new Creature("Goblin", 5, 6);

        assertThat(goblin.name()).isEqualTo("Goblin");
        assertThat(goblin.skill()).isEqualTo(5);
        assertThat(goblin.stamina()).isEqualTo(6);
    }

    @Test
    void wound_returns_new_creature_with_reduced_stamina() {
        Creature goblin = new Creature("Goblin", 5, 6);

        Creature wounded = goblin.wound(2);

        assertThat(wounded.stamina()).isEqualTo(4);
        assertThat(wounded.skill()).isEqualTo(5);
        assertThat(wounded.name()).isEqualTo("Goblin");
    }

    @Test
    void wound_does_not_mutate_original() {
        Creature goblin = new Creature("Goblin", 5, 6);

        goblin.wound(2);

        assertThat(goblin.stamina()).as("Creature is immutable").isEqualTo(6);
    }

    @Test
    void wound_clamps_stamina_at_zero() {
        Creature goblin = new Creature("Goblin", 5, 6);

        Creature wounded = goblin.wound(100);

        assertThat(wounded.stamina()).isZero();
    }

    @Test
    void isAlive_true_when_stamina_positive() {
        Creature goblin = new Creature("Goblin", 5, 6);

        assertThat(goblin.isAlive()).isTrue();
    }

    @Test
    void isAlive_false_when_stamina_zero() {
        Creature goblin = new Creature("Goblin", 5, 0);

        assertThat(goblin.isAlive()).isFalse();
    }
}
