package com.tas.neo.domain.log;

import java.util.List;

public record SessionLog(
    String adventureId,
    List<NavigationEntry> path,
    List<Object> events,
    List<GameError> errors,
    String result,
    int stepsCount
) {}
