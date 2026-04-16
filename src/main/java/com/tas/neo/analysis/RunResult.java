package com.tas.neo.analysis;

import java.util.List;

public record RunResult(
        RunOutcome outcome,
        int endingSection,
        List<Integer> sectionsVisited,
        List<ChapterSnapshot> chapterSnapshots,
        List<String> warnings
) {}
