package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.ComparisonType;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.LacksItemCondition;
import com.tas.neo.domain.adventure.PartyMemberActiveCondition;
import com.tas.neo.domain.adventure.PartyMemberRemovedCondition;
import com.tas.neo.domain.adventure.PartyMemberWaitingCondition;
import com.tas.neo.domain.adventure.StatCondition;
import com.tas.neo.domain.adventure.StateEqualsCondition;
import com.tas.neo.domain.adventure.StateNotEqualsCondition;
import com.tas.neo.domain.party.MemberState;
import com.tas.neo.domain.player.AttributeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    // -------------------------------------------------------------------------
    // HasItemCondition
    // -------------------------------------------------------------------------

    @Test
    void hasItem_true_when_item_in_inventory() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Sword");
        assertThat(ConditionEvaluator.evaluate(new HasItemCondition("Sword"), state))
            .as("HasItemCondition must be true when item is in inventory")
            .isTrue();
    }

    @Test
    void hasItem_false_when_item_absent() {
        assertThat(ConditionEvaluator.evaluate(new HasItemCondition("Shield"),
                new SimulatedGameState()))
            .as("HasItemCondition must be false when item is not in inventory")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // LacksItemCondition
    // -------------------------------------------------------------------------

    @Test
    void lacksItem_true_when_item_absent() {
        assertThat(ConditionEvaluator.evaluate(new LacksItemCondition("Map"),
                new SimulatedGameState()))
            .as("LacksItemCondition must be true when item is not in inventory")
            .isTrue();
    }

    @Test
    void lacksItem_false_when_item_present() {
        SimulatedGameState state = new SimulatedGameState();
        state.addItem("Map");
        assertThat(ConditionEvaluator.evaluate(new LacksItemCondition("Map"), state))
            .as("LacksItemCondition must be false when item is in inventory")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // GoldCondition
    // -------------------------------------------------------------------------

    @Test
    void gold_true_when_gold_meets_minimum() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(10);
        assertThat(ConditionEvaluator.evaluate(new GoldCondition(10), state))
            .as("GoldCondition must be true when gold equals the minimum")
            .isTrue();
    }

    @Test
    void gold_true_when_gold_exceeds_minimum() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(15);
        assertThat(ConditionEvaluator.evaluate(new GoldCondition(10), state))
            .as("GoldCondition must be true when gold exceeds the minimum")
            .isTrue();
    }

    @Test
    void gold_false_when_gold_below_minimum() {
        SimulatedGameState state = new SimulatedGameState();
        state.modifyGold(3);
        assertThat(ConditionEvaluator.evaluate(new GoldCondition(10), state))
            .as("GoldCondition must be false when gold is below the minimum")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // StatCondition
    // -------------------------------------------------------------------------

    @Test
    void stat_atLeast_true_when_stat_meets_threshold() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("SKILL", 10);
        assertThat(ConditionEvaluator.evaluate(
                new StatCondition(AttributeType.SKILL, ComparisonType.AT_LEAST, 10), state))
            .as("StatCondition AT_LEAST must be true when stat equals threshold")
            .isTrue();
    }

    @Test
    void stat_atLeast_false_when_stat_below_threshold() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("SKILL", 8);
        assertThat(ConditionEvaluator.evaluate(
                new StatCondition(AttributeType.SKILL, ComparisonType.AT_LEAST, 10), state))
            .as("StatCondition AT_LEAST must be false when stat is below threshold")
            .isFalse();
    }

    @Test
    void stat_atMost_true_when_stat_at_or_below_threshold() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("STAMINA", 5);
        assertThat(ConditionEvaluator.evaluate(
                new StatCondition(AttributeType.STAMINA, ComparisonType.AT_MOST, 10), state))
            .as("StatCondition AT_MOST must be true when stat is below threshold")
            .isTrue();
    }

    @Test
    void stat_atMost_false_when_stat_exceeds_threshold() {
        SimulatedGameState state = new SimulatedGameState();
        state.setStat("STAMINA", 15);
        assertThat(ConditionEvaluator.evaluate(
                new StatCondition(AttributeType.STAMINA, ComparisonType.AT_MOST, 10), state))
            .as("StatCondition AT_MOST must be false when stat exceeds threshold")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // StateEqualsCondition
    // -------------------------------------------------------------------------

    @Test
    void stateEquals_true_when_variable_matches_integer_value() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("suspicion", 3);
        assertThat(ConditionEvaluator.evaluate(new StateEqualsCondition("suspicion", 3), state))
            .as("StateEqualsCondition must be true when integer variable matches")
            .isTrue();
    }

    @Test
    void stateEquals_false_when_variable_does_not_match() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("suspicion", 5);
        assertThat(ConditionEvaluator.evaluate(new StateEqualsCondition("suspicion", 3), state))
            .as("StateEqualsCondition must be false when integer variable differs")
            .isFalse();
    }

    @Test
    void stateEquals_true_when_boolean_variable_matches() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("satchelOpened", true);
        assertThat(ConditionEvaluator.evaluate(
                new StateEqualsCondition("satchelOpened", true), state))
            .as("StateEqualsCondition must be true when boolean variable matches")
            .isTrue();
    }

    @Test
    void stateEquals_false_when_variable_absent() {
        assertThat(ConditionEvaluator.evaluate(
                new StateEqualsCondition("missing", true), new SimulatedGameState()))
            .as("StateEqualsCondition must be false when variable is not set")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // StateNotEqualsCondition
    // -------------------------------------------------------------------------

    @Test
    void stateNotEquals_true_when_variable_differs() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("flag", false);
        assertThat(ConditionEvaluator.evaluate(new StateNotEqualsCondition("flag", true), state))
            .as("StateNotEqualsCondition must be true when variable value differs")
            .isTrue();
    }

    @Test
    void stateNotEquals_true_when_variable_absent() {
        assertThat(ConditionEvaluator.evaluate(
                new StateNotEqualsCondition("absent", true), new SimulatedGameState()))
            .as("StateNotEqualsCondition must be true when variable is not set")
            .isTrue();
    }

    @Test
    void stateNotEquals_false_when_variable_matches() {
        SimulatedGameState state = new SimulatedGameState();
        state.scriptState().set("flag", true);
        assertThat(ConditionEvaluator.evaluate(new StateNotEqualsCondition("flag", true), state))
            .as("StateNotEqualsCondition must be false when variable value matches")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // PartyMemberActiveCondition
    // -------------------------------------------------------------------------

    @Test
    void partyMemberActive_true_when_member_is_active() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("scout", MemberState.ACTIVE);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberActiveCondition("scout"), state))
            .as("PartyMemberActiveCondition must be true when member state is ACTIVE")
            .isTrue();
    }

    @Test
    void partyMemberActive_false_when_member_is_waiting() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("scout", MemberState.WAITING);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberActiveCondition("scout"), state))
            .as("PartyMemberActiveCondition must be false when member state is WAITING")
            .isFalse();
    }

    @Test
    void partyMemberActive_false_when_member_unknown() {
        assertThat(ConditionEvaluator.evaluate(
                new PartyMemberActiveCondition("unknown"), new SimulatedGameState()))
            .as("PartyMemberActiveCondition must be false when member is not tracked")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // PartyMemberWaitingCondition
    // -------------------------------------------------------------------------

    @Test
    void partyMemberWaiting_true_when_member_is_waiting() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("guide", MemberState.WAITING);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberWaitingCondition("guide"), state))
            .as("PartyMemberWaitingCondition must be true when member state is WAITING")
            .isTrue();
    }

    @Test
    void partyMemberWaiting_false_when_member_is_active() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("guide", MemberState.ACTIVE);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberWaitingCondition("guide"), state))
            .as("PartyMemberWaitingCondition must be false when member state is ACTIVE")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // PartyMemberRemovedCondition
    // -------------------------------------------------------------------------

    @Test
    void partyMemberRemoved_true_when_member_is_removed() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("soldier", MemberState.REMOVED);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberRemovedCondition("soldier"), state))
            .as("PartyMemberRemovedCondition must be true when member state is REMOVED")
            .isTrue();
    }

    @Test
    void partyMemberRemoved_false_when_member_is_active() {
        SimulatedGameState state = new SimulatedGameState();
        state.initPartyMember("soldier", MemberState.ACTIVE);
        assertThat(ConditionEvaluator.evaluate(new PartyMemberRemovedCondition("soldier"), state))
            .as("PartyMemberRemovedCondition must be false when member state is ACTIVE")
            .isFalse();
    }
}
