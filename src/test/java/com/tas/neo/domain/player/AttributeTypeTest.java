package com.tas.neo.domain.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeTypeTest {

    @Test
    void defines_three_attributes() {
        assertThat(AttributeType.values())
            .containsExactlyInAnyOrder(
                AttributeType.SKILL,
                AttributeType.STAMINA,
                AttributeType.LUCK
            );
    }
}
