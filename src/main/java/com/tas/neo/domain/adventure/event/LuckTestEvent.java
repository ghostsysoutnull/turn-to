package com.tas.neo.domain.adventure.event;

public record LuckTestEvent(int successSection, int failSection) implements SectionEvent {}
