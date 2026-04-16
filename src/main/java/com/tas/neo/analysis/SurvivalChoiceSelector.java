package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SectionType;

import java.util.List;
import java.util.Random;

/**
 * Wraps another ChoiceSelector and filters out choices that lead directly to
 * an INSTANT_DEATH section before delegating to it. If all choices are lethal
 * (forced death), the full list is passed through unchanged.
 */
public class SurvivalChoiceSelector implements ChoiceSelector {

    private final Adventure adventure;
    private final ChoiceSelector delegate;

    public SurvivalChoiceSelector(Adventure adventure, ChoiceSelector delegate) {
        this.adventure = adventure;
        this.delegate = delegate;
    }

    @Override
    public int select(List<Choice> choices, SimulatedGameState state, Random random) {
        List<Choice> safe = choices.stream()
            .filter(c -> !isInstantDeath(c))
            .toList();

        if (safe.isEmpty()) {
            // All choices are lethal — can't avoid death, fall through
            return delegate.select(choices, state, random);
        }

        if (safe.size() == choices.size()) {
            // No filtering needed — delegate directly with original list and indices
            return delegate.select(choices, state, random);
        }

        // Delegate on the safe subset, then map back to the original index
        int safeIdx = delegate.select(safe, state, random);
        Choice chosen = safe.get(safeIdx);
        return choices.indexOf(chosen);
    }

    private boolean isInstantDeath(Choice choice) {
        if (!(choice.target() instanceof SectionTarget t)) return false;
        try {
            return adventure.getSection(t.sectionNumber()).type() == SectionType.INSTANT_DEATH;
        } catch (Exception e) {
            return false;
        }
    }
}
