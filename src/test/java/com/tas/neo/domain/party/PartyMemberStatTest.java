package com.tas.neo.domain.party;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartyMemberStatTest {

    @Test
    void modify_adds_delta_within_bounds() {
        PartyMemberStat stat = new PartyMemberStat("HP", 10, 20);

        PartyMemberStat result = stat.modify(5);

        assertThat(result.current()).isEqualTo(15);
        assertThat(result.max()).isEqualTo(20);
    }

    @Test
    void modify_clamps_to_zero_when_reduced_below_minimum() {
        PartyMemberStat stat = new PartyMemberStat("HP", 3, 20);

        PartyMemberStat result = stat.modify(-10);

        assertThat(result.current()).isEqualTo(0);
    }

    @Test
    void modify_clamps_to_max_when_raised_above_maximum() {
        PartyMemberStat stat = new PartyMemberStat("HP", 10, 20);

        PartyMemberStat result = stat.modify(100);

        assertThat(result.current()).isEqualTo(20);
    }

    @Test
    void isDepleted_true_when_current_is_zero() {
        PartyMemberStat stat = new PartyMemberStat("HP", 0, 20);

        assertThat(stat.isDepleted()).isTrue();
    }

    @Test
    void isDepleted_false_when_current_is_positive() {
        PartyMemberStat stat = new PartyMemberStat("HP", 1, 20);

        assertThat(stat.isDepleted()).isFalse();
    }

    @Test
    void display_returns_current_slash_max_when_below_max() {
        PartyMemberStat stat = new PartyMemberStat("HP", 7, 20);

        assertThat(stat.display()).isEqualTo("7/20");
    }

    @Test
    void display_returns_current_only_when_at_max() {
        PartyMemberStat stat = new PartyMemberStat("HP", 20, 20);

        assertThat(stat.display()).isEqualTo("20");
    }

    @Test
    void modify_returns_new_instance_leaving_original_unchanged() {
        PartyMemberStat original = new PartyMemberStat("HP", 10, 20);

        PartyMemberStat modified = original.modify(-4);

        assertThat(original.current()).isEqualTo(10);
        assertThat(modified.current()).isEqualTo(6);
    }
}
