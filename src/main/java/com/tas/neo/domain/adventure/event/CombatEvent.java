package com.tas.neo.domain.adventure.event;

import com.tas.neo.domain.adventure.ScriptBlock;
import com.tas.neo.domain.combat.Creature;
import java.util.List;
import java.util.Map;

public record CombatEvent(
    String system,
    List<String> participantIds,
    List<Creature> opponents,
    boolean simultaneous,
    Map<String, Object> params,
    ScriptBlock scripts,
    int successSection,
    int failureSection
) implements SectionEvent {}
