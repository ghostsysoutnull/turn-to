package com.tas.neo.analysis;

import java.util.List;

/**
 * An immutable description of a playback run: which adventure to load and
 * the ordered sequence of section numbers the player intends to navigate to.
 *
 * <p>Each element of {@code path} is the <em>target</em> of one player choice.
 * Sections reached automatically via events (NavigateEvent, LuckTestEvent, etc.)
 * do not appear in the path — only sections that require an explicit choice do.
 */
public record PlaybackScenario(String adventureId, List<Integer> path) {

    public PlaybackScenario {
        path = List.copyOf(path);
    }

    /**
     * Parses a comma-separated section list, e.g. {@code "2,4"}.
     */
    public static PlaybackScenario of(String adventureId, String commaSeparatedPath) {
        List<Integer> sections = java.util.Arrays.stream(commaSeparatedPath.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .toList();
        return new PlaybackScenario(adventureId, sections);
    }
}
