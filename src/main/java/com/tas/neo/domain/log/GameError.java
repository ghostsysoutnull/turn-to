package com.tas.neo.domain.log;

public record GameError(
    String source,
    String type,
    String message,
    PlayerSnapshot snapshot
) {}
