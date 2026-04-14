package com.tas.neo.domain.party;

public record PartyMemberStat(String name, int current, int max) {

    public PartyMemberStat modify(int delta) {
        int newCurrent = Math.max(0, Math.min(max, current + delta));
        return new PartyMemberStat(name, newCurrent, max);
    }

    public boolean isDepleted() {
        return current == 0;
    }

    public String display() {
        if (current == max) {
            return String.valueOf(current);
        }
        return current + "/" + max;
    }
}
