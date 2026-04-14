package com.tas.neo.domain.log;

import java.util.List;

public record SessionLog<E>(
    String adventureId,
    List<NavigationEntry> path,
    List<E> events,
    List<GameError> errors,
    String result,
    int stepsCount
) {}
