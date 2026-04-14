package com.tas.neo.domain.adventure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SectionTypeTest {

    @Test
    void defines_normal_victory_instant_death() {
        assertThat(SectionType.values())
            .containsExactlyInAnyOrder(
                SectionType.NORMAL,
                SectionType.VICTORY,
                SectionType.INSTANT_DEATH
            );
    }
}
