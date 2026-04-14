package com.tas.neo.domain.party;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberStateTest {

    @Test
    void defines_three_states() {
        assertThat(MemberState.values())
            .containsExactlyInAnyOrder(
                MemberState.WAITING,
                MemberState.ACTIVE,
                MemberState.REMOVED
            );
    }
}
