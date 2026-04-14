package com.tas.neo.domain.adventure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonTypeTest {

    @Test
    void defines_at_least_and_at_most() {
        assertThat(ComparisonType.values())
            .containsExactlyInAnyOrder(ComparisonType.AT_LEAST, ComparisonType.AT_MOST);
    }
}
