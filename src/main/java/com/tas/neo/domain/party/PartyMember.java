package com.tas.neo.domain.party;

import java.util.Map;

public class PartyMember {

    private final String id;
    private final String displayName;
    private final String lifeStat;
    private final Map<String, PartyMemberStat> stats;
    private final DefeatConsequence onDefeat;
    private MemberState state;

    public PartyMember(String id, String displayName, String lifeStat,
                       Map<String, PartyMemberStat> stats,
                       DefeatConsequence onDefeat, MemberState state) {
        this.id = id;
        this.displayName = displayName;
        this.lifeStat = lifeStat;
        this.stats = stats;
        this.onDefeat = onDefeat;
        this.state = state;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public MemberState state() {
        return state;
    }

    public boolean isActive() {
        return state == MemberState.ACTIVE;
    }

    public int getStat(String name) {
        return stats.get(name).current();
    }

    public int getMaxStat(String name) {
        return stats.get(name).max();
    }

    public void modifyStat(String name, int delta) {
        stats.put(name, stats.get(name).modify(delta));
    }

    public boolean hasStat(String name) {
        return stats.containsKey(name);
    }

    public boolean isDefeated() {
        return stats.get(lifeStat).isDepleted();
    }

    public void setState(MemberState state) {
        this.state = state;
    }

    public DefeatConsequence onDefeat() {
        return onDefeat;
    }
}
