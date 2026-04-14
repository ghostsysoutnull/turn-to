package com.tas.neo.domain.player;

public record Attribute(AttributeType type, int current, int max) {

    public Attribute modify(int delta) {
        int newCurrent = Math.max(0, Math.min(max, current + delta));
        return new Attribute(type, newCurrent, max);
    }
}
