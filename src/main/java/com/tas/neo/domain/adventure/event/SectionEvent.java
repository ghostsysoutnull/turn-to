package com.tas.neo.domain.adventure.event;

public sealed interface SectionEvent
    permits CombatEvent, StatChangeEvent, ItemEvent,
            LuckTestEvent, SkillTestEvent, NavigateEvent, GoldChangeEvent {}
