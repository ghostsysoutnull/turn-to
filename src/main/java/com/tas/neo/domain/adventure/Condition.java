package com.tas.neo.domain.adventure;

public sealed interface Condition
    permits HasItemCondition, LacksItemCondition, StatCondition, GoldCondition,
            PartyStatCondition, PartyMemberActiveCondition,
            PartyMemberWaitingCondition, PartyMemberRemovedCondition,
            StateEqualsCondition, StateNotEqualsCondition {}
