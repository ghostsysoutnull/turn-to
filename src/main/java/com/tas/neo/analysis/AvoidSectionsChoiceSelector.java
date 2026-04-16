package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Wraps another ChoiceSelector and filters out choices that lead to any of the
 * explicitly avoided section numbers before delegating. If all choices point to
 * avoided sections, the full list is passed through unchanged (forced navigation).
 *
 * <p>Composes naturally with SurvivalChoiceSelector:
 * <pre>
 *   new AvoidSectionsChoiceSelector(avoided,
 *       new SurvivalChoiceSelector(adventure, new RandomChoiceSelector()))
 * </pre>
 */
public class AvoidSectionsChoiceSelector implements ChoiceSelector {

    private final Set<Integer> avoided;
    private final ChoiceSelector delegate;

    public AvoidSectionsChoiceSelector(Set<Integer> avoided, ChoiceSelector delegate) {
        this.avoided = avoided;
        this.delegate = delegate;
    }

    @Override
    public int select(List<Choice> choices, SimulatedGameState state, Random random) {
        if (avoided.isEmpty()) {
            return delegate.select(choices, state, random);
        }

        List<Choice> safe = choices.stream()
            .filter(c -> !isAvoided(c))
            .toList();

        if (safe.isEmpty()) {
            // All choices are avoided — forced navigation, fall through
            return delegate.select(choices, state, random);
        }

        if (safe.size() == choices.size()) {
            // Nothing filtered — delegate directly
            return delegate.select(choices, state, random);
        }

        int safeIdx = delegate.select(safe, state, random);
        return choices.indexOf(safe.get(safeIdx));
    }

    private boolean isAvoided(Choice choice) {
        if (!(choice.target() instanceof SectionTarget t)) return false;
        return avoided.contains(t.sectionNumber());
    }
}
