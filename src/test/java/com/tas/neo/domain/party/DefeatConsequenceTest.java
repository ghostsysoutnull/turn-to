package com.tas.neo.domain.party;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefeatConsequenceTest {

    @Test
    void gameOver_consequence_carries_message() {
        GameOverConsequence g = new GameOverConsequence("All is lost.");

        assertThat(g.message()).isEqualTo("All is lost.");
    }

    @Test
    void remove_consequence_carries_message() {
        RemoveConsequence r = new RemoveConsequence("Theron falls. You press on alone.");

        assertThat(r.message()).isEqualTo("Theron falls. You press on alone.");
    }

    @Test
    void navigate_consequence_carries_section_and_message() {
        NavigateConsequence n = new NavigateConsequence(404, "You flee in grief.");

        assertThat(n.section()).isEqualTo(404);
        assertThat(n.message()).isEqualTo("You flee in grief.");
    }

    @Test
    void sealed_interface_is_implemented_by_all_three() {
        DefeatConsequence a = new GameOverConsequence("a");
        DefeatConsequence b = new RemoveConsequence("b");
        DefeatConsequence c = new NavigateConsequence(1, "c");

        assertThat(a).isInstanceOf(GameOverConsequence.class);
        assertThat(b).isInstanceOf(RemoveConsequence.class);
        assertThat(c).isInstanceOf(NavigateConsequence.class);
    }
}
