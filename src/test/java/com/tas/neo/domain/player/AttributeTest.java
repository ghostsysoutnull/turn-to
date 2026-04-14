package com.tas.neo.domain.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeTest {

    @Test
    void modify_adds_positive_delta_within_bounds() {
        Attribute a = new Attribute(AttributeType.STAMINA, 10, 24);

        Attribute result = a.modify(5);

        assertThat(result.current()).isEqualTo(15);
        assertThat(result.max()).isEqualTo(24);
    }

    @Test
    void modify_subtracts_negative_delta_within_bounds() {
        Attribute a = new Attribute(AttributeType.STAMINA, 10, 24);

        Attribute result = a.modify(-4);

        assertThat(result.current()).isEqualTo(6);
    }

    @Test
    void modify_clamps_to_zero_when_reduced_below_minimum() {
        Attribute a = new Attribute(AttributeType.STAMINA, 3, 24);

        Attribute result = a.modify(-10);

        assertThat(result.current()).isEqualTo(0);
    }

    @Test
    void modify_clamps_to_max_when_raised_above_maximum() {
        Attribute a = new Attribute(AttributeType.STAMINA, 20, 24);

        Attribute result = a.modify(20);

        assertThat(result.current()).isEqualTo(24);
    }

    @Test
    void modify_returns_a_new_instance_leaving_original_unchanged() {
        Attribute original = new Attribute(AttributeType.SKILL, 9, 9);

        Attribute modified = original.modify(-2);

        assertThat(original.current()).as("Attribute is immutable").isEqualTo(9);
        assertThat(modified.current()).isEqualTo(7);
    }

    @Test
    void carries_its_attribute_type() {
        Attribute a = new Attribute(AttributeType.LUCK, 7, 12);

        assertThat(a.type()).isEqualTo(AttributeType.LUCK);
    }
}
