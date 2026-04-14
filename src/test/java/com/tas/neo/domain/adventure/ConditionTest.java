package com.tas.neo.domain.adventure;

import com.tas.neo.domain.player.AttributeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionTest {

    @Test
    void hasItem_condition_records_item_name() {
        HasItemCondition c = new HasItemCondition("Iron Key");

        assertThat(c.itemName()).isEqualTo("Iron Key");
        assertThat((Condition) c).isInstanceOf(HasItemCondition.class);
    }

    @Test
    void lacksItem_condition_records_item_name() {
        LacksItemCondition c = new LacksItemCondition("Goblin Key");

        assertThat(c.itemName()).isEqualTo("Goblin Key");
    }

    @Test
    void statCondition_records_attribute_comparison_and_threshold() {
        StatCondition c = new StatCondition(AttributeType.SKILL, ComparisonType.AT_LEAST, 10);

        assertThat(c.attribute()).isEqualTo(AttributeType.SKILL);
        assertThat(c.comparison()).isEqualTo(ComparisonType.AT_LEAST);
        assertThat(c.threshold()).isEqualTo(10);
    }

    @Test
    void goldCondition_records_minimum() {
        GoldCondition c = new GoldCondition(5);

        assertThat(c.minimum()).isEqualTo(5);
    }

    @Test
    void partyStatCondition_records_all_fields() {
        PartyStatCondition c =
            new PartyStatCondition("theron", "HP", ComparisonType.AT_LEAST, 5);

        assertThat(c.memberId()).isEqualTo("theron");
        assertThat(c.statName()).isEqualTo("HP");
        assertThat(c.comparison()).isEqualTo(ComparisonType.AT_LEAST);
        assertThat(c.threshold()).isEqualTo(5);
    }

    @Test
    void partyMemberActiveCondition_records_member_id() {
        PartyMemberActiveCondition c = new PartyMemberActiveCondition("theron");

        assertThat(c.memberId()).isEqualTo("theron");
    }

    @Test
    void partyMemberWaitingCondition_records_member_id() {
        PartyMemberWaitingCondition c = new PartyMemberWaitingCondition("theron");

        assertThat(c.memberId()).isEqualTo("theron");
    }

    @Test
    void partyMemberRemovedCondition_records_member_id() {
        PartyMemberRemovedCondition c = new PartyMemberRemovedCondition("theron");

        assertThat(c.memberId()).isEqualTo("theron");
    }

    @Test
    void stateEquals_condition_records_key_and_value() {
        StateEqualsCondition c = new StateEqualsCondition("doorUnlocked", Boolean.TRUE);

        assertThat(c.key()).isEqualTo("doorUnlocked");
        assertThat(c.value()).isEqualTo(Boolean.TRUE);
    }

    @Test
    void stateNotEquals_condition_records_key_and_value() {
        StateNotEqualsCondition c = new StateNotEqualsCondition("cursed", Boolean.FALSE);

        assertThat(c.key()).isEqualTo("cursed");
        assertThat(c.value()).isEqualTo(Boolean.FALSE);
    }
}
