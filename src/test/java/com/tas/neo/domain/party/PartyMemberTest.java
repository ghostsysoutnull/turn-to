package com.tas.neo.domain.party;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartyMemberTest {

    private static PartyMember newMember(String id, String displayName, String lifeStat,
                                         MemberState state, int hp, int maxHp,
                                         DefeatConsequence onDefeat) {
        Map<String, PartyMemberStat> stats = new LinkedHashMap<>();
        stats.put(lifeStat, new PartyMemberStat(lifeStat, hp, maxHp));
        return new PartyMember(id, displayName, lifeStat, stats, onDefeat, state);
    }

    private static PartyMember activeMember() {
        return newMember("theron", "Theron", "HP", MemberState.ACTIVE, 10, 10,
            new RemoveConsequence("Theron falls."));
    }

    @Test
    void id_and_displayName_exposed() {
        PartyMember m = activeMember();

        assertThat(m.id()).isEqualTo("theron");
        assertThat(m.displayName()).isEqualTo("Theron");
    }

    @Test
    void isActive_true_only_when_state_is_ACTIVE() {
        PartyMember active = activeMember();
        PartyMember waiting = newMember("t", "T", "HP", MemberState.WAITING, 10, 10,
            new RemoveConsequence("-"));
        PartyMember removed = newMember("t", "T", "HP", MemberState.REMOVED, 10, 10,
            new RemoveConsequence("-"));

        assertThat(active.isActive()).isTrue();
        assertThat(waiting.isActive()).isFalse();
        assertThat(removed.isActive()).isFalse();
    }

    @Test
    void state_reflects_construction_value() {
        PartyMember waiting = newMember("t", "T", "HP", MemberState.WAITING, 10, 10,
            new RemoveConsequence("-"));

        assertThat(waiting.state()).isEqualTo(MemberState.WAITING);
    }

    @Test
    void setState_changes_state() {
        PartyMember m = activeMember();

        m.setState(MemberState.REMOVED);

        assertThat(m.state()).isEqualTo(MemberState.REMOVED);
    }

    @Test
    void getStat_returns_current_value_of_named_stat() {
        PartyMember m = activeMember();

        assertThat(m.getStat("HP")).isEqualTo(10);
    }

    @Test
    void getMaxStat_returns_max_of_named_stat() {
        PartyMember m = activeMember();

        assertThat(m.getMaxStat("HP")).isEqualTo(10);
    }

    @Test
    void hasStat_true_when_stat_declared() {
        PartyMember m = activeMember();

        assertThat(m.hasStat("HP")).isTrue();
        assertThat(m.hasStat("MANA")).isFalse();
    }

    @Test
    void modifyStat_adjusts_value_and_clamps_at_max() {
        PartyMember m = activeMember();

        m.modifyStat("HP", -4);
        assertThat(m.getStat("HP")).isEqualTo(6);

        m.modifyStat("HP", 100);
        assertThat(m.getStat("HP")).isEqualTo(10);
    }

    @Test
    void modifyStat_clamps_at_zero() {
        PartyMember m = activeMember();

        m.modifyStat("HP", -50);

        assertThat(m.getStat("HP")).isZero();
    }

    @Test
    void isDefeated_true_when_life_stat_is_zero() {
        PartyMember m = activeMember();

        m.modifyStat("HP", -100);

        assertThat(m.isDefeated()).isTrue();
    }

    @Test
    void isDefeated_false_when_life_stat_is_positive() {
        PartyMember m = activeMember();

        m.modifyStat("HP", -9);

        assertThat(m.isDefeated()).isFalse();
    }
}
