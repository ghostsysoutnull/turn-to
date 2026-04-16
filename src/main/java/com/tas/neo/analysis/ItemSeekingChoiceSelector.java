package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.HasItemCondition;

import java.util.List;
import java.util.Random;

/**
 * Prefers choices that are gated by a satisfied HAS_ITEM condition (weight 2 vs 1).
 * The intent is to exercise item-gated paths more frequently, surfacing unreachable items.
 */
public class ItemSeekingChoiceSelector implements ChoiceSelector {

    @Override
    public int select(List<Choice> choices, SimulatedGameState state, Random random) {
        if (choices.size() == 1) return 0;

        int[] weights = new int[choices.size()];
        int totalWeight = 0;
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            int weight = 1;
            if (choice.condition().isPresent()
                    && choice.condition().get() instanceof HasItemCondition c
                    && state.hasItem(c.itemName())) {
                weight = 2;
            }
            weights[i] = weight;
            totalWeight += weight;
        }

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < choices.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return i;
        }
        return choices.size() - 1;
    }
}
