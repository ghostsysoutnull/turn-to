package com.tas.neo.domain.location;

import com.tas.neo.domain.adventure.Condition;
import com.tas.neo.domain.adventure.HasItemCondition;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PassageTest {

    @Test
    void passage_without_label_condition_or_toSection() {
        Passage p = new Passage(Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(p.label()).isEmpty();
        assertThat(p.condition()).isEmpty();
        assertThat(p.toSection()).isEmpty();
    }

    @Test
    void passage_with_label() {
        Passage p = new Passage(Optional.of("Leave the cave"), Optional.empty(), Optional.empty());

        assertThat(p.label()).contains("Leave the cave");
    }

    @Test
    void passage_with_condition() {
        Condition c = new HasItemCondition("Iron Key");
        Passage p = new Passage(Optional.empty(), Optional.of(c), Optional.empty());

        assertThat(p.condition()).contains(c);
    }

    @Test
    void passage_with_toSection_exits_to_that_section() {
        Passage p = new Passage(Optional.empty(), Optional.empty(), Optional.of(42));

        assertThat(p.toSection()).contains(42);
    }
}
