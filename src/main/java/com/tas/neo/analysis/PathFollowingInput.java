package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Choice;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.SystemChoiceTarget;
import com.tas.neo.io.GameInput;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link GameInput} implementation that navigates a pre-planned path through an
 * adventure by target section number rather than by choice index.
 *
 * <p>At each {@link #readChoice} call the input takes the next section number from
 * its path and returns the 1-based index of whichever presented choice leads there.
 * System choices ({@code SystemChoiceTarget}) and grid choices ({@code GridTarget})
 * are skipped during resolution — the path addresses adventure sections only.
 *
 * <p>Event-only sections (luck tests, skill tests, combat) have no authored choices. After
 * the event fires and navigates elsewhere, the engine still renders system choices and calls
 * {@code readChoice}. In that case — when no {@code SectionTarget} choices are presented —
 * this input selects the "inventory" system choice as a safe no-op and does NOT advance the
 * path cursor. The next call (from the section the event navigated to) will consume the
 * next path entry normally.
 *
 * <p>{@link #readYesNo} always returns {@code false} (decline), which causes optional
 * interactions such as luck tests during combat to be silently opted out. This keeps
 * playback scenarios deterministic without requiring them to specify every sub-prompt.
 */
public class PathFollowingInput implements GameInput {

    private final List<Integer> path;
    private int cursor;

    public PathFollowingInput(List<Integer> path) {
        this.path = List.copyOf(path);
        this.cursor = 0;
    }

    @Override
    public int readChoice(List<Choice> choices) {
        // Event-only sections (combat, luck/skill tests) have no authored choices.
        // The event has already navigated elsewhere; we just need to pass through
        // without consuming a path entry.
        if (noSectionTargets(choices)) {
            return safePassThroughChoice(choices);
        }
        if (cursor >= path.size()) {
            throw new IllegalStateException(
                "PathFollowingInput exhausted after " + path.size() + " choices. " +
                "Presented choices: " + describeChoices(choices));
        }
        int target = path.get(cursor++);
        return resolveChoice(choices, target);
    }

    @Override
    public boolean readYesNo(String prompt) {
        return false; // decline all optional interactions
    }

    @Override
    public void waitForEnter() {
        // no-op in playback mode
    }

    // -----------------------------------------------------------------------
    // Package-private — independently testable
    // -----------------------------------------------------------------------

    /**
     * Returns the 1-based index of the choice whose target is a {@link SectionTarget}
     * matching {@code targetSection}.
     *
     * @throws IllegalStateException if no presented choice leads to {@code targetSection}
     */
    static int resolveChoice(List<Choice> choices, int targetSection) {
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).target() instanceof SectionTarget st
                    && st.sectionNumber() == targetSection) {
                return i + 1;
            }
        }
        throw new IllegalStateException(
            "No choice leads to section " + targetSection + ". " +
            "Available choices: " + describeChoices(choices));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static boolean noSectionTargets(List<Choice> choices) {
        return choices.stream().noneMatch(c -> c.target() instanceof SectionTarget);
    }

    /**
     * Picks the "inventory" system choice as a safe no-op pass-through.
     * Falls back to the first non-quit system choice if inventory is absent.
     */
    private static int safePassThroughChoice(List<Choice> choices) {
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).target() instanceof SystemChoiceTarget sc
                    && sc.action().equals("inventory")) {
                return i + 1;
            }
        }
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).target() instanceof SystemChoiceTarget sc
                    && !sc.action().equals("quit")) {
                return i + 1;
            }
        }
        throw new IllegalStateException(
            "No safe pass-through choice found: " + describeChoices(choices));
    }

    private static String describeChoices(List<Choice> choices) {
        return choices.stream()
            .map(c -> "\"" + c.text() + "\" → " + c.target())
            .collect(Collectors.joining(", ", "[", "]"));
    }
}
