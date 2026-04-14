package com.tas.neo.domain.adventure.event;

public record ItemEvent(String itemName, ItemAction action, int quantity) implements SectionEvent {}
