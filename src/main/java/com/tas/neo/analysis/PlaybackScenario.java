package com.tas.neo.analysis;

import java.util.List;
import java.util.OptionalLong;

/**
 * An immutable description of a playback run: which adventure to load,
 * the ordered sequence of section numbers the player intends to navigate to,
 * and an optional dice seed for deterministic outcomes.
 *
 * <p>Each element of {@code path} is the <em>target</em> of one player choice.
 * Sections reached automatically via events (NavigateEvent, LuckTestEvent, etc.)
 * do not appear in the path — only sections that require an explicit choice do.
 *
 * <p>When {@code seed} is present, a {@link com.tas.neo.mechanics.SeededDice} is used,
 * making luck tests, skill tests, and combat outcomes fully deterministic and
 * reproducible. Required whenever the path passes through a dice-dependent event.
 */
public record PlaybackScenario(String adventureId, List<Integer> path, OptionalLong seed) {

    public PlaybackScenario {
        path = List.copyOf(path);
    }

    /** Convenience constructor without a seed (random dice). */
    public PlaybackScenario(String adventureId, List<Integer> path) {
        this(adventureId, path, OptionalLong.empty());
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

    /**
     * Parses a comma-separated section list with a seed, e.g. {@code "2,4"}, seed {@code 42L}.
     */
    public static PlaybackScenario of(String adventureId, String commaSeparatedPath, long seed) {
        List<Integer> sections = java.util.Arrays.stream(commaSeparatedPath.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .toList();
        return new PlaybackScenario(adventureId, sections, OptionalLong.of(seed));
    }
}
