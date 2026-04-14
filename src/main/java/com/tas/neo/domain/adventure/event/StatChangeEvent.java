package com.tas.neo.domain.adventure.event;

import com.tas.neo.domain.player.AttributeType;

public record StatChangeEvent(AttributeType attribute, int delta) implements SectionEvent {}
