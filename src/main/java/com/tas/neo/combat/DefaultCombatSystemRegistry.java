package com.tas.neo.combat;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultCombatSystemRegistry implements CombatSystemRegistry {

    private final Map<String, CombatSystem> systems = new LinkedHashMap<>();

    public DefaultCombatSystemRegistry(CombatSystem... systems) {
        for (CombatSystem system : systems) {
            this.systems.put(system.id(), system);
        }
    }

    @Override
    public CombatSystem get(String id) {
        CombatSystem system = systems.get(id);
        if (system == null) {
            throw new IllegalArgumentException("Unknown combat system: " + id);
        }
        return system;
    }

    @Override
    public boolean has(String id) {
        return systems.containsKey(id);
    }
}
