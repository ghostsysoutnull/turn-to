package com.tas.neo.scripting;

import com.tas.neo.domain.party.DefeatConsequence;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.party.PartyMember;
import com.tas.neo.domain.party.PartyMemberStat;
import com.tas.neo.domain.party.RemoveConsequence;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for the concrete {@code PartyMemberProxy} implementation.
 *
 * <p>Covers the test strategy rows from {@code docs/design/07-scripting-engine.md}:
 * <ul>
 *   <li>All mutation calls are no-ops on an unknown id</li>
 *   <li>Boolean queries return false for an unknown id</li>
 *   <li>Numeric queries return 0 for an unknown id</li>
 * </ul>
 */
class PartyMemberProxyTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PartyMember activeMemberWithHp(int hp, int maxHp) {
        Map<String, PartyMemberStat> stats = new LinkedHashMap<>();
        stats.put("HP", new PartyMemberStat("HP", hp, maxHp));
        DefeatConsequence onDefeat = new RemoveConsequence("Member falls.");
        return new PartyMember("ally", "Ally", "HP", stats, onDefeat, MemberState.ACTIVE);
    }

    private static PartyMember waitingMember() {
        Map<String, PartyMemberStat> stats = new LinkedHashMap<>();
        stats.put("HP", new PartyMemberStat("HP", 8, 10));
        DefeatConsequence onDefeat = new RemoveConsequence("Member falls.");
        return new PartyMember("ally", "Ally", "HP", stats, onDefeat, MemberState.WAITING);
    }

    // -----------------------------------------------------------------------
    // Unknown id — null member proxy
    // -----------------------------------------------------------------------

    @Test
    void getStat_returns_zero_for_unknown_id() {
        PartyMemberProxy proxy = PartyMemberProxy.unknown("ghost");

        assertThat(proxy.getStat("HP"))
            .as("getStat must return 0 for an unknown party member id")
            .isEqualTo(0);
    }

    @Test
    void getMaxStat_returns_zero_for_unknown_id() {
        PartyMemberProxy proxy = PartyMemberProxy.unknown("ghost");

        assertThat(proxy.getMaxStat("HP"))
            .as("getMaxStat must return 0 for an unknown party member id")
            .isEqualTo(0);
    }

    @Test
    void isActive_returns_false_for_unknown_id() {
        PartyMemberProxy proxy = PartyMemberProxy.unknown("ghost");

        assertThat(proxy.isActive())
            .as("isActive must return false for an unknown party member id")
            .isFalse();
    }

    @Test
    void isDefeated_returns_false_for_unknown_id() {
        PartyMemberProxy proxy = PartyMemberProxy.unknown("ghost");

        assertThat(proxy.isDefeated())
            .as("isDefeated must return false for an unknown party member id")
            .isFalse();
    }

    @Test
    void modifyStat_does_not_throw_for_unknown_id() {
        PartyMemberProxy proxy = PartyMemberProxy.unknown("ghost");

        assertThatCode(() -> proxy.modifyStat("HP", -3))
            .as("modifyStat must be a silent no-op on an unknown party member id")
            .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Known member — delegates to PartyMember
    // -----------------------------------------------------------------------

    @Test
    void getStat_delegates_to_member_for_known_id() {
        PartyMember member = activeMemberWithHp(7, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.getStat("HP"))
            .as("getStat must return the member's current stat value")
            .isEqualTo(7);
    }

    @Test
    void getMaxStat_delegates_to_member_for_known_id() {
        PartyMember member = activeMemberWithHp(7, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.getMaxStat("HP"))
            .as("getMaxStat must return the member's maximum stat value")
            .isEqualTo(10);
    }

    @Test
    void isActive_returns_true_for_active_member() {
        PartyMember member = activeMemberWithHp(10, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.isActive())
            .as("isActive must return true for an ACTIVE member")
            .isTrue();
    }

    @Test
    void isActive_returns_false_for_waiting_member() {
        PartyMember member = waitingMember();
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.isActive())
            .as("isActive must return false for a WAITING member")
            .isFalse();
    }

    @Test
    void isDefeated_returns_false_when_hp_above_zero() {
        PartyMember member = activeMemberWithHp(5, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.isDefeated())
            .as("isDefeated must return false when life stat is above zero")
            .isFalse();
    }

    @Test
    void isDefeated_returns_true_when_hp_is_zero() {
        PartyMember member = activeMemberWithHp(0, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        assertThat(proxy.isDefeated())
            .as("isDefeated must return true when life stat is depleted")
            .isTrue();
    }

    @Test
    void modifyStat_updates_member_stat() {
        PartyMember member = activeMemberWithHp(10, 10);
        PartyMemberProxy proxy = PartyMemberProxy.of(member);

        proxy.modifyStat("HP", -3);

        assertThat(proxy.getStat("HP"))
            .as("modifyStat must apply delta to the member's stat")
            .isEqualTo(7);
    }
}
