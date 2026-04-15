package com.tas.neo.analysis;

import com.tas.neo.domain.adventure.Adventure;
import com.tas.neo.domain.adventure.Section;
import com.tas.neo.domain.adventure.SectionTarget;
import com.tas.neo.domain.adventure.event.CombatEvent;
import com.tas.neo.domain.adventure.event.LuckTestEvent;
import com.tas.neo.domain.adventure.event.NavigateEvent;
import com.tas.neo.domain.adventure.event.SectionEvent;
import com.tas.neo.domain.adventure.event.SkillTestEvent;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Directed navigation graph built from an {@link Adventure}.
 *
 * <p>Nodes are section numbers. Edges represent all transitions a player can follow:
 * choices with section targets, navigation events (NAVIGATE, LUCK_TEST, SKILL_TEST,
 * COMBAT), and literal {@code navigateTo(N)} calls in onEnter scripts and CombatEvent
 * scripts.
 *
 * <p>This is the single source of truth for section reachability. Both
 * {@code AdventureValidationTest} and {@code ChapterValidationTest} delegate to this
 * class rather than maintaining their own BFS implementations.
 */
public class SectionGraph {

    private static final Pattern NAVIGATE_TO = Pattern.compile("navigateTo\\((\\d+)\\)");

    private final Adventure adventure;

    private SectionGraph(Adventure adventure) {
        this.adventure = adventure;
    }

    public static SectionGraph of(Adventure adventure) {
        return new SectionGraph(adventure);
    }

    /**
     * All section numbers directly reachable from {@code sectionNumber} in one step.
     * Includes: choice targets, event targets, and onEnter script navigateTo() calls.
     * Returns an empty set for unknown section numbers.
     */
    public Set<Integer> successors(int sectionNumber) {
        Section section;
        try {
            section = adventure.getSection(sectionNumber);
        } catch (IllegalArgumentException e) {
            return Set.of();
        }

        Set<Integer> result = new LinkedHashSet<>();

        for (var choice : section.choices()) {
            if (choice.target() instanceof SectionTarget st) {
                result.add(st.sectionNumber());
            }
        }

        for (SectionEvent event : section.events()) {
            result.addAll(eventSuccessors(event));
        }

        section.scripts().get("onEnter").ifPresent(script ->
            result.addAll(scriptSuccessors(script))
        );

        return Collections.unmodifiableSet(result);
    }

    /** BFS from {@code start} — unbounded, visits all reachable section numbers. */
    public Set<Integer> reachableFrom(int start) {
        return bfs(Set.of(start), -1, -1);
    }

    /**
     * BFS from {@code start} — stops traversing at sections outside
     * [{@code rangeFrom}, {@code rangeTo}]. Exit targets just beyond the range
     * boundary are recorded in the visited set but their successors are not followed.
     */
    public Set<Integer> reachableFrom(int start, int rangeFrom, int rangeTo) {
        return bfs(Set.of(start), rangeFrom, rangeTo);
    }

    /**
     * BFS seeded from all sections in {@code starts} simultaneously, bounded to
     * [{@code rangeFrom}, {@code rangeTo}]. Use this when a chapter has multiple
     * valid entry points (e.g. branching gate entries).
     */
    public Set<Integer> reachableFrom(Set<Integer> starts, int rangeFrom, int rangeTo) {
        return bfs(starts, rangeFrom, rangeTo);
    }

    /** All section numbers present in the adventure. */
    public Set<Integer> allSectionNumbers() {
        Set<Integer> all = new LinkedHashSet<>();
        for (Section s : adventure.sections()) all.add(s.number());
        return Collections.unmodifiableSet(all);
    }

    // -------------------------------------------------------------------------
    // Static helpers — public so callers outside the package can reuse them
    // without going through a full SectionGraph instance
    // -------------------------------------------------------------------------

    public static Set<Integer> eventSuccessors(SectionEvent event) {
        Set<Integer> targets = new LinkedHashSet<>();
        switch (event) {
            case NavigateEvent e -> targets.add(e.targetSection());
            case LuckTestEvent e -> { targets.add(e.successSection()); targets.add(e.failSection()); }
            case SkillTestEvent e -> { targets.add(e.successSection()); targets.add(e.failSection()); }
            case CombatEvent e -> {
                if (e.successSection() > 0) targets.add(e.successSection());
                if (e.failureSection() > 0) targets.add(e.failureSection());
                e.scripts().hooks().values().forEach(s -> targets.addAll(scriptSuccessors(s)));
            }
            default -> {}
        }
        return targets;
    }

    public static Set<Integer> scriptSuccessors(String script) {
        Set<Integer> targets = new LinkedHashSet<>();
        Matcher m = NAVIGATE_TO.matcher(script);
        while (m.find()) targets.add(Integer.parseInt(m.group(1)));
        return targets;
    }

    // -------------------------------------------------------------------------

    private Set<Integer> bfs(Collection<Integer> starts, int rangeFrom, int rangeTo) {
        boolean bounded = rangeFrom >= 0;
        Set<Integer> visited = new LinkedHashSet<>();
        Deque<Integer> queue = new ArrayDeque<>(starts);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            if (!visited.add(current)) continue;
            if (bounded && (current < rangeFrom || current > rangeTo)) continue;

            for (int next : successors(current)) {
                if (!visited.contains(next)) queue.add(next);
            }
        }

        return Collections.unmodifiableSet(visited);
    }
}
