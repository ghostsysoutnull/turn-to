package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Condition;
import com.tas.neo.domain.adventure.ComparisonType;
import com.tas.neo.domain.adventure.GoldCondition;
import com.tas.neo.domain.adventure.HasItemCondition;
import com.tas.neo.domain.adventure.LacksItemCondition;
import com.tas.neo.domain.adventure.PartyMemberActiveCondition;
import com.tas.neo.domain.adventure.PartyMemberRemovedCondition;
import com.tas.neo.domain.adventure.PartyMemberWaitingCondition;
import com.tas.neo.domain.adventure.PartyStatCondition;
import com.tas.neo.domain.adventure.StatCondition;
import com.tas.neo.domain.adventure.StateEqualsCondition;
import com.tas.neo.domain.adventure.StateNotEqualsCondition;

public class ConditionEvaluator {

    public static boolean evaluate(Condition condition, SimulatedGameState state) {
        return switch (condition) {
            case HasItemCondition c -> state.hasItem(c.itemName());
            case LacksItemCondition c -> !state.hasItem(c.itemName());
            case GoldCondition c -> state.gold() >= c.minimum();
            case StatCondition c -> {
                int value = state.stat(c.attribute().name());
                yield c.comparison() == ComparisonType.AT_LEAST
                    ? value >= c.threshold()
                    : value <= c.threshold();
            }
            case StateEqualsCondition c -> {
                Object stored = state.scriptState().get(c.key());
                yield stored != null && stored.equals(c.value());
            }
            case StateNotEqualsCondition c -> {
                Object stored = state.scriptState().get(c.key());
                yield stored == null || !stored.equals(c.value());
            }
            // Party conditions — not tracked in simulation; default to false
            case PartyMemberActiveCondition ignored -> false;
            case PartyMemberWaitingCondition ignored -> false;
            case PartyMemberRemovedCondition ignored -> false;
            case PartyStatCondition ignored -> false;
        };
    }
}
