package com.tas.neo.analysis;

import java.util.Map;
import java.util.Set;

public record ChapterSnapshot(
        String chapterId,
        int atSection,
        Set<String> inventory,
        int gold,
        Map<String, Object> stateVariables
) {}
