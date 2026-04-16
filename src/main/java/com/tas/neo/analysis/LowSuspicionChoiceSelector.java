package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Avoids choices whose target section is in the suspicion-raising set (weight 1 vs 3).
 */
public class LowSuspicionChoiceSelector implements ChoiceSelector {

    private final Set<Integer> suspicionSections;

    public LowSuspicionChoiceSelector(Set<Integer> suspicionSections) {
        this.suspicionSections = suspicionSections;
    }

    @Override
    public int select(List<Choice> choices, SimulatedGameState state, Random random) {
        if (choices.size() == 1) return 0;

        int[] weights = new int[choices.size()];
        int totalWeight = 0;
        for (int i = 0; i < choices.size(); i++) {
            int weight = isSuspicionRaising(choices.get(i)) ? 1 : 3;
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

    private boolean isSuspicionRaising(Choice choice) {
        return choice.target() instanceof SectionTarget t
            && suspicionSections.contains(t.sectionNumber());
    }
}
