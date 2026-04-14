package com.tas.neo.combat;

public interface CombatSystemRegistry {
    CombatSystem get(String id);
    boolean has(String id);
}
