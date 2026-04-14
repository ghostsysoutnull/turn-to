package com.tas.neo.domain.adventure.event;

public record GoldChangeEvent(int delta) implements SectionEvent {}
