package com.tas.neo.domain.adventure.event;

public record NavigateEvent(int targetSection) implements SectionEvent {}
