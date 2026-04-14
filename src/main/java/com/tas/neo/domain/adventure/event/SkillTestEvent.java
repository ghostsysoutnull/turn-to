package com.tas.neo.domain.adventure.event;

public record SkillTestEvent(int successSection, int failSection) implements SectionEvent {}
