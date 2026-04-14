package com.tas.neo.domain.combat;

public record Creature(String name, int skill, int stamina) {

    public Creature wound(int damage) {
        return new Creature(name, skill, Math.max(0, stamina - damage));
    }

    public boolean isAlive() {
        return stamina > 0;
    }
}
