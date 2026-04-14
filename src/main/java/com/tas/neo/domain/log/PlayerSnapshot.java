package com.tas.neo.domain.log;

import java.util.List;

public record PlayerSnapshot(
    String location,
    int stamina,
    int skill,
    int luck,
    int gold,
    List<String> inventory
) {}
